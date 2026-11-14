package com.metamorph.core.context;

import com.metamorph.core.spec.BenchmarkAdapter;
import com.metamorph.core.spec.RunView;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the evidence tree an agentic proposer reads. */
public final class ContextBuilder {
  private ContextBuilder() {}

  public record PoolItem(String label, Path harnessDir, RunView rollout) {}

  public static ContextTree build(
      Path dest,
      BenchmarkAdapter adapter,
      String taskId,
      List<RunView> rollouts,
      Path harnessDir,
      Path libraryDir,
      List<PoolItem> pool,
      Map<String, String> decisions)
      throws IOException {
    if (Files.exists(dest)) {
      deleteRecursive(dest);
    }
    Files.createDirectories(dest);
    int trunc = adapter.spec().obsTrunc();

    Files.writeString(dest.resolve("task.md"), adapter.taskPrompt(taskId));
    int sourceFiles = copySource(adapter, taskId, dest);
    if (decisions != null) {
      for (var e : decisions.entrySet()) {
        Path path = dest.resolve("decisions").resolve(e.getKey() + ".md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, e.getValue() == null || e.getValue().isBlank()
            ? "No decision notes returned."
            : e.getValue());
      }
    }

    List<String> gotR = new ArrayList<>();
    List<String> gotD = new ArrayList<>();
    List<String> failedR = new ArrayList<>();
    boolean namesOnly = "names".equals(adapter.spec().proposerDeliverables());
    if (rollouts != null && !rollouts.isEmpty()) {
      Files.createDirectories(dest.resolve("rollouts"));
      Files.createDirectories(dest.resolve("deliverables"));
      for (RunView r : rollouts) {
        String failure = writeRollout(dest, r, adapter, trunc, namesOnly);
        if (failure != null) {
          failedR.add(failure);
        } else {
          gotR.add(r.label());
        }
        gotD.add(r.label());
      }
    }

    int nH = 0;
    if (harnessDir != null && Files.isDirectory(harnessDir)) {
      assertNoLeakPaths(harnessDir, adapter.spec().leakGlobs());
      nH = copyTree(harnessDir, dest.resolve("harness"));
    }

    List<String> gotP = writePool(dest, pool, adapter, trunc, namesOnly);

    boolean lib = false;
    if (libraryDir != null
        && Files.isDirectory(libraryDir)
        && Files.list(libraryDir).findAny().isPresent()) {
      assertNoLeakPaths(libraryDir, adapter.spec().leakGlobs());
      copyTree(libraryDir, dest.resolve("library"));
      lib = true;
    }

    assertNoLeakPaths(dest, adapter.spec().leakGlobs());
    assertNoGraderVerdicts(dest);

    if (rollouts != null && !rollouts.isEmpty() && gotR.isEmpty()) {
      throw new RuntimeException(
          "no trajectory could be rendered for ANY rollout: " + String.join("; ", failedR));
    }
    return new ContextTree(
        dest,
        taskId,
        gotR,
        gotD,
        nH,
        lib,
        gotP,
        failedR,
        namesOnly ? "names" : "contents",
        sourceFiles,
        decisions == null ? 0 : decisions.size());
  }

  public static String mission(ContextTree tree, String job) {
    return "# MISSION\n\n"
        + job
        + "\n\n# EVIDENCE LAYOUT\n\n"
        + tree.layout()
        + "\n\nEdit files under `context/harness/` in place. Wrap decision notes in <notes>..</notes>.";
  }

  public static void assertTreeHasNoGraderVerdicts(Map<String, String> tree) {
    for (var e : tree.entrySet()) {
      String low = e.getValue() == null ? "" : e.getValue().toLowerCase(Locale.ROOT);
      for (String bad : List.of("grader", "rubric", "benchmark score", "evaluation score")) {
        if (low.contains(bad)) {
          throw new IllegalArgumentException(
              e.getKey() + " must not contain grader/rubric language (found '" + bad + "')");
        }
      }
    }
  }

