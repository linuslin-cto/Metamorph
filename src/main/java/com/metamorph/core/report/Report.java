package com.metamorph.core.report;

import com.metamorph.core.judge.Scored;
import com.metamorph.core.schedule.Schedule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Judged selections → printable figure per (mode, k). Ungraded rows are excluded from the mean. */
public final class Report {
  private Report() {}

  public static final class Summary {
    public final String mode;
    public final int k;
    public int n;
    public int nUngraded;
    public Double score;
    public Double allPass;
    public int keptBase;
    public int choseFinal;
    public int choseCandidate;
    public int fellBack;
    public double usd;

    public Summary(String mode, int k) {
      this.mode = mode;
      this.k = k;
    }

    public String name() {
      return Schedule.MODE_NAMES.getOrDefault(mode, mode) + " k=" + k;
    }

    public String line(String bench) {
      if (n == 0) {
        return "  " + name() + ": no graded task";
      }
      String figures =
          "lab".equals(bench)
              ? String.format("partial %6.2f%% | pass %5.1f%%", score, allPass)
              : String.format("score %6.2f%%", score);
      String pick =
          String.format(
              " | picked output: final %d / candidate %d / base %d",
              choseFinal, choseCandidate, keptBase);
      String fb = fellBack > 0 ? " | fell back " + fellBack : "";
      String ug = nUngraded > 0 ? " | ungraded " + nUngraded : "";
      return String.format("  %s: n=%3d | %s%s%s%s", name(), n, figures, pick, fb, ug);
    }
  }

  static String kind(Scored r) {
    String lbl = r.label == null ? "" : r.label;
    if (lbl.startsWith("base")) {
      return "base";
    }
    if ("agg".equals(r.mode)) {
      return lbl.startsWith("agg") ? "final" : "candidate";
    }
    if ("seq".equals(r.mode)) {
      for (int i = 1; i <= r.k; i++) {
        if (("c" + i).equals(lbl)) {
          return "final";
        }
      }
    }
    return "candidate";
  }

  public static List<Summary> summarise(
      List<Scored> rows, Map<String, Integer> fellBack, Map<String, Double> usd) {
    Map<String, List<Scored>> byKey = new LinkedHashMap<>();
    for (Scored r : rows) {
      byKey.computeIfAbsent(r.mode + "|" + r.k, k -> new ArrayList<>()).add(r);
    }
    List<Summary> out = new ArrayList<>();
    List<String> keys = byKey.keySet().stream().sorted().toList();
    for (String key : keys) {
      List<Scored> group = byKey.get(key);
      Scored sample = group.get(0);
      Summary s = new Summary(sample.mode, sample.k);
      List<Scored> good = group.stream().filter(Scored::graded).toList();
      s.n = good.size();
      s.nUngraded = group.size() - good.size();
      s.keptBase = (int) good.stream().filter(r -> r.chosenIsBase).count();
      s.choseFinal = (int) good.stream().filter(r -> "final".equals(kind(r))).count();
      s.choseCandidate = (int) good.stream().filter(r -> "candidate".equals(kind(r))).count();
      String mk = sample.mode + "|" + sample.k;
      s.fellBack = fellBack == null ? 0 : fellBack.getOrDefault(mk, 0);
      s.usd = usd == null ? 0.0 : Math.round(usd.getOrDefault(mk, 0.0) * 100.0) / 100.0;
      if (!good.isEmpty()) {
        double mean =
            good.stream().mapToDouble(r -> r.frac() == null ? 0.0 : r.frac()).average().orElse(0);
        // Python: round(100 * mean, 2) / round(100 * pass_rate, 2)
        s.score = Math.round(100.0 * mean * 100.0) / 100.0;
        long passes = good.stream().filter(r -> r.allPass).count();
        s.allPass = Math.round(100.0 * passes / good.size() * 100.0) / 100.0;
      }
      out.add(s);
    }
    return out;
  }

  public static String render(List<Summary> summaries, String title, String bench) {
    List<String> lines = new ArrayList<>();
    if (title != null && !title.isBlank()) {
      lines.add(title);
      lines.add("=".repeat(Math.max(40, title.length())));
    }
    summaries.stream()
        .sorted(Comparator.comparingInt((Summary s) -> s.k).thenComparing(s -> s.mode))
        .forEach(s -> lines.add(s.line(bench == null ? "" : bench)));
    Map<String, Summary> at1 =
        summaries.stream()
            .filter(s -> s.k == 1 && s.score != null)
            .collect(Collectors.toMap(s -> s.mode, s -> s, (a, b) -> a));
    if (at1.size() == 2 && at1.containsKey("agg") && at1.containsKey("seq")) {
      double gap = Math.abs(at1.get("agg").score - at1.get("seq").score);
      lines.add("");
      lines.add(
          "  NOTE  at k=1 the parallel and sequential modes are the same procedure "
              + "(one INVARIANT draw, one DOMAIN draw, one composition).");
      lines.add(
          String.format(
              "        The %.2f pt between them is therefore a NOISE FLOOR, not a result -- an"
                  + " effect at k>=2 has to clear it.",
              gap));
    }
    return String.join("\n", lines);
  }
}
