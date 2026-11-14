package com.metamorph.benchmarks.jb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JobBenchStagingTest {
  @TempDir Path root;

  @Test
  void stageCopiesTaskRenderAndStartsCleanSandbox() throws Exception {
    Path task = root.resolve("tasks/t1/task_folder");
    Files.createDirectories(task);
    Files.writeString(task.resolve("TASK_INSTRUCTIONS.txt"), "do it");
    Path render = root.resolve("render");
    Files.createDirectories(render.resolve(".opencode"));
    Files.writeString(render.resolve(".opencode/config.json"), "{}");
    Files.writeString(render.resolve("AGENTS.md"), "stay in workspace");
    Files.createDirectories(render.resolve("scripts"));
    Files.writeString(render.resolve("scripts/helper.py"), "print(1)");

    JobBenchAdapter adapter = new JobBenchAdapter(root);
    Path ws = adapter.stageRollout("t1", "base1", render);
    assertTrue(Files.isRegularFile(ws.resolve("task_folder/TASK_INSTRUCTIONS.txt")));
    assertTrue(Files.isDirectory(ws.resolve("output")));
    assertTrue(Files.isRegularFile(ws.resolve(".opencode/config.json")));
    assertTrue(Files.isRegularFile(ws.resolve("AGENTS.md")));
    assertTrue(Files.isRegularFile(ws.resolve("scripts/helper.py")));
    assertTrue(Files.isDirectory(JobBenchIsolation.sandbox(ws).resolve("tmp")));

    Files.writeString(ws.resolve("output/stale.txt"), "old");
    Files.writeString(ws.resolve("task_folder/TASK_INSTRUCTIONS.txt"), "mutated");
    adapter.resetRollout("t1", ws);
    assertEquals("do it", Files.readString(ws.resolve("task_folder/TASK_INSTRUCTIONS.txt")));
    assertFalse(Files.exists(ws.resolve("output/stale.txt")));
    assertTrue(Files.isDirectory(ws.resolve("output")));
  }

  @Test
  void rolloutEnvCarriesPermissionAndIsolation() throws Exception {
    JobBenchAdapter adapter = new JobBenchAdapter(root);
    Path ws = root.resolve("hevo_ws/t1_probe_1");
    Files.createDirectories(ws);
    Map<String, String> env = adapter.rolloutEnv(ws, Map.of("PATH", "/usr/bin", "HOME", "/home/u"));
    assertTrue(env.containsKey("OPENCODE_PERMISSION"));
    assertTrue(env.get("NPM_CONFIG_PREFIX").contains("npm-global") || env.get("NPM_CONFIG_PREFIX").contains("npm-global".replace('/', java.io.File.separatorChar)));
    assertTrue(JobBenchIsolation.allows(env.get("OPENCODE_PERMISSION"), ws.resolve("output/a.docx")));
  }
}
