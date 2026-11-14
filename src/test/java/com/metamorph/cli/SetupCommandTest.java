package com.metamorph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class SetupCommandTest {
  @Test
  void dryRunPrintsPlanForJbAndRejectsBadSubset() {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    PrintStream prev = System.out;
    System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
    int code;
    try {
      code = new CommandLine(new SetupCommand()).execute("jb", "--substrate", "oc");
    } finally {
      System.setOut(prev);
    }
    assertEquals(0, code);
    String out = buf.toString(StandardCharsets.UTF_8);
    assertTrue(out.contains("dry-run"), out);
    assertTrue(out.contains("job-bench-eval"), out);
    assertTrue(out.contains("download JobBench"), out);

    ByteArrayOutputStream err = new ByteArrayOutputStream();
    PrintStream prevErr = System.err;
    System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
    int bad;
    try {
      bad = new CommandLine(new SetupCommand()).execute("lab", "--subset", "office");
    } finally {
      System.setErr(prevErr);
    }
    assertEquals(2, bad);
    assertTrue(err.toString(StandardCharsets.UTF_8).contains("--subset only applies to wb"));
  }
}
