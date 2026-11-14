package com.metamorph.benchmarks.jb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JobBenchIsolationTest {
  @TempDir Path root;

  @Test
  void laterSandboxCannotShareNpmPipOrTmpWithEarlier() throws Exception {
    Path tempBase = root.resolve("hevo_ws");
    Files.createDirectories(tempBase);
    Path first = tempBase.resolve("t1_base1_1");
    Path second = tempBase.resolve("t1_inv1_2");
    Files.createDirectories(first);
    Files.createDirectories(second);
    JobBenchIsolation.stageSandbox(first);
    JobBenchIsolation.stageSandbox(second);

    Path leakNpm =
        JobBenchIsolation.sandbox(first).resolve("npm-global/lib/node_modules/leaked-pkg/index.js");
    Path leakPip = JobBenchIsolation.sandbox(first).resolve("python-user/lib/leaked_mod.py");
    Path leakTmp = JobBenchIsolation.sandbox(first).resolve("tmp/leaked-scratch.txt");
    Files.createDirectories(leakNpm.getParent());
    Files.createDirectories(leakPip.getParent());
    Files.writeString(leakNpm, "left by earlier");
    Files.writeString(leakPip, "left by earlier");
    Files.writeString(leakTmp, "left by earlier");

    Map<String, String> env2 = JobBenchIsolation.isolationEnv(second, "/usr/bin", ":");
    assertFalse(Files.isRegularFile(Path.of(env2.get("NPM_CONFIG_PREFIX")).resolve("lib/node_modules/leaked-pkg/index.js")));
    assertFalse(Files.isRegularFile(Path.of(env2.get("PYTHONUSERBASE")).resolve("lib/leaked_mod.py")));
    assertFalse(Files.isRegularFile(Path.of(env2.get("TMPDIR")).resolve("leaked-scratch.txt")));
    assertNotEquals(
        JobBenchIsolation.isolationEnv(first, "/usr/bin", ":").get("TMPDIR"), env2.get("TMPDIR"));
  }

  @Test
  void installedCommandsStayOnPathHead() {
    // Use POSIX paths so ':' PATH joining (JobBench/Linux) is not split by Windows drive letters.
    Path ws = Path.of("/tmp/hevo_ws/ws");
    Map<String, String> env = JobBenchIsolation.isolationEnv(ws, "/usr/bin", ":");
    String npmBin = Path.of(env.get("NPM_CONFIG_PREFIX"), "bin").toString().replace('\\', '/');
    String userBin = Path.of(env.get("PYTHONUSERBASE"), "bin").toString().replace('\\', '/');
    String path = env.get("PATH").replace('\\', '/');
    assertEquals(npmBin + ":" + userBin + ":/usr/bin", path);
  }

  @Test
  void fenceBlocksSiblingRolloutButKeepsOwnDirsAndTmp() throws Exception {
    Path tempBase = root.resolve("hevo_ws");
    Path own = tempBase.resolve("t1_base1_1");
    Files.createDirectories(own.resolve("output"));
    String permission = JobBenchIsolation.permission(own, tempBase);
    Path sibling = tempBase.resolve("t1_dom1_99999").resolve("output").resolve("answer.docx");
    assertFalse(JobBenchIsolation.allows(permission, sibling));
    assertTrue(JobBenchIsolation.allows(permission, own.resolve("output").resolve("answer.docx")));
    assertTrue(
        JobBenchIsolation.allows(
            permission, JobBenchIsolation.sandbox(own, tempBase).resolve("tmp").resolve("x.txt")));
    assertTrue(JobBenchIsolation.allows(permission, Path.of("/tmp/scratch/notes.txt")));
  }

  @Test
  void tmpDirLeavesRoomForUnixSocketPaths() {
    Path workspace =
        Path.of("/tmp/hevo_ws")
            .resolve(("long-task-id".repeat(12)) + "_base1_4194304");
    String tmpdir = JobBenchIsolation.isolationEnv(workspace, "/usr/bin", ":").get("TMPDIR");
    assertTrue(tmpdir.length() + "/pymp-xxxxxxxx/listener-xxxxxxxx".length() <= 107);
  }
}
