package com.metamorph.benchmarks.jb;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Stage / reset a JobBench OpenCode workspace. Ports {@code jb_opencode_runner._stage} and
 * {@code _reset_task}.
 */
public final class JobBenchStaging {
  private JobBenchStaging() {}

  /** Wipe {@code workspace}, copy task + render surface, leave empty {@code output/}. */
  public static void stage(Path taskDir, Path renderDir, Path workspace) throws Exception {
    if (Files.exists(workspace)) {
      deleteRecursive(workspace);
    }
    Files.createDirectories(workspace.resolve("output"));
    copyDirectory(taskDir, workspace.resolve("task_folder"));
    Path opencode = renderDir.resolve(".opencode");
    if (Files.isDirectory(opencode)) {
      copyDirectory(opencode, workspace.resolve(".opencode"));
    }
    Path agents = renderDir.resolve("AGENTS.md");
    if (Files.isRegularFile(agents)) {
      Files.copy(agents, workspace.resolve("AGENTS.md"), StandardCopyOption.REPLACE_EXISTING);
    }
    Path scripts = renderDir.resolve("scripts");
    if (Files.isDirectory(scripts)) {
      copyDirectory(scripts, workspace.resolve("scripts"));
    }
    JobBenchIsolation.stageSandbox(workspace);
  }

  /** Restore a pristine task_folder and empty output/ before a retry. */
  public static void resetTask(Path taskDir, Path workspace) throws Exception {
    Path folder = workspace.resolve("task_folder");
    Path output = workspace.resolve("output");
    if (Files.exists(folder)) {
      deleteRecursive(folder);
    }
    if (Files.exists(output)) {
      deleteRecursive(output);
    }
    copyDirectory(taskDir, folder);
    Files.createDirectories(output);
    JobBenchIsolation.stageSandbox(workspace);
  }

  static void copyDirectory(Path src, Path dest) throws Exception {
    Files.createDirectories(dest);
    try (Stream<Path> walk = Files.walk(src)) {
      for (Path p : walk.toList()) {
        Path rel = src.relativize(p);
        Path target = dest.resolve(rel.toString());
        if (Files.isDirectory(p)) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  static void deleteRecursive(Path root) throws Exception {
    if (!Files.exists(root)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(root)) {
      for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(p);
      }
    }
  }
}
