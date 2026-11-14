package com.metamorph.cli;

import com.metamorph.benchmarks.jb.JobBenchAdapter;
import com.metamorph.benchmarks.jb.JobBenchExitNote;
import com.metamorph.benchmarks.jb.JobBenchIsolation;
import com.metamorph.benchmarks.lab.LabAdapter;
import com.metamorph.benchmarks.lab.LabDeliverables;
import com.metamorph.config.Config;
import com.metamorph.benchmarks.wb.WorkBuddyAdapter;
import com.metamorph.benchmarks.wb.WorkBuddyDeliverables;
import com.metamorph.demo.DemoAdapter;
import com.metamorph.substrate.CodexPreflight;
import com.metamorph.substrate.CodexSurfaces;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "check", description = "Preflight checks for offline Metamorph readiness")
public final class CheckCommand implements Runnable {
  @Option(names = "--bench", defaultValue = "demo", description = "demo | lab | jb | cx | wb")
  String bench;

  @Override
  public void run() {
    List<String> errors = new ArrayList<>();
    String javaVer = System.getProperty("java.version", "");
    int major = javaMajor(javaVer);
    if (major < 21) {
      errors.add("JDK 21+ required, found Java " + javaVer);
    }
    try {
      checkPython(errors);
    } catch (Exception e) {
      errors.add("python check failed: " + e.getMessage());
    }
    Path tmp = null;
    try {
      tmp = Files.createTempDirectory("metamorph-check-");
      if ("lab".equalsIgnoreCase(bench)) {
        LabAdapter adapter = new LabAdapter(tmp);
        Path run = tmp.resolve("sample-run");
        Files.createDirectories(run.resolve("output").resolve("nested"));
        Files.writeString(run.resolve("output").resolve("nested").resolve("note.txt"), "ok");
        Map<String, String> files = adapter.deliverableFiles(run);
        if (!files.containsKey("nested/note.txt")) {
          errors.add("LabAdapter deliverable extraction missed nested/note.txt");
        }
        System.out.println(
            "Ready: lab/"
                + adapter.substrate().name()
                + "; "
                + adapter.tasks().size()
                + " tasks (execute needs harvey-labs). Cap="
                + LabDeliverables.DEFAULT_CAP);
      } else if ("jb".equalsIgnoreCase(bench)) {
        checkJobBench(tmp, errors);
      } else if ("wb".equalsIgnoreCase(bench) || "workbuddy".equalsIgnoreCase(bench)) {
        WorkBuddyAdapter adapter = new WorkBuddyAdapter(tmp);
        Path run = tmp.resolve("sample-run");
        Files.createDirectories(run.resolve("output"));
        Files.writeString(run.resolve("output").resolve("note.txt"), "x".repeat(200));
        if (!adapter.delivered(run)) {
          errors.add("WorkBuddyAdapter.delivered should accept >= " + WorkBuddyDeliverables.MIN_BODY_CHARS + " body chars");
        }
        if (!Files.isRegularFile(adapter.baseHarness().resolve("systemprompt.md"))) {
          errors.add("WorkBuddy base harness missing systemprompt.md");
        }
        System.out.println(
            "Ready: wb/"
                + adapter.substrate().name()
                + "; base harness + deliverable threshold OK (execute needs WB runtime).");
      } else if ("cx".equalsIgnoreCase(bench) || "codex".equalsIgnoreCase(bench)) {
        if (CodexSurfaces.CODEX_SURFACE.alwaysOn().isEmpty()) {
          errors.add("CodexSurfaces missing always-on AGENTS.md");
        }
        CodexPreflight.Result pf = CodexPreflight.check(false, false);
        if (pf.ok()) {
          System.out.println(
              "Ready: codex surface + Responses URL shape OK (binary/LiveLLM not required).");
        } else {
          System.out.println(
              "Codex surface OK; Responses endpoint not configured yet:");
          for (String e : pf.errors()) {
            System.out.println("  - " + e);
          }
          // Soft: offline check still passes without live bridge credentials.
        }
      } else {
        DemoAdapter adapter = new DemoAdapter(tmp);
        if (adapter.tasks().size() != 2) {
          errors.add("DemoAdapter expected 2 tasks, got " + adapter.tasks().size());
        }
        Path run = adapter.execute("ledger-1", adapter.baseHarness(), "check/base");
        Map<String, Object> score = adapter.score(run, "ledger-1");
        // Base harness sums positives only → should fail the signed-sum criterion.
        if (score == null || !Integer.valueOf(0).equals(score.get("n_passed"))) {
          errors.add("DemoAdapter base score should be 0/1 before adaptation");
        }
        System.out.println(
            "Ready: demo/"
                + adapter.substrate().name()
                + "; "
                + adapter.tasks().size()
                + " tasks. Model access is not exercised.");
      }
    } catch (Exception e) {
      errors.add(bench + " adapter preflight: " + e.getMessage());
    } finally {
      if (tmp != null) {
        try {
          deleteRecursive(tmp);
        } catch (Exception ignored) {
          // best-effort cleanup
        }
      }
    }
    for (String error : errors) {
      System.out.println("FAIL: " + error);
    }
    if (!errors.isEmpty()) {
      System.out.println("Setup incomplete: " + errors.size() + " issue(s).");
      System.exit(1);
    }
    System.out.println("Metamorph check: Java " + javaVer + " OK");
  }

