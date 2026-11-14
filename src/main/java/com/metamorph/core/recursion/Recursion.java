package com.metamorph.core.recursion;

import com.metamorph.config.Config;
import com.metamorph.core.context.ContextBuilder;
import com.metamorph.core.context.ContextTree;
import com.metamorph.core.ledger.Ledger;
import com.metamorph.core.prompts.Prompts;
import com.metamorph.core.proposer.Proposer;
import com.metamorph.core.proposer.ProposerError;
import com.metamorph.core.proposer.Reply;
import com.metamorph.core.spec.BenchmarkAdapter;
import com.metamorph.core.spec.BenchPrompts;
import com.metamorph.core.spec.RunView;
import com.metamorph.core.spec.Specs;
import com.metamorph.core.tree.HarnessTree;
import com.metamorph.core.tree.SurfaceSpec;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parallel and sequential task-level recursion. */
public final class Recursion {
  public static final int STAGE_ATTEMPTS = 2;
  public static final int TRANSPORT_ATTEMPTS = Config.TRANSPORT_ATTEMPTS;
  public static final int PHASE_WIDTH = Config.PROPOSER_CONCURRENCY;

  private static final Pattern NOTES = Pattern.compile("(?s)<notes>(.*?)</notes>");

  private Recursion() {}

  public static ModeResult runMode(
      String mode,
      String taskId,
      int k,
      List<RunView> base,
      BenchmarkAdapter adapter,
      Proposer prop,
      Path workDir,
      Function<String, String> runIdOf,
      Map<String, String> baseTree,
      Path libraryDir,
      int proposeWorkers,
      int rolloutWorkers)
      throws Exception {
    if ("agg".equals(mode)) {
      return runParallel(
          taskId, k, base, adapter, prop, workDir, runIdOf, baseTree, libraryDir, proposeWorkers,
          rolloutWorkers);
    }
    if ("seq".equals(mode)) {
      return runSequential(
          taskId, k, base, adapter, prop, workDir, runIdOf, baseTree, libraryDir, proposeWorkers,
          rolloutWorkers);
    }
    throw new IllegalArgumentException("unknown mode '" + mode + "'; expected agg|seq");
  }

  public static ModeResult runParallel(
      String taskId,
      int k,
      List<RunView> base,
      BenchmarkAdapter adapter,
      Proposer prop,
      Path workDir,
      Function<String, String> runIdOf,
      Map<String, String> baseTree,
      Path libraryDir,
      int proposeWorkers,
      int rolloutWorkers)
      throws Exception {
    SurfaceSpec surface = adapter.substrate().surface();
    Ledger ledger = new Ledger("agg", taskId, k);
    for (RunView r : base) {
      ledger.rollout(r.label());
    }
    ModeResult result = new ModeResult("agg", taskId, k);
    result.ledger = ledger;
    for (RunView r : base) {
      result.rollouts.put(r.label(), r);
    }
    Path ctxDir = workDir.resolve("context");
    BenchPrompts benchmarkPrompts = Specs.resolvePrompts(adapter, taskId);
    Path baseDir = HarnessTree.writeTree(surface, baseTree, workDir.resolve("harnesses").resolve("base"), true);

    List<String[]> specs = new ArrayList<>();
    for (int i = 1; i <= k; i++) {
      for (String d : List.of("INV", "DOM")) {
        specs.add(new String[] {d.toLowerCase() + i, d, String.valueOf(i)});
      }
    }
    List<Stage> stages =
        mapParallel(
            specs,
            Math.min(specs.size(), Math.max(1, proposeWorkers)),
            s ->
                runStage(
                    s[0],
                    s[1],
                    Prompts.propose(benchmarkPrompts, s[1], surface, k, Integer.parseInt(s[2])),
                    baseTree,
                    adapter,
                    prop,
                    ctxDir,
                    taskId,
                    base,
                    baseDir,
                    libraryDir,
                    List.of(),
                    null));
    recordSessions(ledger, stages);
    for (Stage stage : stages) {
      result.stages.add(stage);
      result.trees.put(stage.label, stage.tree);
    }
    List<Map.Entry<String, Map<String, String>>> items =
        stages.stream().map(st -> Map.entry(st.label, st.tree)).toList();
    result.rollouts.putAll(
        rollMany(adapter, ledger, taskId, items, runIdOf, workDir, rolloutWorkers));

    List<ContextBuilder.PoolItem> poolEv = new ArrayList<>();
    for (Stage stage : stages) {
      poolEv.add(
          new ContextBuilder.PoolItem(
              stage.label,
              workDir.resolve("harnesses").resolve(stage.label),
              result.rollouts.get(stage.label)));
    }
    Map<String, String> decisions = new LinkedHashMap<>();
    for (Stage stage : stages) {
      decisions.put(stage.label, stage.notes.isBlank() ? stage.error : stage.notes);
    }
    List<String> aggSpecs = new ArrayList<>();
    for (int i = 1; i <= k; i++) {
      aggSpecs.add("agg" + i);
    }
    List<Stage> aggs =
        mapParallel(
            aggSpecs,
            Math.min(aggSpecs.size(), Math.max(1, proposeWorkers)),
            lbl ->
                runStage(
                    lbl,
                    "AGG",
                    Prompts.aggregate(
                        benchmarkPrompts, surface, k, Integer.parseInt(lbl.substring(3))),
                    baseTree,
                    adapter,
                    prop,
                    ctxDir,
                    taskId,
                    base,
                    baseDir,
                    libraryDir,
                    poolEv,
                    decisions));
    recordSessions(ledger, aggs);
    for (Stage stage : aggs) {
      result.stages.add(stage);
      result.trees.put(stage.label, stage.tree);
      result.finals.add(stage.label);
    }
    List<Map.Entry<String, Map<String, String>>> aggItems =
        aggs.stream().map(st -> Map.entry(st.label, st.tree)).toList();
    result.rollouts.putAll(
        rollMany(adapter, ledger, taskId, aggItems, runIdOf, workDir, rolloutWorkers));
    Map<String, Map<String, String>> aggTrees = new LinkedHashMap<>();
    for (Stage s : aggs) {
      aggTrees.put(s.label, s.tree);
    }
    result.similarity = HarnessTree.diversity(aggTrees);
    ledger.assertIdentity();
    return result;
  }

