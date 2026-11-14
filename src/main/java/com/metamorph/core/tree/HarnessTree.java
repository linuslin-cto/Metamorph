package com.metamorph.core.tree;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The c1..c8 component tree: read, validate, write, diff — parametrised by {@link SurfaceSpec}.
 *
 * <p>Port of {@code upstream.core.tree}.
 */
public final class HarnessTree {
  public static final Set<String> RESERVED_SCRIPTS =
      Set.of("read_doc.py", "pack.py", "unpack.py");

  private static final String NAME = "[a-z0-9][a-z0-9_\\-]*";
  private static final ObjectMapper JSON = new ObjectMapper();

  private HarnessTree() {}

  public static String normalizeRel(String rel) {
    String r = rel.strip().replace('\\', '/');
    if (r.startsWith("/") || List.of(r.split("/")).contains("..")) {
      throw new IllegalArgumentException("path escapes the harness: '" + rel + "'");
    }
    while (r.startsWith("./")) {
      r = r.substring(2);
    }
    return r.replaceAll("^/+|/+$", "");
  }

  private static Patterns patterns(SurfaceSpec surface) {
    String sd = Pattern.quote(surface.skillDir());
    Pattern skill = Pattern.compile(sd + "/(" + NAME + ")/SKILL\\.md");
    Pattern skillScript = Pattern.compile(sd + "/" + NAME + "/scripts/[A-Za-z0-9_.\\-]+");
    Pattern subagent = Pattern.compile("sub_agents/(" + NAME + ")/AGENT\\.md");
    Pattern script = Pattern.compile("scripts/[a-z0-9_][a-z0-9_]*\\.py");
    Pattern scriptRef =
        Pattern.compile("(?:python3?\\s+)((?:" + sd + "/[\\w\\-]+/)?scripts/[\\w.\\-]+\\.py)");
    Pattern plugin =
        surface.pluginDir() == null
            ? null
            : Pattern.compile(Pattern.quote(surface.pluginDir()) + "/(" + NAME + ")\\.ts");
    return new Patterns(skill, skillScript, subagent, script, scriptRef, plugin);
  }

  public static boolean isAllowed(SurfaceSpec surface, String rel) {
    try {
      rel = normalizeRel(rel);
    } catch (IllegalArgumentException e) {
      return false;
    }
    if (surface.alwaysOn().contains(rel)) {
      return true;
    }
    if (surface.hooksFile() != null && surface.hooksFile().equals(rel)) {
      return true;
    }
    Patterns r = patterns(surface);
    if (r.skill.matcher(rel).matches()
        || r.skillScript.matcher(rel).matches()
        || r.subagent.matcher(rel).matches()
        || r.script.matcher(rel).matches()) {
      return true;
    }
    return r.plugin != null && r.plugin.matcher(rel).matches();
  }

  public static List<String> skillNames(SurfaceSpec surface, Map<String, String> tree) {
    Pattern rx = patterns(surface).skill;
    return tree.keySet().stream()
        .map(rx::matcher)
        .filter(Matcher::matches)
        .map(m -> m.group(1))
        .distinct()
        .sorted()
        .toList();
  }

  public static List<String> subagentNames(SurfaceSpec surface, Map<String, String> tree) {
    Pattern rx = patterns(surface).subagent;
    return tree.keySet().stream()
        .map(rx::matcher)
        .filter(Matcher::matches)
        .map(m -> m.group(1))
        .distinct()
        .sorted()
        .toList();
  }

  public static Map<String, String> readTree(
      SurfaceSpec surface, Path dir, List<String> rejected) {
    Map<String, String> out = new LinkedHashMap<>();
    if (!Files.isDirectory(dir)) {
      return out;
    }
    Set<String> stubs =
        surface.stubs().stream().map(SurfaceSpec.Stub::rel).collect(Collectors.toSet());
    try (Stream<Path> walk = Files.walk(dir)) {
      walk.filter(Files::isRegularFile)
          .filter(p -> !p.toString().contains("__pycache__"))
          .sorted()
          .forEach(
              p -> {
                String rel = dir.relativize(p).toString().replace('\\', '/');
                if (stubs.contains(rel)) {
                  return;
                }
                if (!isAllowed(surface, rel)) {
                  if (rejected != null) {
                    rejected.add(rel);
                  }
                  return;
                }
                try {
                  out.put(rel, Files.readString(p, StandardCharsets.UTF_8));
                } catch (IOException e) {
                  out.put(rel, "");
                }
              });
    } catch (IOException e) {
      return out;
    }
    return out;
  }

