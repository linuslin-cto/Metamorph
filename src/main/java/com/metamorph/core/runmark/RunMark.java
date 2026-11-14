package com.metamorph.core.runmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Trial identity: was this rollout finished, and by this configuration?
 *
 * <p>Port of {@code upstream.core.runmark}.
 */
public final class RunMark {
  public static final String MARK = ".adapt_complete";
  public static final int VERSION = 1;
  private static final ObjectMapper JSON =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

  private RunMark() {}

  private static boolean ignored(Path p) {
    String name = p.getFileName() == null ? "" : p.getFileName().toString();
    return p.toString().contains("__pycache__")
        || name.endsWith(".pyc")
        || name.equals(".DS_Store");
  }

  /** Stable digest of the harness tree; {@code null} means inherited base → {@code "base"}. */
  public static String fingerprint(Path harnessDir) throws Exception {
    if (harnessDir == null) {
      return "base";
    }
    if (!Files.isDirectory(harnessDir)) {
      return "absent";
    }
    MessageDigest h = MessageDigest.getInstance("SHA-1");
    try (Stream<Path> walk = Files.walk(harnessDir)) {
      walk.filter(Files::isRegularFile)
          .filter(p -> !MARK.equals(p.getFileName().toString()) && !ignored(p))
          .sorted()
          .forEach(
              p -> {
                try {
                  h.update(
                      harnessDir
                          .relativize(p)
                          .toString()
                          .replace('\\', '/')
                          .getBytes(StandardCharsets.UTF_8));
                  h.update((byte) 0);
                  try {
                    h.update(Files.readAllBytes(p));
                  } catch (Exception e) {
                    h.update("<unreadable>".getBytes(StandardCharsets.UTF_8));
                  }
                  h.update((byte) 0);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
    }
    return hex(h.digest()).substring(0, 16);
  }

  /** Stable digest of model-visible prompt + source files. */
  public static String inputsFingerprint(String taskPrompt, Path filesDir) throws Exception {
    MessageDigest h = MessageDigest.getInstance("SHA-1");
    h.update("prompt\0".getBytes(StandardCharsets.UTF_8));
    h.update(String.valueOf(taskPrompt).getBytes(StandardCharsets.UTF_8));
    h.update((byte) 0);
    if (filesDir != null && Files.isDirectory(filesDir)) {
      try (Stream<Path> walk = Files.walk(filesDir)) {
        walk.filter(Files::isRegularFile)
            .filter(p -> !ignored(p))
            .sorted()
            .forEach(
                p -> {
                  try {
                    h.update(
                        filesDir
                            .relativize(p)
                            .toString()
                            .replace('\\', '/')
                            .getBytes(StandardCharsets.UTF_8));
                    h.update((byte) 0);
                    try {
                      h.update(Files.readAllBytes(p));
                    } catch (Exception e) {
                      h.update("<unreadable>".getBytes(StandardCharsets.UTF_8));
                    }
                    h.update((byte) 0);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                });
      }
    }
    return hex(h.digest()).substring(0, 16);
  }

  public static Path markComplete(
      Path where, String runId, Path harnessDir, String inputs, Map<String, Object> extra)
      throws Exception {
    Files.createDirectories(where);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("version", VERSION);
    payload.put("run_id", runId);
    payload.put("at", System.currentTimeMillis() / 1000.0);
    payload.put("harness", fingerprint(harnessDir));
    if (extra != null) {
      payload.putAll(extra);
    }
    if (inputs != null) {
      payload.put("inputs", inputs);
    }
    Path tmp = where.resolve(MARK + "." + ProcessHandle.current().pid() + ".tmp");
    JSON.writeValue(tmp.toFile(), payload);
    Path finalMark = where.resolve(MARK);
    Files.move(tmp, finalMark, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    return finalMark;
  }

  public static Path markComplete(Path where, String runId) throws Exception {
    return markComplete(where, runId, null, null, null);
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> readMark(Path where) {
    try {
      Path mark = where.resolve(MARK);
      if (!Files.isRegularFile(mark)) {
        return null;
      }
      return JSON.readValue(Files.readString(mark), Map.class);
    } catch (Exception e) {
      return null;
    }
  }

  public static boolean isComplete(Path where) {
    return isComplete(where, null, true, null);
  }

  public static boolean isComplete(Path where, String inputs) {
    return isComplete(where, null, true, inputs);
  }

  public static boolean isComplete(
      Path where, Path harnessDir, boolean matchHarness, String inputs) {
    try {
      Map<String, Object> m = readMark(where);
      if (m == null || !Integer.valueOf(VERSION).equals(asInt(m.get("version")))) {
        return false;
      }
      if (inputs != null && !inputsMatch(m, inputs)) {
        return false;
      }
      if (!matchHarness) {
        return true;
      }
      return fingerprint(harnessDir).equals(String.valueOf(m.get("harness")));
    } catch (Exception e) {
      return false;
    }
  }

  public static String whyNot(Path where) {
    return whyNot(where, null, null);
  }

  public static String whyNot(Path where, Path harnessDir, String inputs) {
    try {
      Map<String, Object> m = readMark(where);
      if (m == null) {
        return "no " + MARK + " -- never finished, or finished before completion was recorded";
      }
      if (!Integer.valueOf(VERSION).equals(asInt(m.get("version")))) {
        return "marker version " + m.get("version") + " != " + VERSION;
      }
      if (!inputsMatch(m, inputs)) {
        return "task inputs "
            + m.get("inputs")
            + " != "
            + inputs
            + " -- the task prompt or its source files changed since this rollout finished";
      }
      String want = fingerprint(harnessDir);
      Object got = m.get("harness");
      if (!want.equals(String.valueOf(got))) {
        return "harness fingerprint "
            + got
            + " != "
            + want
            + " -- produced by a different configuration";
      }
      return "reusable";
    } catch (Exception e) {
      return "unreadable marker: " + e.getMessage();
    }
  }

  private static boolean inputsMatch(Map<String, Object> m, String inputs) {
    Object recorded = m.get("inputs");
    return recorded == null || inputs == null || recorded.equals(inputs);
  }

  private static Integer asInt(Object v) {
    if (v instanceof Number n) {
      return n.intValue();
    }
    if (v == null) {
      return null;
    }
    try {
      return Integer.parseInt(String.valueOf(v));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String hex(byte[] d) {
    StringBuilder sb = new StringBuilder();
    for (byte b : d) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
