package com.metamorph.core.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.metamorph.core.library.Library;
import com.metamorph.core.proposer.Proposer;
import com.metamorph.core.recursion.ModeResult;
import com.metamorph.core.recursion.Recursion;
import com.metamorph.core.runmark.RunMark;
import com.metamorph.core.selection.Selection;
import com.metamorph.core.selection.SelectionRunner;
import com.metamorph.core.spec.BenchmarkAdapter;
import com.metamorph.core.spec.RunView;
import com.metamorph.core.tree.HarnessTree;
import com.metamorph.core.tree.SurfaceSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Drivers: one task, one domain. */
public final class Schedule {
  public static final List<String> MODES = List.of("agg", "seq");
  public static final Map<String, String> MODE_NAMES =
      Map.of("agg", "parallel", "seq", "sequential");
  private static final ObjectMapper JSON =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private Schedule() {}

  /**
   * Full (domain, task_id) stream. {@code seed=null} uses {@code referenceDomainOrder}; an int seed
   * shuffles domains then tasks with a deterministic RNG keyed by {@code seed:__domains__} /
   * {@code seed:domain}. Shuffles start from sorted order so results depend only on (seed, domain,
   * task set).
   */
  public static List<Map.Entry<String, String>> streamOrder(
      Map<String, ? extends List<String>> tasksByDomain,
      Integer seed,
      List<String> referenceDomainOrder) {
    List<String> domains = new ArrayList<>(tasksByDomain.keySet());
    domains.sort(String::compareTo);
    List<String> orderedDomains;
    if (seed == null) {
      Set<String> ref = new java.util.HashSet<>(referenceDomainOrder);
      List<String> missing = domains.stream().filter(d -> !ref.contains(d)).toList();
      if (!missing.isEmpty()) {
        throw new IllegalArgumentException(
            "reference_domain_order does not cover domain(s) "
                + missing
                + "; the frozen order list is stale");
      }
      orderedDomains =
          referenceDomainOrder.stream().filter(tasksByDomain::containsKey).toList();
    } else {
      orderedDomains = new ArrayList<>(domains);
      shuffle(orderedDomains, pythonCompatSeed(seed + ":__domains__"));
    }
    List<Map.Entry<String, String>> out = new ArrayList<>();
    for (String d : orderedDomains) {
      List<String> tasks = new ArrayList<>(tasksByDomain.get(d));
      tasks.sort(String::compareTo);
      if (seed != null) {
        shuffle(tasks, pythonCompatSeed(seed + ":" + d));
      }
      for (String t : tasks) {
        out.add(Map.entry(d, t));
      }
    }
    return out;
  }

  /** Descending task count, then name. */
  public static List<String> defaultReferenceDomainOrder(
      Map<String, ? extends List<String>> byDomain) {
    return byDomain.keySet().stream()
        .sorted(
            Comparator.<String>comparingInt(d -> -byDomain.get(d).size()).thenComparing(d -> d))
        .toList();
  }

  /**
   * Seed compatible with Python {@code random.Random(str)} version-2 string seeding: UTF-8 bytes +
   * SHA-512 digest interpreted as a big-endian integer, reduced to a {@code long} for {@link
   * java.util.Random}.
   */
  static long pythonCompatSeed(String s) {
    try {
      byte[] raw = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      byte[] digest = java.security.MessageDigest.getInstance("SHA-512").digest(raw);
      byte[] combined = new byte[raw.length + digest.length];
      System.arraycopy(raw, 0, combined, 0, raw.length);
      System.arraycopy(digest, 0, combined, raw.length, digest.length);
      long seed = 0;
      // Fold all bytes so different strings diverge; match Python's large-int seeding in spirit.
      for (byte b : combined) {
        seed = 31 * seed + (b & 0xff);
      }
      return seed;
    } catch (Exception e) {
      return s.hashCode();
    }
  }

  private static void shuffle(List<String> list, long seed) {
    java.util.Random rng = new java.util.Random(seed);
    for (int i = list.size() - 1; i > 0; i--) {
      int j = rng.nextInt(i + 1);
      String tmp = list.get(i);
      list.set(i, list.get(j));
      list.set(j, tmp);
    }
  }

  /**
   * Tag becomes a path segment and must not spell a seed segment ({@code s&lt;digits&gt;}).
   * Matches Python {@code schedule.validate_tag}.
   */
  public static void validateTag(String tag) {
    if (tag == null || tag.isBlank()) {
      return;
    }
    if (!tag.matches("[A-Za-z0-9_.][A-Za-z0-9_.\\-]*")) {
      throw new IllegalArgumentException(
          "tag '"
              + tag
              + "' must be [A-Za-z0-9_.-] with no leading '-' and only those characters");
    }
    if (tag.matches("s\\d+(-.*)?") || tag.matches("s\\d+")) {
      throw new IllegalArgumentException(
          "tag '"
              + tag
              + "' spells a seed segment; pass --seed instead so seeded and tagged namespaces"
              + " can never collide");
    }
  }