  public static Map<String, String> readTree(SurfaceSpec surface, Path dir) {
    return readTree(surface, dir, null);
  }

  public static boolean isTree(SurfaceSpec surface, Path dir) {
    if (!Files.isDirectory(dir)) {
      return false;
    }
    return surface.alwaysOn().stream().anyMatch(a -> Files.isRegularFile(dir.resolve(a)));
  }

  public static Path writeTree(
      SurfaceSpec surface, Map<String, String> tree, Path dest, boolean stubs) throws IOException {
    Files.createDirectories(dest);
    Set<String> stubSet =
        surface.stubs().stream().map(SurfaceSpec.Stub::rel).collect(Collectors.toSet());
    try (Stream<Path> walk = Files.walk(dest)) {
      List<Path> files =
          walk.filter(Files::isRegularFile)
              .sorted(Comparator.reverseOrder())
              .collect(Collectors.toList());
      for (Path p : files) {
        String rel = dest.relativize(p).toString().replace('\\', '/');
        if (isAllowed(surface, rel) || stubSet.contains(rel)) {
          Files.deleteIfExists(p);
        }
      }
    }
    for (String a : surface.alwaysOn()) {
      Path p = dest.resolve(a);
      Files.createDirectories(p.getParent());
      if (!tree.containsKey(a) && !Files.exists(p)) {
        Files.writeString(p, "", StandardCharsets.UTF_8);
      }
    }
    for (Map.Entry<String, String> e : tree.entrySet()) {
      Path p = dest.resolve(normalizeRel(e.getKey()));
      Files.createDirectories(p.getParent() == null ? dest : p.getParent());
      Files.writeString(p, e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8);
    }
    if (stubs) {
      for (SurfaceSpec.Stub stub : surface.stubs()) {
        Path p = dest.resolve(stub.rel());
        Files.createDirectories(p.getParent() == null ? dest : p.getParent());
        if (!Files.exists(p)) {
          Files.writeString(p, stub.body(), StandardCharsets.UTF_8);
        }
      }
    }
    return dest;
  }

  public static String alwaysOnText(SurfaceSpec surface, Map<String, String> tree) {
    return surface.alwaysOn().stream()
        .map(a -> tree.getOrDefault(a, ""))
        .collect(Collectors.joining("\n\n"));
  }

  public static List<String> autotrigger(SurfaceSpec surface, Map<String, String> tree) {
    return autotrigger(surface, tree, surface.alwaysOnMax());
  }

  public static List<String> autotrigger(
      SurfaceSpec surface, Map<String, String> tree, int alwaysOnMax) {
    String body = alwaysOnText(surface, tree);
    List<String> fixed = new ArrayList<>();
    for (String name : skillNames(surface, tree)) {
      if (body.contains(name)) {
        continue;
      }
      String fm =
          tree.getOrDefault(surface.skillDir() + "/" + name + "/SKILL.md", "");
      if (fm.length() > 600) {
        fm = fm.substring(0, 600);
      }
      Matcher m = Pattern.compile("(?m)^description:\\s*(.+)$").matcher(fm);
      String desc =
          m.find()
              ? m.group(1).strip().replaceAll("\\.$", "")
              : "the " + name + " workflow applies";
      String line =
          surface
              .triggerLine()
              .replace("{name}", name)
              .replace("{desc}", desc);
      String cur = tree.getOrDefault(surface.autotriggerFile(), "");
      if (alwaysOnText(surface, tree).length() + line.length() + 2 > alwaysOnMax) {
        continue;
      }
      tree.put(
          surface.autotriggerFile(),
          cur.strip().isEmpty() ? line : cur.stripTrailing() + "\n" + line);
      body = alwaysOnText(surface, tree);
      fixed.add(name);
    }
    return fixed;
  }

