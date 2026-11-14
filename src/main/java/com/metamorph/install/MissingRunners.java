package com.metamorph.install;

import com.metamorph.config.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports which setup runners/patches are absent under {@code third_party/}. Ports {@code
 * install.missing_runners}.
 */
public final class MissingRunners {
  public static final Map<String, String> PATCHES =
      Map.of(
          "opencode", "opencode-compat.diff",
          "job-bench-eval", "job-bench-eval-judge.diff",
          "harvey-labs", "harvey-labs-judge-vertex-anthropic.diff",
          "workbuddy-bench", "workbuddy-bench-runtime.diff");

  public static final List<String> RUNNERS =
      List.of(
          "jb_runner/run_benchmark_codex_cli_adapt.sh",
          "lab_runner/opencode/run_opencode.py",
          "wb_runner/agents/oc_agent.py");

  private MissingRunners() {}

  public static Path thirdPartyRoot() {
    return Config.projectRoot().resolve("third_party");
  }

  public static List<Path> requiredPaths(Path thirdParty) {
    List<Path> need = new ArrayList<>();
    for (String name : PATCHES.values()) {
      need.add(thirdParty.resolve("patches").resolve(name));
    }
    for (String name : RUNNERS) {
      need.add(thirdParty.resolve(name));
    }
    return need;
  }

  /** Empty string when complete; otherwise a single-line explanation. */
  public static String report() {
    Path root = thirdPartyRoot();
    List<Path> need = requiredPaths(root);
    List<Path> absent = need.stream().filter(p -> !Files.exists(p)).toList();
    if (absent.isEmpty()) {
      return "";
    }
    return "benchmark runners and patches are not included in this copy ("
        + absent.size()
        + " of "
        + need.size()
        + " missing under third_party/); see third_party/README.md";
  }

  public static Map<String, Boolean> checklist() {
    Path root = thirdPartyRoot();
    Map<String, Boolean> out = new LinkedHashMap<>();
    for (Path p : requiredPaths(root)) {
      out.put(root.relativize(p).toString().replace('\\', '/'), Files.exists(p));
    }
    return out;
  }
}
