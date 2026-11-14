package com.metamorph.benchmarks.lab;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Offline LAB deliverable extraction: read {@code output/} (or {@code workspace/output/}) without
 * the upstream harvey-labs reader. Text suffixes are decoded; other files get a binary stub line.
 *
 * <p>Mirrors the shape of {@code LabAdapter.deliverable_files} / the Python {@code _EXTRACT_SRC}
 * fallback path so nested paths stay relative POSIX keys.
 */
public final class LabDeliverables {
  public static final int DEFAULT_CAP = 60_000;

  private static final Set<String> TEXT_SUFFIXES =
      Set.of(
          ".md", ".txt", ".csv", ".json", ".xml", ".html", ".py", ".yaml", ".yml", ".tsv");

  private LabDeliverables() {}

  public static Path outputDir(Path runDir) {
    Path direct = runDir.resolve("output");
    if (Files.isDirectory(direct)) {
      return direct;
    }
    Path nested = runDir.resolve("workspace").resolve("output");
    if (Files.isDirectory(nested)) {
      return nested;
    }
    return direct;
  }

  public static Map<String, String> extract(Path runDir) throws Exception {
    return extract(runDir, DEFAULT_CAP);
  }

  public static Map<String, String> extract(Path runDir, int cap) throws Exception {
    Path base = outputDir(runDir);
    Map<String, String> out = new LinkedHashMap<>();
    if (!Files.isDirectory(base)) {
      return out;
    }
    try (Stream<Path> walk = Files.walk(base)) {
      for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
        String rel = base.relativize(p).toString().replace('\\', '/');
        String text = readAsText(p);
        if (cap > 0 && text.length() > cap) {
          text =
              text.substring(0, cap)
                  + "\n\n... [truncated, "
                  + (text.length() - cap)
                  + " more chars]";
        }
        out.put(rel, text);
      }
    }
    return out;
  }

  public static String asContents(Map<String, String> files) {
    if (files == null || files.isEmpty()) {
      return "(this run produced NO deliverables)";
    }
    StringBuilder sb = new StringBuilder();
    for (var e : files.entrySet()) {
      if (!sb.isEmpty()) {
        sb.append("\n\n");
      }
      String body = e.getValue() == null ? "" : e.getValue().strip();
      sb.append("----- FILE: ")
          .append(e.getKey())
          .append(" -----\n")
          .append(body.isEmpty() ? "(empty file)" : body);
    }
    return sb.toString();
  }

  static String readAsText(Path p) {
    String suffix = suffix(p);
    try {
      if (TEXT_SUFFIXES.contains(suffix)) {
        return Files.readString(p, StandardCharsets.UTF_8);
      }
      long size = Files.size(p);
      // Best-effort UTF-8 for unknown suffixes that happen to be text; else binary stub.
      byte[] bytes = Files.readAllBytes(p);
      String asUtf8 = new String(bytes, StandardCharsets.UTF_8);
      if (asUtf8.indexOf('\uFFFD') < 0 && looksMostlyText(asUtf8)) {
        return asUtf8;
      }
      return "(binary file: " + p.getFileName() + ", " + size + " bytes)";
    } catch (Exception exc) {
      return "(error reading " + p.getFileName() + ": " + exc + ")";
    }
  }

  private static String suffix(Path p) {
    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot);
  }

  private static boolean looksMostlyText(String s) {
    if (s.isEmpty()) {
      return true;
    }
    int sample = Math.min(s.length(), 512);
    int ctrl = 0;
    for (int i = 0; i < sample; i++) {
      char c = s.charAt(i);
      if (c < 0x09 || (c > 0x0d && c < 0x20)) {
        ctrl++;
      }
    }
    return ctrl * 20 < sample;
  }
}