  public static Map<String, Object> validate(
      SurfaceSpec surface,
      Map<String, String> tree,
      boolean allowSubagents,
      Integer alwaysOnMax,
      Set<String> reservedScripts) {
    List<String> errs = new ArrayList<>();
    Patterns r = patterns(surface);
    int cap = alwaysOnMax == null ? surface.alwaysOnMax() : alwaysOnMax;
    String body = alwaysOnText(surface, tree);

    if (body.length() > cap) {
      errs.add(
          "always-on content is "
              + body.length()
              + " chars, over the "
              + cap
              + " cap; "
              + surface.alwaysOnCapReason());
    }
    if (surface.rejectEmptyAlwaysOn() && body.isBlank()) {
      errs.add(
          surface.alwaysOn().get(0)
              + " is empty — it is the only always-on file on this framework, so an empty one is a"
              + " harness that does not exist");
    }

    List<String> skills = checkSkills(surface, tree, body, errs);
    List<String> subs = checkSubagents(surface, tree, allowSubagents, errs);

    for (Map.Entry<String, String> e : tree.entrySet()) {
      String rel = e.getKey();
      if (r.script.matcher(rel).matches()
          || (surface.compileSkillScripts()
              && r.skillScript.matcher(rel).matches()
              && rel.endsWith(".py"))) {
        if (e.getValue() == null) {
          errs.add(rel + " does not compile: null source");
        } else {
          pyCompiles(rel, e.getValue(), errs);
        }
      }
    }

    if (surface.hooksFile() != null && tree.containsKey(surface.hooksFile())) {
      checkHooks(surface, tree.get(surface.hooksFile()), errs);
    }

    checkScriptRefs(surface, tree, r, reservedScripts, errs);

    if (!errs.isEmpty()) {
      throw new IllegalArgumentException(String.join(" | ", errs));
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("always_on_chars", body.length());
    out.put("n_files", tree.size());
    out.put("skills", skills.size());
    out.put("sub_agents", subs.size());
    out.put(
        "scripts",
        tree.keySet().stream().filter(rel -> r.script.matcher(rel).matches()).count());
    if (surface.pluginDir() != null) {
      out.put(
          "plugins",
          tree.keySet().stream()
              .filter(rel -> r.plugin != null && r.plugin.matcher(rel).matches())
              .count());
    }
    if (surface.hooksFile() != null) {
      out.put("hooks", tree.containsKey(surface.hooksFile()) ? 1 : 0);
    }
    return out;
  }

  public static Map<String, Object> validate(SurfaceSpec surface, Map<String, String> tree) {
    return validate(surface, tree, true, null, RESERVED_SCRIPTS);
  }

  public static Map<String, Object> diffSummary(
      SurfaceSpec surface, Map<String, String> before, Map<String, String> after) {
    Set<String> b = before.keySet();
    Set<String> a = after.keySet();
    List<String> added = a.stream().filter(k -> !b.contains(k)).sorted().toList();
    List<String> removed = b.stream().filter(k -> !a.contains(k)).sorted().toList();
    List<String> modified =
        b.stream()
            .filter(a::contains)
            .filter(k -> !ObjectsEquals(before.get(k), after.get(k)))
            .sorted()
            .toList();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("added", added);
    out.put("removed", removed);
    out.put("modified", modified);
    out.put(
        "always_on_char_delta",
        alwaysOnText(surface, after).length() - alwaysOnText(surface, before).length());
    return out;
  }

  public static Map<String, Object> diversity(Map<String, Map<String, String>> trees) {
    List<String> names = trees.keySet().stream().sorted().toList();
    if (names.size() < 2) {
      return Map.of("n", names.size(), "n_distinct", names.size(), "mean_line_jaccard", 1.0);
    }
    Map<String, Set<String>> sets = new HashMap<>();
    for (String n : names) {
      Set<String> lines = new HashSet<>();
      for (Map.Entry<String, String> e : trees.get(n).entrySet()) {
        for (String ln : e.getValue().split("\\R", -1)) {
          if (!ln.isBlank()) {
            lines.add(e.getKey() + ":" + ln);
          }
        }
      }
      sets.put(n, lines);
    }
    List<Double> sims = new ArrayList<>();
    for (int i = 0; i < names.size(); i++) {
      for (int j = i + 1; j < names.size(); j++) {
        Set<String> u = new HashSet<>(sets.get(names.get(i)));
        u.addAll(sets.get(names.get(j)));
        Set<String> inter = new HashSet<>(sets.get(names.get(i)));
        inter.retainAll(sets.get(names.get(j)));
        sims.add(u.isEmpty() ? 1.0 : (double) inter.size() / u.size());
      }
    }
    long distinct =
        trees.values().stream()
            .map(
                t ->
                    t.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining("\n")))
            .distinct()
            .count();
    double mean = sims.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("n", names.size());
    out.put("n_distinct", distinct);
    out.put("mean_line_jaccard", Math.round(mean * 10000.0) / 10000.0);
    return out;
  }

