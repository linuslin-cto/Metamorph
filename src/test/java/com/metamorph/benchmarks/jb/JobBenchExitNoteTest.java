package com.metamorph.benchmarks.jb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JobBenchExitNoteTest {
  @TempDir Path root;

  static Stream<Arguments> signalWording() {
    return Stream.of(
        arguments(-9, "2400", "SIGKILL (-9), after the 2400 s time limit or by the system"),
        arguments(137, "2400", "SIGKILL (137), after the 2400 s time limit or by the system"),
        arguments(-11, "2400", "killed by signal 11"),
        arguments(124, "40m", "stopped at the 40m time limit (exit 124)"));
  }

  @ParameterizedTest
  @MethodSource("signalWording")
  void signalWordingMatchesPython(int code, String limit, String expected, @TempDir Path dir)
      throws Exception {
    Path traj = dir.resolve("t" + code + ".jsonl");
    Files.writeString(traj, "");
    JobBenchExitNote.noteExit(traj, dir.resolve("x.log"), code, limit, false);
    String rendered = JobBenchExitNote.renderErrorLines(traj);
    assertTrue(rendered.contains(expected), rendered);
    assertTrue(Files.readString(dir.resolve("x.log")).contains("EXIT " + code + ":"));
  }

  @Test
  void keptDeliverableWordingAndCleanExitLeftAlone() throws Exception {
    Path traj = root.resolve("kept.jsonl");
    Files.writeString(traj, "");
    JobBenchExitNote.noteExit(traj, root.resolve("a.log"), 124, "1", true);
    String rendered = JobBenchExitNote.renderErrorLines(traj);
    assertTrue(rendered.contains("stopped at the 1 s time limit (exit 124)"));
    assertTrue(rendered.contains("kept as the deliverable"));

    Path clean = root.resolve("clean.jsonl");
    Files.writeString(clean, "{\"type\":\"step_finish\"}\n");
    JobBenchExitNote.noteExit(clean, root.resolve("b.log"), 0, "30", false);
    assertFalse(Files.readString(clean).contains("\"error\""));
    assertFalse(Files.exists(root.resolve("b.log")));
  }

  @Test
  void crashedEmptyOutputSaysSo() throws Exception {
    Path traj = root.resolve("crash.jsonl");
    JobBenchExitNote.noteExit(traj, root.resolve("c.log"), 1, "60", false);
    String rendered = JobBenchExitNote.renderErrorLines(traj);
    assertTrue(rendered.contains("exited with status 1"));
    assertTrue(rendered.contains("the output directory was empty"));
  }
}
