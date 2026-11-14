package com.metamorph.benchmarks.wb;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Offline WorkBuddy artifact helpers. Full office rendering needs the Python {@code wb_render}
 * bridge; this skeleton counts / lists files under {@code output/} for delivered() and selector
 * views.
 */
public final class WorkBuddyDeliverables {
  /** Python {@code delivered} threshold over artifact bodies. */
  public static final int MIN_BODY_CHARS = 200;

  private WorkBuddyDeliverables() {}

  public static Path outputDir(Path runDir) {
    return runDir.resolve("output");
  }

  public static Map<String, String> extract(Path runDir) throws Exception {
    Path base = outputDir(runDir);
    Map<String, String> out = new LinkedHashMap<>();
    if (!Files.isDirectory(base)) {
      return out;
    }
    try (Stream<Path> walk = Files.walk(base)) {
      for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
        String rel = base.relativize(p).toString().replace('\\', '/');
        out.put(rel, readBody(p));
      }
    }
    return out;
  }

  public static int bodyChars(Path runDir) throws Exception {
    int n = 0;
    for (String body : extract(runDir).values()) {
      n += body == null ? 0 : body.length();
    }
    return n;
  }

  public static String flatText(Path runDir) throws Exception {
    Map<String, String> files = extract(runDir);
    if (files.isEmpty()) {
      return "(this run produced NO deliverables)";
    }
    StringBuilder sb = new StringBuilder();
    for (var e : files.entrySet()) {
      if (!sb.isEmpty()) {
        sb.append("\n\n");
      }
      sb.append("----- FILE: ").append(e.getKey()).append(" -----\n").append(e.getValue());
    }
    return sb.toString();
  }

  static String readBody(Path p) {
    try {
      byte[] bytes = Files.readAllBytes(p);
      String asUtf8 = new String(bytes, StandardCharsets.UTF_8);
      if (asUtf8.indexOf('\uFFFD') < 0) {
        return asUtf8;
      }
      return "(binary file: " + p.getFileName() + ", " + bytes.length + " bytes)";
    } catch (Exception e) {
      return "(error reading " + p.getFileName() + ": " + e + ")";
    }
  }
}