  private static List<String> checkSkills(
      SurfaceSpec surface, Map<String, String> tree, String body, List<String> errs) {
    List<String> skills = skillNames(surface, tree);
    for (String name : skills) {
      String path = surface.skillDir() + "/" + name + "/SKILL.md";
      String fm = tree.getOrDefault(path, "");
      if (fm.length() > 600) {
        fm = fm.substring(0, 600);
      }
      if (!Pattern.compile("(?m)^description:\\s*\\S").matcher(fm).find()) {
        errs.add(
            path
                + " needs a `description:` in its frontmatter, or the skill is dropped SILENTLY at"
                + " load");
      }
      Matcher mName = Pattern.compile("(?m)^name:\\s*(\\S+)\\s*$").matcher(fm);
      if ("must_match".equals(surface.skillNameRule())) {
        if (!(mName.find() && name.equals(mName.group(1)))) {
          errs.add(
              path
                  + " needs frontmatter `name: "
                  + name
                  + "` matching its directory (a mismatch is dropped SILENTLY at load)");
        }
      } else if (mName.find() && !name.equals(mName.group(1))) {
        errs.add(
            path
                + " frontmatter `name: "
                + mName.group(1)
                + "` OVERRIDES the directory name at load, so every trigger line naming `"
                + name
                + "` stops matching; drop the `name:` line or make it `name: "
                + name
                + "`");
      }
      if (!body.contains(name)) {
        errs.add(
            "skill '"
                + name
                + "' is never named in the always-on text, so the agent will not know to invoke it"
                + " — an untriggered skill is dead weight. "
                + surface.triggerHint().replace("{name}", name));
      }
    }
    return skills;
  }

  private static List<String> checkSubagents(
      SurfaceSpec surface, Map<String, String> tree, boolean allowSubagents, List<String> errs) {
    List<String> subs = subagentNames(surface, tree);
    if (!subs.isEmpty() && !allowSubagents) {
      errs.add("sub_agents are not available on this deployment: " + subs);
    }
    for (String name : subs) {
      String path = "sub_agents/" + name + "/AGENT.md";
      String fm = tree.getOrDefault(path, "");
      if (fm.length() > 600) {
        fm = fm.substring(0, 600);
      }
      if (surface.subagentRequiresMode()
          && !Pattern.compile("(?m)^mode:\\s*subagent\\s*$").matcher(fm).find()) {
        errs.add(path + " needs frontmatter `mode: subagent`");
      }
      if (!Pattern.compile("(?m)^description:\\s*\\S").matcher(fm).find()) {
        errs.add(path + " needs a `description:`");
      }
    }
    return subs;
  }

