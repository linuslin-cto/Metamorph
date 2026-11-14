package com.metamorph.core.library;

import com.metamorph.core.context.ContextBuilder;
import com.metamorph.core.tree.HarnessTree;
import com.metamorph.core.tree.SurfaceSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Per-domain experience library. Port of {@code upstream.core.library}.
 *
 * <pre>
 *   &lt;root&gt;/&lt;domain&gt;/INDEX.md
 *   &lt;root&gt;/&lt;domain&gt;/harnesses/&lt;task&gt;/harness/...
 *   &lt;root&gt;/&lt;domain&gt;/harnesses/&lt;task&gt;/EXPERIENCE.md
 * </pre>
 */
public final class Library {
  private final Path root;
  private final String domain;
  private final SurfaceSpec surface;

  public Library(Path root, String domain, SurfaceSpec surface) {
    this.root = root;
    this.domain = domain;
    this.surface = surface;
  }

  public static String slug(String taskId) {
    return taskId.replace("/", "__");
  }

  public static String domainDir(String domain) {
    String cleaned = domain == null ? "" : domain.replaceAll("\\s*[/\\\\]\\s*", "__").strip();
    return cleaned.isEmpty() ? "unknown" : cleaned;
  }

  public static boolean hasExperience(Object experience) {
    if (!(experience instanceof Map<?, ?> m)) {
      return false;
    }
    for (String key : List.of("cue", "lesson", "evidence")) {
      if (!hasField(m.get(key))) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasField(Object v) {
    if (v == null) {
      return false;
    }
    if (v instanceof String s) {
      return !s.isBlank();
    }
    // Non-string values are not a transferable library cue/lesson/evidence record.
    return false;
  }

  /** Parse cue/lesson/evidence/confidence lines from an {@code <experience>} body. */
  public static Map<String, String> parseFields(String body) {
    Map<String, String> out = new java.util.LinkedHashMap<>();
    if (body == null || body.isBlank()) {
      return out;
    }
    for (String field : List.of("cue", "lesson", "evidence", "confidence")) {
      var m =
          Pattern.compile("(?im)^\\s*" + Pattern.quote(field) + "\\s*:\\s*(\\S.*)$")
              .matcher(body);
      if (m.find()) {
        out.put(field, m.group(1).strip());
      }
    }
    return out;
  }

  public Path root() {
    return root;
  }

  public Path dir() {
    return root.resolve(domainDir(domain));
  }

  public Path index() {
    return dir().resolve("INDEX.md");
  }

  public Path entryDir(String taskId) {
    return dir().resolve("harnesses").resolve(slug(taskId));
  }

  public boolean exists() {
    return Files.isRegularFile(index());
  }

  /** Directory handed to an improver session, or null when this domain is empty. */
  public Path contextDir() {
    try {
      if (!exists()) {
        return null;
      }
      try (Stream<Path> walk = Files.walk(dir())) {
        boolean any = walk.anyMatch(p -> p.getFileName().toString().equals("EXPERIENCE.md"));
        return any ? dir() : null;
      }
    } catch (Exception e) {
      return null;
    }
  }

  public List<String> entries() {
    Path d = dir().resolve("harnesses");
    if (!Files.isDirectory(d)) {
      return List.of();
    }
    try (Stream<Path> list = Files.list(d)) {
      return list.filter(Files::isDirectory)
          .map(p -> p.getFileName().toString())
          .sorted()
          .toList();
    } catch (Exception e) {
      return List.of();
    }
  }

  public Map<String, String> treeOf(String taskId) {
    return HarnessTree.readTree(surface, entryDir(taskId).resolve("harness"));
  }

  public void forget(List<String> taskIds) throws Exception {
    boolean removed = false;
    for (String tid : taskIds) {
      Path d = entryDir(tid);
      if (Files.isDirectory(d)) {
        deleteRecursive(d);
        removed = true;
      }
    }
    if (removed) {
      rebuildIndex();
    }
  }

  /** Keep only the entries preceding the current task in this stream. */
  public void retain(List<String> taskIds) {
    try {
      var keep = taskIds.stream().map(Library::slug).collect(java.util.stream.Collectors.toSet());
      for (String entry : entries()) {
        if (!keep.contains(entry)) {
          Path d = dir().resolve("harnesses").resolve(entry);
          if (Files.isDirectory(d)) {
            deleteRecursive(d);
          }
        }
      }
      rebuildIndex();
    } catch (Exception ignored) {
    }
  }

  public Path add(
      String taskId,
      Map<String, String> harness,
      String why,
      String label,
      String runId,
      Map<String, Object> experience)
      throws Exception {
    if (!hasExperience(experience)) {
      return null;
    }
    ContextBuilder.assertTreeHasNoGraderVerdicts(harness);
    ContextBuilder.assertTreeHasNoGraderVerdicts(
        Map.of("EXPERIENCE.md", String.valueOf(experience) + (why == null ? "" : why)));
    Path d = entryDir(taskId);
    Path harnessDir = d.resolve("harness");
    if (Files.exists(harnessDir)) {
      deleteRecursive(harnessDir);
    }
    Files.createDirectories(harnessDir);
    HarnessTree.writeTree(surface, harness, harnessDir, false);

    Map<String, Object> exp = experience == null ? Map.of() : experience;
    List<String> lines = new ArrayList<>();
    lines.add("# " + taskId);
    lines.add("");
    lines.add(
        "- adopted: `"
            + label
            + "`"
            + (runId == null || runId.isBlank() ? "" : "  (run `" + runId + "`)"));
    lines.add("- components: " + inventory(surface, harness));
    lines.add("");
    if (why != null && !why.isBlank()) {
      lines.add("## Why this was chosen");
      lines.add(why.strip());
      lines.add("");
    }
    if (exp.get("edits") != null) {
      lines.add("## What each edit did");
      lines.add(String.valueOf(exp.get("edits")).strip());
      lines.add("");
    }
    if (exp.get("lesson") != null) {
      lines.add("## Transferable lesson");
      for (String f : List.of("cue", "lesson", "evidence", "confidence")) {
        if (exp.get(f) != null && !String.valueOf(exp.get(f)).isBlank()) {
          lines.add("- **" + f + "**: " + String.valueOf(exp.get(f)).strip());
        }
      }
      lines.add("");
    }
    Files.writeString(d.resolve("EXPERIENCE.md"), String.join("\n", lines));
    rebuildIndex();
    return d;
  }

  public Path rebuildIndex() throws Exception {
    List<String> rows = new ArrayList<>();
    rows.add("# Library — " + domain);
    rows.add("");
    rows.add(
        "Each entry is an earlier task in THIS domain whose harness was chosen, stored with its"
            + " FULL component tree and why it was picked.");
    rows.add("");
    List<String> names = entries();
    if (names.isEmpty()) {
      rows.add("_(empty — this is the first task in this domain)_");
    }
    for (String name : names) {
      Path ed = dir().resolve("harnesses").resolve(name);
      String cue = field(ed.resolve("EXPERIENCE.md"), "cue");
      Map<String, String> tree = HarnessTree.readTree(surface, ed.resolve("harness"));
      rows.add("## `harnesses/" + name + "/`");
      rows.add(
          "- harness: `harnesses/" + name + "/harness/` — " + inventory(surface, tree));
      rows.add("- why:     `harnesses/" + name + "/EXPERIENCE.md`");
      if (cue != null && !cue.isBlank()) {
        rows.add("- cue:     " + cue);
      }
      rows.add("");
    }
    Files.createDirectories(dir());
    Files.writeString(index(), String.join("\n", rows));
    return index();
  }

  private static String inventory(SurfaceSpec surface, Map<String, String> tree) {
    return tree.size() + " files on " + surface.name();
  }

  private static String field(Path experienceMd, String name) {
    try {
      if (!Files.isRegularFile(experienceMd)) {
        return "";
      }
      Pattern p = Pattern.compile("(?m)^- \\*\\*" + Pattern.quote(name) + "\\*\\*:\\s*(.+)$");
      var m = p.matcher(Files.readString(experienceMd));
      return m.find() ? m.group(1).strip() : "";
    } catch (Exception e) {
      return "";
    }
  }

  private static void deleteRecursive(Path root) throws Exception {
    if (!Files.exists(root)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(root)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
              });
    }
  }
}