  public static ModeResult runSequential(
      String taskId,
      int k,
      List<RunView> base,
      BenchmarkAdapter adapter,
      Proposer prop,
      Path workDir,
      Function<String, String> runIdOf,
      Map<String, String> baseTree,
      Path libraryDir,
      int proposeWorkers,
      int rolloutWorkers)
      throws Exception {
    SurfaceSpec surface = adapter.substrate().surface();
    Ledger ledger = new Ledger("seq", taskId, k);
    for (RunView r : base) {
      ledger.rollout(r.label());
    }
    ModeResult result = new ModeResult("seq", taskId, k);
    result.ledger = ledger;
    for (RunView r : base) {
      result.rollouts.put(r.label(), r);
    }
    Path ctxDir = workDir.resolve("context");
    BenchPrompts benchmarkPrompts = Specs.resolvePrompts(adapter, taskId);

    Map<String, String> currentHarness = new LinkedHashMap<>(baseTree);
    List<ContextBuilder.PoolItem> history = new ArrayList<>();
    for (int t = 1; t <= k; t++) {
      String currentLabel = t > 1 ? "c" + (t - 1) : "base";
      Path currentDir =
          HarnessTree.writeTree(
              surface, currentHarness, workDir.resolve("harnesses").resolve(currentLabel), true);
      List<String[]> specs =
          List.of(new String[] {"inv" + t, "INV"}, new String[] {"dom" + t, "DOM"});
      final int round = t;
      Map<String, String> fallback = new LinkedHashMap<>(currentHarness);
      final List<ContextBuilder.PoolItem> historySnapshot = List.copyOf(history);
      List<Stage> stages =
          mapParallel(
              specs,
              Math.min(2, Math.max(1, proposeWorkers)),
              s ->
                  runStage(
                      s[0],
                      s[1],
                      Prompts.propose(
                          benchmarkPrompts,
                          s[1],
                          surface,
                          k,
                          round,
                          k > 1 ? round : null),
                      fallback,
                      adapter,
                      prop,
                      ctxDir,
                      taskId,
                      base,
                      currentDir,
                      libraryDir,
                      historySnapshot,
                      decisionsOf(result.stages)));
      recordSessions(ledger, stages);
      for (Stage stage : stages) {
        result.stages.add(stage);
        result.trees.put(stage.label, stage.tree);
      }
      List<Map.Entry<String, Map<String, String>>> items =
          stages.stream().map(st -> Map.entry(st.label, st.tree)).toList();
      result.rollouts.putAll(
          rollMany(adapter, ledger, taskId, items, runIdOf, workDir, rolloutWorkers));

      List<ContextBuilder.PoolItem> roundPool = new ArrayList<>(history);
      for (Stage stage : stages) {
        roundPool.add(
            new ContextBuilder.PoolItem(
                stage.label,
                workDir.resolve("harnesses").resolve(stage.label),
                result.rollouts.get(stage.label)));
      }
      String ck = "c" + t;
      String compJob =
          k == 1
              ? Prompts.aggregate(benchmarkPrompts, surface, 1, 1)
              : Prompts.composeRound(benchmarkPrompts, surface, t, k);
      Stage comp =
          runStage(
              ck,
              "COMPOSE",
              compJob,
              currentHarness,
              adapter,
              prop,
              ctxDir,
              taskId,
              base,
              currentDir,
              libraryDir,
              roundPool,
              decisionsOf(result.stages));
      recordSessions(ledger, List.of(comp));
      result.stages.add(comp);
      result.trees.put(ck, comp.tree);
      result.rollouts.put(
          ck, roll(adapter, ledger, taskId, ck, comp.tree, runIdOf, workDir, true));
      history = new ArrayList<>(roundPool);
      history.add(
          new ContextBuilder.PoolItem(
              ck, workDir.resolve("harnesses").resolve(ck), result.rollouts.get(ck)));
      currentHarness = comp.tree;
    }
    for (int i = 1; i <= k; i++) {
      result.finals.add("c" + i);
    }
    ledger.assertIdentity();
    return result;
  }