  private static void checkScriptRefs(
      SurfaceSpec surface,
      Map<String, String> tree,
      Patterns r,
      Set<String> reservedScripts,
      List<String> errs) {
    Set<String> have =
        tree.keySet().stream()
            .filter(rel -> r.script.matcher(rel).matches() || r.skillScript.matcher(rel).matches())
            .collect(Collectors.toSet());
    for (Map.Entry<String, String> e : tree.entrySet()) {
      String rel = e.getKey();
      if (!(r.skill.matcher(rel).matches() || surface.alwaysOn().contains(rel))) {
        continue;
      }
      Matcher m = r.scriptRef.matcher(e.getValue());
      while (m.find()) {
        String ref = m.group(1);
        String base = ref.contains("/") ? ref.substring(ref.lastIndexOf('/') + 1) : ref;
        if (reservedScripts.contains(base)) {
          continue;
        }
        if (!have.contains(ref)) {
          errs.add(rel + " invokes `" + ref + "` but that file is not in the harness");
        }
      }
    }
  }

  private static void checkHooks(SurfaceSpec surface, String src, List<String> errs) {
    String hf = surface.hooksFile();
    try {
      JsonNode doc = JSON.readTree(src);
      JsonNode hooks = doc.get("hooks");
      if (hooks == null || !hooks.isObject() || hooks.isEmpty()) {
        errs.add(hf + " needs a top-level {\"hooks\": {\"<Event>\": [...]}} object");
        return;
      }
      hooks
          .fields()
          .forEachRemaining(
              entry -> {
                String event = entry.getKey();
                if (!surface.hookEvents().contains(event)) {
                  errs.add(
                      hf
                          + ": unknown event '"
                          + event
                          + "'; the framework dispatches "
                          + surface.hookEvents().stream().sorted().collect(Collectors.joining(", "))
                          + " (PascalCase)");
                  return;
                }
                JsonNode entries = entry.getValue();
                if (!entries.isArray()) {
                  errs.add(hf + ": " + event + " must map to a LIST of matcher entries");
                  return;
                }
                for (int i = 0; i < entries.size(); i++) {
                  JsonNode item = entries.get(i);
                  if (!item.isObject() || !item.path("hooks").isArray()) {
                    errs.add(hf + ": " + event + "[" + i + "] needs a `hooks` list");
                    continue;
                  }
                  for (JsonNode h : item.get("hooks")) {
                    if (!h.isObject() || !"command".equals(h.path("type").asText())) {
                      errs.add(
                          hf
                              + ": "
                              + event
                              + "["
                              + i
                              + "] hook type must be \"command\" — async/prompt/agent hooks are"
                              + " skipped as 'not supported yet'");
                    } else if (h.path("command").asText("").isBlank()) {
                      errs.add(hf + ": " + event + "[" + i + "] has an empty `command`");
                    }
                  }
                }
              });
    } catch (Exception exc) {
      errs.add(hf + " is not valid JSON: " + exc.getMessage());
    }
  }

  private static void pyCompiles(String rel, String src, List<String> errs) {
    try {
      Path tmp = Files.createTempFile("metamorph-", ".py");
      try {
        Files.writeString(tmp, src, StandardCharsets.UTF_8);
        ProcessBuilder pb =
            new ProcessBuilder(
                pythonExecutable(),
                "-c",
                "import pathlib,sys; compile(pathlib.Path(sys.argv[1]).read_text(encoding='utf-8'),"
                    + " sys.argv[1], 'exec')",
                tmp.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean ok = p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
        if (!ok) {
          String detail = out.strip();
          if (detail.length() > 160) {
            detail = detail.substring(0, 160);
          }
          errs.add(rel + " does not compile: " + (detail.isBlank() ? "SyntaxError" : detail));
        }
      } finally {
        Files.deleteIfExists(tmp);
      }
    } catch (Exception e) {
      errs.add(rel + " does not compile: " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private static String pythonExecutable() {
    String fromEnv = System.getenv("PYTHON");
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv;
    }
    String prop = System.getProperty("PYTHON");
    if (prop != null && !prop.isBlank()) {
      return prop;
    }
    return "python";
  }

  private static boolean ObjectsEquals(String a, String b) {
    return java.util.Objects.equals(a, b);
  }

  private record Patterns(
      Pattern skill,
      Pattern skillScript,
      Pattern subagent,
      Pattern script,
      Pattern scriptRef,
      Pattern plugin) {}
}