  private static void checkJobBench(Path tmp, List<String> errors) throws Exception {
    Path tasks = tmp.resolve("tasks/t1/task_folder");
    Files.createDirectories(tasks);
    Files.writeString(tasks.resolve("TASK_INSTRUCTIONS.txt"), "write a memo");
    Path render = tmp.resolve("render");
    Files.createDirectories(render.resolve(".opencode"));
    JobBenchAdapter adapter = new JobBenchAdapter(tmp);
    Path ws = adapter.stageRollout("t1", "base1", render);
    if (!Files.isDirectory(ws.resolve("output"))
        || !Files.isRegularFile(ws.resolve("task_folder/TASK_INSTRUCTIONS.txt"))) {
      errors.add("JobBench staging did not materialize task_folder/output");
    }
    Map<String, String> env = adapter.rolloutEnv(ws, Map.of("PATH", "/usr/bin"));
    if (!JobBenchIsolation.allows(env.get("OPENCODE_PERMISSION"), ws.resolve("output/a.docx"))) {
      errors.add("JobBench isolation fence rejected own workspace output");
    }
    Path traj = tmp.resolve("traj.jsonl");
    JobBenchExitNote.noteExit(traj, tmp.resolve("run.log"), 124, "1", true);
    String note = JobBenchExitNote.renderErrorLines(traj);
    if (!note.contains("stopped at the 1 s time limit")) {
      errors.add("JobBench exit note missing time-limit wording");
    }
    System.out.println(
        "Ready: jb/"
            + adapter.substrate().name()
            + "; "
            + adapter.tasks().size()
            + " tasks (execute needs bun/opencode). Isolation+staging+exit-note OK.");
  }

  private static void checkPython(List<String> errors) throws Exception {
    String py = Config.env("PYTHON", Config.env("METAMORPH_PYTHON", "python"));
    ProcessBuilder pb = new ProcessBuilder(py, "--version");
    pb.redirectErrorStream(true);
    Process p = pb.start();
    boolean finished = p.waitFor(10, TimeUnit.SECONDS);
    if (!finished) {
      p.destroyForcibly();
      errors.add("python timed out (" + py + ")");
      return;
    }
    String out = new String(p.getInputStream().readAllBytes()).strip();
    if (p.exitValue() != 0) {
      errors.add("python not runnable (" + py + "): " + out);
    } else {
      System.out.println("Python: " + out);
    }
  }

  private static int javaMajor(String version) {
    if (version == null || version.isBlank()) {
      return 0;
    }
    String v = version.startsWith("1.") ? version.substring(2) : version;
    int dot = v.indexOf('.');
    String head = dot < 0 ? v : v.substring(0, dot);
    try {
      return Integer.parseInt(head.replaceAll("[^0-9].*$", ""));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static void deleteRecursive(Path root) throws Exception {
    if (!Files.exists(root)) {
      return;
    }
    try (var walk = Files.walk(root)) {
      List<Path> paths = walk.sorted((a, b) -> b.compareTo(a)).toList();
      for (Path p : paths) {
        Files.deleteIfExists(p);
      }
    }
  }
}