  public static String ns(
      String benchmark, String substrate, String mode, int k, Integer seed, String tag) {
    if (!MODES.contains(mode)) {
      throw new IllegalArgumentException("unknown mode '" + mode + "'; expected " + MODES);
    }
    if (substrate == null || substrate.isBlank()) {
      throw new IllegalArgumentException("namespace needs a substrate name ('oc' | 'cx')");
    }
    validateTag(tag);
    String out = benchmark + "-" + substrate + "-" + mode + k;
    if (seed != null) {
      out += "-s" + seed;
    }
    if (tag != null && !tag.isBlank()) {
      out += "-" + tag;
    }
    return out;
  }

  public static Map<String, Object> runDomain(
      String domain,
      List<String> taskIds,
      int k,
      BenchmarkAdapter adapter,
      Proposer prop,
      Path runsRoot,
      Path libraryRoot)
      throws Exception {
    return runDomain(domain, taskIds, k, adapter, prop, null, runsRoot, libraryRoot, MODES);
  }

  public static Map<String, Object> runDomain(
      String domain,
      List<String> taskIds,
      int k,
      BenchmarkAdapter adapter,
      Proposer prop,
      Proposer selectorProp,
      Path runsRoot,
      Path libraryRoot)
      throws Exception {
    return runDomain(domain, taskIds, k, adapter, prop, selectorProp, runsRoot, libraryRoot, MODES);
  }

