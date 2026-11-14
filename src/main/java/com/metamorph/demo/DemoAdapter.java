package com.metamorph.demo;

import com.metamorph.config.Config;
import com.metamorph.core.spec.BenchPrompts;
import com.metamorph.core.spec.BenchSpec;
import com.metamorph.core.spec.BenchmarkAdapter;
import com.metamorph.core.spec.ChannelSpec;
import com.metamorph.core.spec.SubstrateLike;
import com.metamorph.core.tree.HarnessTree;
import com.metamorph.substrate.OpencodeSubstrate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic local adapter for exercising the real adaptation workflow. */
public final class DemoAdapter implements BenchmarkAdapter {
  public static final String BASE_SCRIPT =
      """
      import csv
      import sys
      from decimal import Decimal

      with open(sys.argv[1]) as source:
          amounts = [Decimal(row['amount']) for row in csv.DictReader(source)]
      total = sum((amount for amount in amounts if amount > 0), Decimal(0))
      print(f'Total: {total:.2f}')
      """;

  private final Path root;
  private final OpencodeSubstrate substrate = new OpencodeSubstrate();
  private final BenchSpec spec;
  public final AtomicInteger executionCount = new AtomicInteger();

  public DemoAdapter(Path root) throws Exception {
    this.root = root;
    ChannelSpec channel = new ChannelSpec("opencode", "local/deterministic");
    this.spec =
        BenchSpec.builder(
                "demo",
                root.resolve("runs"),
                new BenchPrompts("reconcile signed amounts in a transaction ledger"))
            .installMode("workspace_config")
            .channels(Map.of("proposer", channel, "selector", channel))
            .leakGlobs("reference_answer.txt")
            .build();

    Map<String, String> base = new LinkedHashMap<>();
    for (String name : substrate.surface().alwaysOn()) {
      base.put(name, "");
    }
    base.put("systemprompt.md", "Reconcile the ledger using python scripts/summarize.py.\n");
    base.put("scripts/summarize.py", BASE_SCRIPT);
    HarnessTree.writeTree(substrate.surface(), base, baseHarness(), false);

    writeLedger("ledger-1", "100.25\n-20.10\n5.00\n");
    writeLedger("ledger-2", "35.50\n-10.25\n-5.00\n");
  }

  private void writeLedger(String taskId, String amounts) throws Exception {
    Path source = taskFiles(taskId);
    Files.createDirectories(source);
    Files.writeString(source.resolve("ledger.csv"), "amount\n" + amounts, StandardCharsets.UTF_8);
  }

  @Override
  public BenchSpec spec() {
    return spec;
  }

  @Override
  public SubstrateLike substrate() {
    return substrate;
  }

  @Override
  public List<String> tasks(String split) {
    return List.of("ledger-1", "ledger-2");
  }

  @Override
  public String domainOf(String taskId) {
    return "accounting";
  }

  @Override
  public String taskPrompt(String taskId) {
    return "Sum every signed amount in ledger.csv. Return Total: with two decimal places.";
  }

  @Override
  public Path taskFiles(String taskId) {
    return root.resolve("sources").resolve(taskId);
  }

  @Override
  public Path baseHarness() {
    return root.resolve("base");
  }

  @Override
  public void validateTree(Map<String, String> harness) {
    if (!harness.containsKey("scripts/summarize.py")) {
      throw new IllegalArgumentException("The local executor requires scripts/summarize.py");
    }
  }

  @Override
  public Path execute(String taskId, Path harnessDir, String runId, Integer timeout) {
    executionCount.incrementAndGet();
    try {
      Path runDir = root.resolve("executions").resolve(sanitize(runId));
      Files.createDirectories(runDir);
      Path script =
          (harnessDir == null ? baseHarness() : harnessDir).resolve("scripts/summarize.py");
      Path csv = taskFiles(taskId).resolve("ledger.csv");
      String stdout = runPython(script, csv, timeout == null ? 10 : timeout);
      Files.writeString(runDir.resolve("answer.txt"), stdout, StandardCharsets.UTF_8);
      Files.writeString(
          runDir.resolve("trajectory.md"),
          "[ACTION] Run scripts/summarize.py on ledger.csv\n[OBSERVATION]\n" + stdout,
          StandardCharsets.UTF_8);
      return runDir;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String sanitize(String runId) {
    return runId.replace('/', '_').replace('\\', '_').replace(':', '_');
  }

  private static String runPython(Path script, Path csv, int timeoutSec) throws Exception {
    List<String> cmd = new ArrayList<>();
    cmd.add(pythonExecutable());
    cmd.add(script.toAbsolutePath().toString());
    cmd.add(csv.toAbsolutePath().toString());
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.redirectErrorStream(true);
    Map<String, String> env = pb.environment();
    env.clear();
    env.putAll(Config.scrubEnv());
    env.put("PATH", System.getenv("PATH") == null ? "" : System.getenv("PATH"));
    env.put(
        "SystemRoot",
        System.getenv("SystemRoot") == null ? "C:\\Windows" : System.getenv("SystemRoot"));
    Process p = pb.start();
    boolean finished = p.waitFor(timeoutSec, TimeUnit.SECONDS);
    if (!finished) {
      p.destroyForcibly();
      throw new RuntimeException("script timed out");
    }
    String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (p.exitValue() != 0) {
      throw new RuntimeException("script failed: " + out);
    }
    return out;
  }

  private static String pythonExecutable() {
    String fromEnv = Config.env("PYTHON", Config.env("METAMORPH_PYTHON", null));
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv;
    }
    return "python";
  }

  @Override
  public boolean delivered(Path runDir) {
    return Files.isRegularFile(runDir.resolve("answer.txt"));
  }

  @Override
  public String trajectory(Path runDir, int obsTrunc) {
    try {
      return Files.readString(runDir.resolve("trajectory.md"), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String deliverableText(Path runDir) {
    try {
      return Files.readString(runDir.resolve("answer.txt"), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Map<String, String> deliverableFiles(Path runDir) {
    return Map.of("answer.txt", deliverableText(runDir));
  }

  /**
   * Offline grade: one criterion — answer contains the signed-sum {@code Total:} line the task
   * asks for (base harness only sums positives; adaptation must reach these).
   */
  @Override
  public Map<String, Object> score(Path runDir, String taskId) {
    String expected =
        switch (taskId) {
          case "ledger-1" -> "Total: 85.15";
          case "ledger-2" -> "Total: 20.25";
          default -> null;
        };
    if (expected == null) {
      return null;
    }
    String got = deliverableText(runDir);
    if (got != null) {
      got = got.replace("\r\n", "\n");
    }
    boolean pass = got != null && got.contains(expected);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("n_passed", pass ? 1 : 0);
    out.put("n_criteria", 1);
    out.put("all_pass", pass);
    out.put("metric", "total_line");
    return out;
  }
}