  public static void assertNoLeakPaths(Path root, List<String> globs) throws IOException {
    if (globs == null || globs.isEmpty() || !Files.exists(root)) {
      return;
    }
    java.util.List<String> hits = new java.util.ArrayList<>();
    try (var walk = Files.walk(root)) {
      for (Path p : walk.filter(Files::isRegularFile).toList()) {
        String rel = root.relativize(p).toString().replace('\\', '/');
        for (String g : globs) {
          if (com.metamorph.util.GlobMatch.leakMatch(rel, g)) {
            hits.add(rel);
            break;
          }
        }
      }
    }
    if (!hits.isEmpty()) {
      String shown = hits.stream().sorted().limit(8).reduce((a, b) -> a + ", " + b).orElse("");
      throw new IllegalArgumentException("answer-key path(s) would enter model context: " + shown);
    }
  }

  private static void assertNoGraderVerdicts(Path dest) throws IOException {
    if (!Files.exists(dest)) {
      return;
    }
    Files.walk(dest)
        .filter(Files::isRegularFile)
        .forEach(
            p -> {
              try {
                String low = Files.readString(p).toLowerCase(Locale.ROOT);
                if (low.contains("grader verdict") || low.contains("rubric score")) {
                  throw new IllegalArgumentException("grader language in evidence: " + p);
                }
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
  }

  /** Public helper for selection/reflection contexts. */
  public static int copySourcePublic(BenchmarkAdapter adapter, String taskId, Path root)
      throws IOException {
    return copySource(adapter, taskId, root);
  }

  private static int copySource(BenchmarkAdapter adapter, String taskId, Path root)
      throws IOException {
    Path src = adapter.taskFiles(taskId);
    if (src == null || !Files.isDirectory(src)) {
      return 0;
    }
    Path dst = root.resolve("source");
    return copyTree(src, dst);
  }

  private static String writeRollout(
      Path dest, RunView r, BenchmarkAdapter adapter, int trunc, boolean namesOnly) {
    try {
      String traj = adapter.trajectory(r.runDir(), trunc);
      Files.writeString(dest.resolve("rollouts").resolve(r.label() + ".md"), traj);
      Path ddir = dest.resolve("deliverables").resolve(r.label());
      Files.createDirectories(ddir);
      if (namesOnly) {
        Files.writeString(ddir.resolve("INDEX.md"), String.join("\n", adapter.deliverableFiles(r.runDir()).keySet()));
      } else {
        Map<String, String> files = adapter.deliverableFiles(r.runDir());
        StringBuilder index = new StringBuilder();
        for (var e : files.entrySet()) {
          Files.writeString(ddir.resolve(e.getKey()), e.getValue());
          index.append(e.getKey()).append('\n');
        }
        Files.writeString(ddir.resolve("INDEX.md"), index.toString());
      }
      return null;
    } catch (Exception exc) {
      return r.label() + ": " + exc.getClass().getSimpleName() + ": " + exc.getMessage();
    }
  }

  private static List<String> writePool(
      Path dest, List<PoolItem> pool, BenchmarkAdapter adapter, int trunc, boolean namesOnly)
      throws IOException {
    List<String> got = new ArrayList<>();
    if (pool == null || pool.isEmpty()) {
      return got;
    }
    for (PoolItem item : pool) {
      Path pdir = dest.resolve("pool").resolve(item.label());
      Files.createDirectories(pdir);
      if (item.harnessDir() != null && Files.isDirectory(item.harnessDir())) {
        copyTree(item.harnessDir(), pdir.resolve("harness"));
      }
      if (item.rollout() != null) {
        writeRollout(pdir, item.rollout(), adapter, trunc, namesOnly);
      }
      got.add(item.label());
    }
    return got;
  }

  private static int copyTree(Path from, Path to) throws IOException {
    Files.createDirectories(to);
    int[] n = {0};
    Files.walkFileTree(
        from,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Path rel = from.relativize(file);
            Path target = to.resolve(rel.toString());
            Files.createDirectories(target.getParent());
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            n[0]++;
            return FileVisitResult.CONTINUE;
          }
        });
    return n[0];
  }

  private static void deleteRecursive(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.deleteIfExists(file);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.deleteIfExists(dir);
            return FileVisitResult.CONTINUE;
          }
        });
  }
}