  /** Package API used by offline stage-retry tests. */
  public static Stage runStage(
      String label,
      String dim,
      String job,
      Map<String, String> fallback,
      BenchmarkAdapter adapter,
      Proposer prop,
      Path ctxDir,
      String taskId,
      List<RunView> rollouts,
      Path harnessDir,
      Path libraryDir,
      List<ContextBuilder.PoolItem> pool,
      Map<String, String> decisions) {
    SurfaceSpec surface = adapter.substrate().surface();
    ContextTree tree;
    try {
      tree =
          ContextBuilder.build(
              ctxDir.resolve(label),
              adapter,
              taskId,
              rollouts,
              harnessDir,
              libraryDir,
              pool,
              decisions);
      ContextBuilder.mission(tree, job);
    } catch (Exception exc) {
      Stage s = new Stage(label, dim, fallback);
      s.fellBack = true;
      s.error =
          ("could not build evidence: " + exc.getClass().getSimpleName() + ": " + exc.getMessage());
      if (s.error.length() > 400) {
        s.error = s.error.substring(0, 400);
      }
      s.attempts = 0;
      return s;
    }

    String err = "";
    double spentUsd = 0;
    int spentCalls = 0;
    boolean allCached = true;
    boolean executionFailed = false;
    for (int attempt = 1; attempt <= STAGE_ATTEMPTS; attempt++) {
      try {
        String mission =
            ContextBuilder.mission(tree, job)
                + (err.isBlank()
                    ? ""
                    : "\n\n# YOUR PREVIOUS ATTEMPT WAS REJECTED\n\n"
                        + err
                        + "\n\nFix it in `context/harness/` and reply again.");
        Reply reply =
            prop.call(mission, tree.root(), label + ".a" + attempt, "context/harness", TRANSPORT_ATTEMPTS);
        spentUsd += reply.costUsd();
        spentCalls += reply.cached() ? 0 : Math.max(1, reply.attempts());
        allCached = allCached && reply.cached();
        if (reply.workspace() == null) {
          err =
              "the harness directory did not come back from the session; edit the files in"
                  + " `context/harness/` in place";
          continue;
        }
        List<String> rejected = new ArrayList<>();
        Map<String, String> neu = HarnessTree.readTree(surface, reply.workspace(), rejected);
        if (neu.isEmpty()) {
          err = "`context/harness/` came back empty -- edit the files, do not delete them all";
          continue;
        }
        if (!rejected.isEmpty()) {
          err =
              "these files are off the editable surface and were not applied: "
                  + String.join(", ", rejected.stream().sorted().limit(6).toList());
          try {
            HarnessTree.writeTree(surface, neu, tree.root().resolve("harness"), true);
          } catch (Exception ignored) {
          }
          continue;
        }
        Accept accept = acceptTree(surface, adapter, neu, fallback, label);
        if (!accept.error.isBlank()) {
          err = accept.error;
          try {
            HarnessTree.writeTree(surface, neu, tree.root().resolve("harness"), true);
          } catch (Exception ignored) {
          }
          continue;
        }
        Stage out = new Stage(label, dim, neu);
        out.keptBase = accept.keptBase;
        out.usd = spentUsd;
        out.attempts = spentCalls;
        out.cached = allCached;
        Matcher notes = NOTES.matcher(reply.text());
        out.notes = notes.find() ? notes.group(1).strip() : reply.text().strip();
        return out;
      } catch (ProposerError exc) {
        err = exc.getMessage();
        executionFailed = true;
        spentCalls += exc.attempts() > 0 ? exc.attempts() : TRANSPORT_ATTEMPTS;
        spentUsd += exc.costUsd();
        allCached = false;
        break;
      }
    }
    Stage fb = new Stage(label, dim, fallback);
    fb.fellBack = true;
    fb.error = err == null ? "" : err.substring(0, Math.min(400, err.length()));
    fb.usd = spentUsd;
    fb.attempts = spentCalls;
    fb.cached = allCached;
    fb.executionFailed = executionFailed;
    return fb;
  }

