package com.metamorph.benchmarks.jb;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Close a JobBench trajectory with an error event when the executor did not exit cleanly. Ports
 * {@code jb_opencode_runner._note_exit}.
 */
public final class JobBenchExitNote {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

  private JobBenchExitNote() {}

  public static void noteExit(Path trajFile, Path logFile, int code, String limit, boolean kept)
      throws Exception {
    if (code == 0) {
      return;
    }
    String message = message(code, limit, kept);
    Map<String, Object> error = new LinkedHashMap<>();
    error.put("name", "RunnerExit");
    error.put("data", Map.of("message", message));
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("type", "error");
    event.put("timestamp", System.currentTimeMillis());
    event.put("error", error);
    String line = "\n" + JSON.writeValueAsString(event) + "\n";
    if (trajFile.getParent() != null) {
      Files.createDirectories(trajFile.getParent());
    }
    if (!Files.exists(trajFile)) {
      Files.writeString(trajFile, "", StandardCharsets.UTF_8);
    }
    Files.writeString(trajFile, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    String stamp = LocalTime.now().format(HMS);
    String logLine = "[" + stamp + "] EXIT " + code + ": " + message + "\n";
    if (logFile.getParent() != null) {
      Files.createDirectories(logFile.getParent());
    }
    Files.writeString(
        logFile,
        logLine,
        StandardCharsets.UTF_8,
        Files.exists(logFile) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
  }

  public static String message(int code, String limit, boolean kept) {
    String span = limit != null && limit.chars().allMatch(Character::isDigit) ? limit + " s" : limit;
    String why;
    if (code == 124) {
      why = "was stopped at the " + span + " time limit (exit 124)";
    } else if (code == -9 || code == 137) {
      why =
          "was killed with SIGKILL ("
              + code
              + "), after the "
              + span
              + " time limit or by the system";
    } else if (code < 0) {
      why = "was killed by signal " + (-code);
    } else {
      why = "exited with status " + code;
    }
    String outcome =
        kept
            ? "files already in the output directory were kept as the deliverable"
            : "the output directory was empty";
    return "Rollout runner: the executor " + why + "; " + outcome + ".";
  }

  /**
   * Render trajectory error lines via the shared OpenCode grammar (same consumer as Python {@code
   * render_events}).
   */
  public static String renderErrorLines(Path trajFile) {
    if (!Files.isRegularFile(trajFile)) {
      return "";
    }
    String rendered =
        com.metamorph.substrate.OpencodeEventRenderer.renderEvents(trajFile, 1000, 10_000);
    if (rendered.startsWith("(")) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    for (String line : rendered.split("\\R")) {
      if (line.startsWith("[ERROR] ")) {
        out.append(line).append('\n');
      }
    }
    return out.toString();
  }
}