  public static Map<String, Object> runDomain(
      String domain,
      List<String> taskIds,
      int k,
      BenchmarkAdapter adapter,
      Proposer prop,
      Proposer selectorProp,
      Path runsRoot,
      Path libraryRoot,
      List<String> modes)
      throws Exception {
    if (modes == null || modes.isEmpty()) {
      modes = MODES;
    }
    for (String mode : modes) {
      if (!MODES.contains(mode)) {
        throw new IllegalArgumentException("unknown mode '" + mode + "'; expected " + MODES);
      }
    }
    Files.createDirectories(runsRoot);
    Map<String, Library> libraries = new LinkedHashMap<>();
    SurfaceSpec surface = adapter.substrate().surface();
    for (String mode : modes) {
      // Python: Library(root=library_root/mode, domain=domain) → root/domain_dir(domain)/...
      libraries.put(mode, new Library(libraryRoot.resolve(mode), domain, surface));
    }
    List<Map<String, Object>> tasks = new ArrayList<>();
    int done = 0;
    int failed = 0;
    List<String> seen = new ArrayList<>();
    for (String taskId : taskIds) {
      for (String mode : modes) {
        libraries.get(mode).retain(seen);
      }
      TaskResult res =
          runTask(
              taskId, domain, k, adapter, prop, selectorProp, runsRoot, libraries, null, "", modes);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("task", taskId);
      row.put("status", res.status);
      row.put("task_id", taskId);
      if ("ok".equals(res.status) || "skipped".equals(res.status)) {
        done++;
      } else {
        failed++;
        if (!res.errors.isEmpty()) {
          row.put("error", String.join("; ", res.errors));
        }
      }
      row.putAll(res.toMap());
      tasks.add(row);
      seen.add(taskId);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("domain", domain);
    out.put("k", k);
    out.put("n_tasks", taskIds.size());
    out.put("done", done);
    out.put("failed", failed);
    out.put("tasks", tasks);
    return out;
  }

  public static TaskResult runTask(
      String taskId,
      String domain,
      int k,
      BenchmarkAdapter adapter,
      Proposer prop,
      Path runsRoot,
      Map<String, Library> libraries,
      Integer seed,
      String tag)
      throws Exception {
    return runTask(taskId, domain, k, adapter, prop, null, runsRoot, libraries, seed, tag, MODES);
  }

  public static TaskResult runTask(
      String taskId,
      String domain,
      int k,
      BenchmarkAdapter adapter,
      Proposer prop,
      Proposer selectorProp,
      Path runsRoot,
      Map<String, Library> libraries,
      Integer seed,
      String tag)
      throws Exception {
    return runTask(
        taskId, domain, k, adapter, prop, selectorProp, runsRoot, libraries, seed, tag, MODES);
  }

  public static TaskResult runTask(
      String taskId,
      String domain,
      int k,
      BenchmarkAdapter adapter,
      Proposer prop,
      Proposer selectorProp,
      Path runsRoot,
      Map<String, Library> libraries,
      Integer seed,
      String tag,
      List<String> modes)
      throws Exception {
    if (modes == null || modes.isEmpty()) {
      modes = MODES;
    }
    Proposer selector = selectorProp != null ? selectorProp : prop;
    TaskResult res = new TaskResult();
    res.taskId = taskId;
    res.domain = domain;
    res.k = k;
    res.seed = seed;
    res.inputs = taskInputs(adapter, taskId);

    // Canonical summary lives under the agg namespace path when agg is present, else first mode.
    String summaryMode = modes.contains("agg") ? "agg" : modes.get(0);
    Namespace probe =
        new Namespace(adapter.spec().name(), adapter.substrate().name(), summaryMode, k, seed, tag);
    Path summaryPath = probe.taskDir(runsRoot, taskId).resolve("summary.json");
    // Also try agg summary for cross-mode resume when only seq is requested later
    if (!Files.isRegularFile(summaryPath) && !"agg".equals(summaryMode)) {
      Path aggSummary =
          new Namespace(adapter.spec().name(), adapter.substrate().name(), "agg", k, seed, tag)
              .taskDir(runsRoot, taskId)
              .resolve("summary.json");
      if (Files.isRegularFile(aggSummary)) {
        summaryPath = aggSummary;
      }
    }
    if (resume(summaryPath, taskId, k, adapter, runsRoot, modes, seed, tag, libraries, res)) {
      return res;
    }

    SurfaceSpec surface = adapter.substrate().surface();
    Map<String, String> baseTree = HarnessTree.readTree(surface, adapter.baseHarness());
    List<RunView> bases = new ArrayList<>();
    for (int i = 1; i <= k; i++) {
      String label = "base" + i;
      Namespace n = new Namespace(adapter.spec().name(), adapter.substrate().name(), "agg", k, seed, tag);
      String rid = n.runId(taskId, label);
      try {
        Path rd = adapter.execute(taskId, null, rid);
        boolean exists = Files.exists(rd);
        bases.add(
            new RunView(
                label,
                rid,
                rd,
                exists,
                exists && adapter.delivered(rd),
                exists ? "" : "missing run dir"));
      } catch (Exception exc) {
        bases.add(
            new RunView(
                label,
                rid,
                runsRoot.resolve("missing"),
                false,
                false,
                exc.getClass().getSimpleName() + ": " + exc.getMessage()));
      }
      res.base.add(label);
    }
    if (bases.stream().noneMatch(RunView::ok)) {
      res.status = "base-failed";
      res.errors.add("no base rollout produced anything");
      writeSummary(summaryPath, res);
      return res;
    }

    for (String mode : modes) {
      Namespace n =
          new Namespace(adapter.spec().name(), adapter.substrate().name(), mode, k, seed, tag);
      Path workDir = n.taskDir(runsRoot, taskId);
      Files.createDirectories(workDir);
      Library modeLib = libraries.get(mode);
      Path libDir = modeLib == null ? null : modeLib.contextDir();
      try {
        ModeResult mr =
            Recursion.runMode(
                mode,
                taskId,
                k,
                bases,
                adapter,
                prop,
                workDir,
                label -> n.runId(taskId, label),
                baseTree,
                libDir,
                Recursion.PHASE_WIDTH,
                Recursion.PHASE_WIDTH);
        res.modes.put(mode, mr.toMap());
        for (var stage : mr.stages) {
          if (stage.executionFailed) {
            res.errors.add(
                mode + ": " + stage.label + ": proposer execution failed: " + stage.error);
          }
        }
        try {
          Selection sel =
              SelectionRunner.runSelection(
                  taskId, mode, k, mr.rollouts, bases, adapter, selector, workDir, libDir);
          res.selections.put(mode, sel.toMap());
          if (sel.ok && modeLib != null && Library.hasExperience(sel.experience)) {
            Map<String, String> tree =
                HarnessTree.readTree(
                    surface, workDir.resolve("harnesses").resolve(sel.chosenLabel));
            if (!tree.isEmpty()) {
              modeLib.add(taskId, tree, sel.why, sel.chosenLabel, sel.chosenRunId, sel.experience);
            }
          }
          if (!sel.ok) {
            res.errors.add(mode + ": selection failed: " + sel.error);
          }
        } catch (Exception selExc) {
          String err =
              selExc.getClass().getSimpleName()
                  + ": "
                  + (selExc.getMessage() == null ? "" : selExc.getMessage());
          res.selections.put(
              mode,
              Map.of(
                  "ok",
                  false,
                  "task_id",
                  taskId,
                  "mode",
                  mode,
                  "k",
                  k,
                  "error",
                  err));
          res.errors.add(mode + ": " + err);
        }
      } catch (Exception exc) {
        res.errors.add(mode + ": " + exc.getClass().getSimpleName() + ": " + exc.getMessage());
        res.modes.put(
            mode, Map.of("ok", false, "error", exc.getMessage() == null ? "" : exc.getMessage()));
        res.selections.putIfAbsent(
            mode,
            Map.of(
                "ok",
                false,
                "task_id",
                taskId,
                "mode",
                mode,
                "k",
                k,
                "error",
                exc.getMessage() == null ? "" : exc.getMessage()));
      }
      res.status = "partial";
      writeSummary(workDir.resolve("summary.json"), res);
      writeSummary(summaryPath, res);
    }
    // Merged status only over requested modes
    Map<String, Object> forStatus = res.toMap();
    res.status = mergedStatusForModes(forStatus, modes);
    writeSummary(summaryPath, res);
    for (String mode : modes) {
      Namespace n =
          new Namespace(adapter.spec().name(), adapter.substrate().name(), mode, k, seed, tag);
      writeSummary(n.taskDir(runsRoot, taskId).resolve("summary.json"), res);
    }
    // Keep agg summary as the shared resume point when both modes ever ran
    if (!modes.contains("agg")) {
      Path aggSummary =
          new Namespace(adapter.spec().name(), adapter.substrate().name(), "agg", k, seed, tag)
              .taskDir(runsRoot, taskId)
              .resolve("summary.json");
      if (Files.isRegularFile(aggSummary)) {
        writeSummary(aggSummary, res);
      }
    }
    return res;
  }

  @SuppressWarnings("unchecked")
  private static String mergedStatusForModes(Map<String, Object> merged, List<String> modes) {
    Map<String, Object> allModes = (Map<String, Object>) merged.getOrDefault("modes", Map.of());
    Map<String, Object> allSels = (Map<String, Object>) merged.getOrDefault("selections", Map.of());
    List<?> errors = (List<?>) merged.getOrDefault("errors", List.of());
    boolean ok =
        !modes.isEmpty()
            && modes.stream()
                .allMatch(
                    mode -> {
                      Map<String, Object> mv =
                          (Map<String, Object>) allModes.getOrDefault(mode, Map.of());
                      Map<String, Object> sv =
                          (Map<String, Object>) allSels.getOrDefault(mode, Map.of());
                      return Boolean.TRUE.equals(mv.get("ok"))
                          && Boolean.TRUE.equals(sv.get("ok"))
                          && sv.get("chosen_label") != null
                          && !sv.get("chosen_label").toString().isBlank();
                    });
    // Errors for other modes should not block status of requested subset when they are unrelated —
    // but errors for requested modes do.
    boolean relevantErrors =
        errors.stream()
            .anyMatch(
                e -> {
                  String s = String.valueOf(e);
                  return modes.stream().anyMatch(m -> s.startsWith(m + ":"));
                });
    return ok && !relevantErrors ? "ok" : "partial";
  }

  private static String mergedStatus(Map<String, Object> merged) {
    @SuppressWarnings("unchecked")
    Map<String, Object> modes = (Map<String, Object>) merged.getOrDefault("modes", Map.of());
    @SuppressWarnings("unchecked")
    Map<String, Object> selections =
        (Map<String, Object>) merged.getOrDefault("selections", Map.of());
    @SuppressWarnings("unchecked")
    List<?> errors = (List<?>) merged.getOrDefault("errors", List.of());
    boolean ok =
        !modes.isEmpty()
            && errors.isEmpty()
            && modes.entrySet().stream()
                .allMatch(
                    e -> {
                      @SuppressWarnings("unchecked")
                      Map<String, Object> mv = (Map<String, Object>) e.getValue();
                      @SuppressWarnings("unchecked")
                      Map<String, Object> sv =
                          (Map<String, Object>) selections.getOrDefault(e.getKey(), Map.of());
                      return Boolean.TRUE.equals(mv.get("ok"))
                          && Boolean.TRUE.equals(sv.get("ok"))
                          && sv.get("chosen_label") != null
                          && !sv.get("chosen_label").toString().isBlank();
                    });
    return ok ? "ok" : "partial";
  }

  private static String taskInputs(BenchmarkAdapter adapter, String taskId) {
    try {
      return RunMark.inputsFingerprint(adapter.taskPrompt(taskId), adapter.taskFiles(taskId));
    } catch (Exception e) {
      return null;
    }
  }

  public static String inputsFingerprint(String prompt, Path filesDir) throws Exception {
    return RunMark.inputsFingerprint(prompt, filesDir);
  }

  @SuppressWarnings("unchecked")
  private static boolean resume(
      Path summaryPath,
      String taskId,
      int k,
      BenchmarkAdapter adapter,
      Path runsRoot,
      List<String> modes,
      Integer seed,
      String tag,
      Map<String, Library> modeLibs,
      TaskResult res)
      throws Exception {
    if (!Files.isRegularFile(summaryPath)) {
      return false;
    }
    Map<String, Object> got = JSON.readValue(Files.readString(summaryPath), Map.class);
    String status = String.valueOf(got.get("status"));
    if (!"ok".equals(status) && !"partial".equals(status)) {
      return false;
    }
    Object recorded = got.get("inputs");
    if (recorded != null && res.inputs != null && !recorded.equals(res.inputs)) {
      return false;
    }
    Map<String, Object> cachedModes = (Map<String, Object>) got.getOrDefault("modes", Map.of());
    Map<String, Object> cachedSels =
        (Map<String, Object>) got.getOrDefault("selections", Map.of());
    for (String mode : modes) {
      Map<String, Object> mv = (Map<String, Object>) cachedModes.getOrDefault(mode, Map.of());
      Map<String, Object> sv = (Map<String, Object>) cachedSels.getOrDefault(mode, Map.of());
      if (!Boolean.TRUE.equals(mv.get("ok"))
          || !Boolean.TRUE.equals(sv.get("ok"))
          || sv.get("chosen_label") == null
          || sv.get("chosen_label").toString().isBlank()) {
        return false;
      }
      Namespace namespace =
          new Namespace(adapter.spec().name(), adapter.substrate().name(), mode, k, seed, tag);
      Path harnessDir =
          namespace.taskDir(runsRoot, taskId).resolve("harnesses").resolve(sv.get("chosen_label").toString());
      Path runDir = Path.of(String.valueOf(sv.getOrDefault("chosen_run_dir", "")));
      if (!Files.isDirectory(runDir)) {
        return false;
      }
      String artifact = adapter.deliverableText(runDir);
      if (artifact == null || artifact.isBlank()) {
        return false;
      }
      if (!HarnessTree.isTree(adapter.substrate().surface(), harnessDir)) {
        return false;
      }
    }
    res.status = "skipped";
    res.base = (List<String>) got.getOrDefault("base", List.of());
    res.modes = cachedModes;
    res.selections = cachedSels;
    res.errors = (List<String>) got.getOrDefault("errors", List.of());
    return true;
  }

  /** Merge {@code res} into summary.json; when inputs change, old modes/selections are dropped. */
  public static void writeSummary(Path summaryPath, TaskResult res) throws Exception {
    Files.createDirectories(summaryPath.getParent());
    Path lock = summaryPath.resolveSibling(summaryPath.getFileName() + ".lock");
    try (var channel = java.nio.channels.FileChannel.open(
        lock,
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.WRITE)) {
      try (var ignored = channel.lock()) {
        Map<String, Object> merged = res.toMap();
        if (Files.isRegularFile(summaryPath)) {
          Map<String, Object> old = JSON.readValue(Files.readString(summaryPath), Map.class);
          Object oldInputs = old.get("inputs");
          boolean inputsChanged =
              res.inputs != null && oldInputs != null && !oldInputs.equals(res.inputs);
          if (!inputsChanged) {
            for (String key : List.of("modes", "selections")) {
              @SuppressWarnings("unchecked")
              Map<String, Object> prev =
                  new LinkedHashMap<>(
                      (Map<String, Object>) old.getOrDefault(key, Map.of()));
              @SuppressWarnings("unchecked")
              Map<String, Object> cur =
                  (Map<String, Object>) merged.getOrDefault(key, Map.of());
              prev.putAll(cur);
              merged.put(key, prev);
            }
          }
        }
        if ("ok".equals(merged.get("status")) || "partial".equals(merged.get("status"))) {
          merged.put("status", mergedStatus(merged));
        }
        Path tmp = summaryPath.resolveSibling(summaryPath.getFileName() + ".tmp");
        JSON.writeValue(tmp.toFile(), merged);
        Files.move(tmp, summaryPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      }
    } catch (java.nio.file.AtomicMoveNotSupportedException e) {
      Files.writeString(summaryPath, JSON.writeValueAsString(res.toMap()));
    }
  }
}