  /** Visible for offline parity tests of validation / kept-base detection. */
  record Accept(boolean keptBase, String error) {}

  static Accept acceptTree(
      SurfaceSpec surface,
      BenchmarkAdapter adapter,
      Map<String, String> neu,
      Map<String, String> fallback,
      String label) {
    List<String> triggered = HarnessTree.autotrigger(surface, neu);
    if (!triggered.isEmpty()) {
      System.out.println("  [" + label + "] auto-triggered skill(s): " + String.join(", ", triggered));
    }
    Map<String, Object> changed = HarnessTree.diffSummary(surface, fallback, neu);
    @SuppressWarnings("unchecked")
    boolean kept =
        ((List<?>) changed.get("added")).isEmpty()
            && ((List<?>) changed.get("removed")).isEmpty()
            && ((List<?>) changed.get("modified")).isEmpty();
    try {
      Set<String> reserved = new HashSet<>(HarnessTree.RESERVED_SCRIPTS);
      reserved.addAll(adapter.spec().reservedScripts());
      HarnessTree.validate(
          surface, neu, adapter.spec().allowSubagents(), null, reserved);
      adapter.validateTree(neu);
      ContextBuilder.assertTreeHasNoGraderVerdicts(neu);
    } catch (IllegalArgumentException exc) {
      return new Accept(kept, exc.getMessage());
    }
    return new Accept(kept, "");
  }

  private static void recordSessions(Ledger ledger, List<Stage> stages) {
    for (Stage stage : stages) {
      ledger.session(stage.label, stage.usd, stage.cached, Math.max(1, stage.attempts));
    }
  }

  private static RunView roll(
      BenchmarkAdapter adapter,
      Ledger ledger,
      String taskId,
      String label,
      Map<String, String> tree,
      Function<String, String> runIdOf,
      Path workDir,
      boolean record)
      throws Exception {
    Path hdir = null;
    if (tree != null) {
      hdir =
          HarnessTree.writeTree(
              adapter.substrate().surface(), tree, workDir.resolve("harnesses").resolve(label), true);
    }
    String runId = runIdOf.apply(label);
    RunView rv;
    try {
      Path rd = adapter.execute(taskId, hdir, runId);
      boolean exists = rd != null && java.nio.file.Files.exists(rd);
      rv =
          new RunView(
              label,
              runId,
              rd,
              exists,
              exists && adapter.delivered(rd),
              exists ? "" : "adapter returned a missing run directory");
    } catch (Exception exc) {
      rv =
          new RunView(
              label,
              runId,
              workDir.resolve("missing"),
              false,
              false,
              exc.getClass().getSimpleName() + ": " + exc.getMessage());
    }
    if (record) {
      ledger.rollout(label);
    }
    return rv;
  }

  private static Map<String, RunView> rollMany(
      BenchmarkAdapter adapter,
      Ledger ledger,
      String taskId,
      List<Map.Entry<String, Map<String, String>>> items,
      Function<String, String> runIdOf,
      Path workDir,
      int width)
      throws Exception {
    Map<String, RunView> out = new LinkedHashMap<>();
    if (items.isEmpty()) {
      return out;
    }
    int n = Math.max(1, Math.min(items.size(), width));
    if (n == 1) {
      for (var it : items) {
        out.put(it.getKey(), roll(adapter, ledger, taskId, it.getKey(), it.getValue(), runIdOf, workDir, true));
      }
      return out;
    }
    List<RunView> got =
        mapParallel(
            items,
            n,
            it -> {
              try {
                return roll(adapter, ledger, taskId, it.getKey(), it.getValue(), runIdOf, workDir, false);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
    for (int i = 0; i < items.size(); i++) {
      ledger.rollout(items.get(i).getKey());
      out.put(items.get(i).getKey(), got.get(i));
    }
    return out;
  }

  private static Map<String, String> decisionsOf(List<Stage> stages) {
    Map<String, String> d = new LinkedHashMap<>();
    for (Stage stage : stages) {
      d.put(stage.label, stage.notes.isBlank() ? stage.error : stage.notes);
    }
    return d;
  }

  private static <T, R> List<R> mapParallel(List<T> items, int workers, Function<T, R> fn)
      throws Exception {
    if (items.isEmpty()) {
      return List.of();
    }
    if (workers <= 1 || items.size() == 1) {
      List<R> out = new ArrayList<>();
      for (T it : items) {
        out.add(fn.apply(it));
      }
      return out;
    }
    ExecutorService pool = Executors.newFixedThreadPool(workers);
    try {
      List<Future<R>> futures = new ArrayList<>();
      for (T it : items) {
        futures.add(pool.submit(() -> fn.apply(it)));
      }
      List<R> out = new ArrayList<>();
      for (Future<R> f : futures) {
        out.add(f.get());
      }
      return out;
    } finally {
      pool.shutdownNow();
    }
  }
}
