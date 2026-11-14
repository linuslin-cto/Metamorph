package com.metamorph.cli;

import com.metamorph.core.judge.Judge;
import com.metamorph.core.judge.Scored;
import com.metamorph.core.report.Report;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "report", description = "Render judged selection summaries from judged.json")
public final class ReportCommand implements Runnable {
  @Option(names = "--judged", description = "Path to judged.json (repeatable via comma list)")
  String judged;

  @Option(names = "--ns-dir", description = "Namespace dir; reads <ns-dir>/judged.json")
  Path nsDir;

  @Option(names = "--bench", defaultValue = "")
  String bench;

  @Option(names = "--title", defaultValue = "Metamorph report")
  String title;

  @Option(names = "--modes", defaultValue = "agg,seq")
  String modes;

  @Override
  public void run() {
    try {
      List<Path> paths = new ArrayList<>();
      if (judged != null && !judged.isBlank()) {
        for (String p : judged.split(",")) {
          if (!p.isBlank()) {
            paths.add(Path.of(p.trim()));
          }
        }
      }
      if (nsDir != null) {
        paths.add(nsDir.resolve("judged.json"));
      }
      if (paths.isEmpty()) {
        throw new IllegalStateException("pass --judged and/or --ns-dir");
      }
      List<String> modeList =
          Arrays.stream(modes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
      List<Scored> rows = new ArrayList<>();
      Map<String, Integer> fellBack = new LinkedHashMap<>();
      Map<String, Double> usd = new LinkedHashMap<>();
      for (Path path : paths) {
        List<Scored> got = Judge.loadRows(path);
        if (got.isEmpty()) {
          System.err.println("  ! no judged rows at " + path);
          continue;
        }
        rows.addAll(got.stream().filter(r -> modeList.contains(r.mode)).toList());
        Path parent = path.getParent();
        if (parent != null) {
          spendAndFallbacks(parent, modeList, fellBack, usd);
        }
      }
      if (rows.isEmpty()) {
        throw new IllegalStateException("nothing to report");
      }
      List<Report.Summary> summaries = Report.summarise(rows, fellBack, usd);
      System.out.println(Report.render(summaries, title, bench));
      double spend = summaries.stream().mapToDouble(s -> s.usd).sum();
      if (spend > 0) {
        System.out.printf(
            "%n  improver (editors, composer, selection, reflection) spend across reported modes: $%.2f%n",
            spend);
      }
    } catch (Exception e) {
      System.err.println(e.getMessage() == null ? e.toString() : e.getMessage());
      System.exit(1);
    }
  }

  @SuppressWarnings("unchecked")
  public static void spendAndFallbacks(
      Path nsDir, List<String> modes, Map<String, Integer> fellBack, Map<String, Double> usd)
      throws Exception {
    if (!Files.isDirectory(nsDir)) {
      return;
    }
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    try (var stream = Files.list(nsDir)) {
      for (Path summaryPath :
          stream.map(p -> p.resolve("summary.json")).filter(Files::isRegularFile).sorted().toList()) {
        Map<String, Object> summary = mapper.readValue(Files.readString(summaryPath), Map.class);
        int k =
            summary.get("k") instanceof Number n
                ? n.intValue()
                : 0;
        Map<String, Object> modesMap =
            summary.get("modes") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> sels =
            summary.get("selections") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        for (String mode : modes) {
          String key = mode + "|" + k;
          Object recObj = modesMap.get(mode);
          Map<String, Object> rec = recObj instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
          List<?> fb = rec.get("fell_back") instanceof List<?> l ? l : List.of();
          fellBack.merge(key, fb.size(), Integer::sum);
          double spend = 0.0;
          if (rec.get("stages") instanceof List<?> stages) {
            for (Object stage : stages) {
              if (stage instanceof Map<?, ?> sm && sm.get("usd") instanceof Number n) {
                spend += n.doubleValue();
              }
            }
          }
          Object selObj = sels.get(mode);
          if (selObj instanceof Map<?, ?> sel && sel.get("usd") instanceof Number n) {
            spend += n.doubleValue();
          }
          usd.merge(key, spend, Double::sum);
        }
      }
    }
  }
}
