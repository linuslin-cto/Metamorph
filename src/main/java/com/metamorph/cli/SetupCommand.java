package com.metamorph.cli;

import com.metamorph.config.Config;
import com.metamorph.install.MissingRunners;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Offline setup/preflight stub for pinned benchmark checkouts. Reports what a full Python {@code
 * metamorph-setup} would install; does not clone or patch upstream by default ({@code --dry-run} is
 * the only supported mode until install runners are wired).
 */
@Command(
    name = "setup",
    mixinStandardHelpOptions = true,
    description = "Report (dry-run) what installing a pinned benchmark would require")
public final class SetupCommand implements Callable<Integer> {
  private static final Map<String, String> BENCH_DIRS =
      Map.of(
          "jb", "job-bench-eval",
          "lab", "harvey-labs",
          "wb", "workbuddy-bench");

  @Parameters(index = "0", description = "jb | lab | wb")
  String benchmark;

  @Option(names = "--substrate", defaultValue = "oc", description = "oc | cx")
  String substrate;

  @Option(names = "--subset", description = "wb only: office | web | code")
  String subset;

  @Option(
      names = "--dry-run",
      negatable = true,
      defaultValue = "true",
      description = "Only print the plan (default true; live install not wired yet)")
  boolean dryRun;

  @Override
  public Integer call() {
    String bench = benchmark == null ? "" : benchmark.strip().toLowerCase();
    if (!BENCH_DIRS.containsKey(bench)) {
      System.err.println("unknown benchmark '" + benchmark + "'; expected jb|lab|wb");
      return 2;
    }
    if (subset != null && !subset.isBlank() && !"wb".equals(bench)) {
      System.err.println("--subset only applies to wb");
      return 2;
    }
    String sub = substrate == null ? "oc" : substrate.strip().toLowerCase();
    if (!("oc".equals(sub) || "cx".equals(sub))) {
      System.err.println("unknown substrate '" + substrate + "'; expected oc|cx");
      return 2;
    }

    Path deps = Config.projectRoot().resolve("dependencies");
    Path benchRoot = deps.resolve(BENCH_DIRS.get(bench));
    List<String> plan = new ArrayList<>();
    plan.add("clone/patch opencode under " + deps.resolve("opencode"));
    plan.add("npm install bun@pinned under dependencies/tools");
    plan.add("bun install in OpenCode tree");
    plan.add("clone/patch " + BENCH_DIRS.get(bench) + " → " + benchRoot);
    plan.add("uv sync --locked --python 3.12 in benchmark repo");
    if ("jb".equals(bench)) {
      plan.add("download JobBench task assets");
    } else if ("lab".equals(bench)) {
      plan.add("install harvey-labs judge extras");
    } else {
      plan.add("install WorkBuddy harness YAML overlays");
      if (subset != null && !subset.isBlank()) {
        plan.add("subset filter: " + subset);
      } else {
        plan.add("subsets: office, web, code");
      }
    }
    if ("cx".equals(sub)) {
      plan.add("Codex dist + bridge files under " + Config.CODEX_DIST + " (metamorph bridge)");
    }

    Map<String, Boolean> present = new LinkedHashMap<>();
    present.put("dependencies/", Files.isDirectory(deps));
    present.put(BENCH_DIRS.get(bench), Files.isDirectory(benchRoot));
    present.put("opencode", Files.isDirectory(deps.resolve("opencode")));
    if ("cx".equals(sub)) {
      present.put("codex binary", Files.isRegularFile(Path.of(Config.CODEX_DIST).resolve("codex")));
    }

    System.out.println(
        "setup "
            + bench
            + " --substrate "
            + sub
            + (dryRun ? " (dry-run)" : " (live install not wired — forcing dry-run)"));
    System.out.println("Plan:");
    for (String step : plan) {
      System.out.println("  - " + step);
    }
    System.out.println("Local presence:");
    for (var e : present.entrySet()) {
      System.out.println("  " + (e.getValue() ? "[x]" : "[ ]") + " " + e.getKey());
    }
    String missing = MissingRunners.report();
    if (!missing.isBlank()) {
      System.out.println("Runners/patches: " + missing);
    } else {
      System.out.println("Runners/patches: third_party complete");
    }
    if (!dryRun) {
      System.out.println(
          "Note: Java setup does not yet clone/patch upstream; re-run with default --dry-run or use the Python upstream setup.");
    }
    return 0;
  }
}
