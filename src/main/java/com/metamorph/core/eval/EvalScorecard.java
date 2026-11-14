package com.metamorph.core.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metamorph.core.judge.Judge;
import com.metamorph.core.judge.Scored;
import com.metamorph.core.report.Report;
import com.metamorph.core.schedule.Schedule;
import com.metamorph.demo.DemoAdapter;
import com.metamorph.demo.DemoProposer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline evaluation scorecard aligned with the paper's figure pipeline: budgets, dual modes,
 * base→evolved lift, library retention, judge/report scores.
 */
public final class EvalScorecard {
  private static final ObjectMapper JSON = new ObjectMapper();

  private EvalScorecard() {}

  public record Dimension(String id, String title, boolean pass, double score, String detail) {}

  public record Result(
      boolean ok, double overall, List<Dimension> dimensions, List<Report.Summary> summaries) {}

  /** Run Demo Propose–Probe–Compose at {@code k} and score the self-evolution contract. */
  public static Result runOfflineDemo(Path root, int k) throws Exception {
    final int kk = k < 1 ? 1 : k;
    List<Dimension> dims = new ArrayList<>();
    DemoAdapter adapter = new DemoAdapter(root);
    DemoProposer proposer = new DemoProposer(adapter.spec().channels().get("proposer"));

    // D1: base harness fails the criterion (room to evolve).
    Path baseRun = adapter.execute("ledger-1", adapter.baseHarness(), "eval/base");
    Map<String, Object> baseScore = adapter.score(baseRun, "ledger-1");
    int basePassed = ((Number) baseScore.get("n_passed")).intValue();
    boolean d1 = basePassed == 0;
    dims.add(
        new Dimension(
            "base_fails",
            "基座 harness 不及格（存在进化空间）",
            d1,
            d1 ? 100.0 : 0.0,
            "n_passed=" + basePassed));

    Path libraryRoot = root.resolve("library").resolve(String.valueOf(kk));
    Map<String, Object> sweep =
        Schedule.runDomain(
            "accounting",
            adapter.tasks(),
            kk,
            adapter,
            proposer,
            adapter.spec().runsRoot(),
            libraryRoot);
    int done = ((Number) sweep.get("done")).intValue();
    int failed = ((Number) sweep.get("failed")).intValue();
    boolean d2 = done == adapter.tasks().size() && failed == 0;
    dims.add(
        new Dimension(
            "sweep_complete",
            "域 sweep 全部完成",
            d2,
            d2 ? 100.0 : 0.0,
            "done=" + done + " failed=" + failed));

    Path nsDir = adapter.spec().runsRoot().resolve("demo-oc-agg" + kk);
    @SuppressWarnings("unchecked")
    Map<String, Object> summary =
        JSON.readValue(Files.readString(nsDir.resolve("ledger-1").resolve("summary.json")), Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> modes = (Map<String, Object>) summary.get("modes");

    boolean budgetsOk = true;
    StringBuilder budgetDetail = new StringBuilder();
    for (String mode : List.of("agg", "seq")) {
      @SuppressWarnings("unchecked")
      Map<String, Object> ledger =
          (Map<String, Object>) ((Map<String, Object>) modes.get(mode)).get("ledger");
      int R = ((Number) ledger.get("R")).intValue();
      int P = ((Number) ledger.get("P")).intValue();
      boolean ok = R == 4 * kk && P == 3 * kk;
      budgetsOk &= ok;
      budgetDetail.append(mode).append(":R=").append(R).append(",P=").append(P).append(' ');
    }
    dims.add(
        new Dimension(
            "budget_identity",
            "预算身份 R=4k / P=3k",
            budgetsOk,
            budgetsOk ? 100.0 : 0.0,
            budgetDetail.toString().strip()));

    boolean evolvedOk = true;
    StringBuilder evolvedDetail = new StringBuilder();
    for (String mode : List.of("agg", "seq")) {
      @SuppressWarnings("unchecked")
      Map<String, Object> sel =
          (Map<String, Object>) ((Map<String, Object>) summary.get("selections")).get(mode);
      Path chosen = Path.of(String.valueOf(sel.get("chosen_run_dir")));
      Map<String, Object> evolved = adapter.score(chosen, "ledger-1");
      int n = ((Number) evolved.get("n_passed")).intValue();
      boolean pass = n >= 1 && Boolean.TRUE.equals(evolved.get("all_pass"));
      evolvedOk &= pass;
      evolvedDetail.append(mode).append(":n_passed=").append(n).append(' ');
    }
    dims.add(
        new Dimension(
            "evolved_passes",
            "进化后 selection 通过准则",
            evolvedOk,
            evolvedOk ? 100.0 : 0.0,
            evolvedDetail.toString().strip()));

    boolean lift = evolvedOk && d1;
    dims.add(
        new Dimension(
            "self_evolution_lift",
            "自进化增益（基座失败 → 进化成功）",
            lift,
            lift ? 100.0 : 0.0,
            lift ? "base 0 → evolved pass" : "no lift"));

    long libFiles = 0;
    if (Files.isDirectory(libraryRoot)) {
      try (var walk = Files.walk(libraryRoot)) {
        libFiles = walk.filter(Files::isRegularFile).count();
      }
    }
    boolean libOk = libFiles > 0;
    dims.add(
        new Dimension(
            "library_retention",
            "Library 保留跨任务材料",
            libOk,
            libOk ? 100.0 : 0.0,
            "files=" + libFiles));

    List<Map<String, Object>> sels = Judge.collectSelections(nsDir, List.of("agg", "seq"));
    List<Scored> graded = Judge.judgeSelections(adapter, sels, 4);
    Path judged = nsDir.resolve("judged.json");
    Judge.write(graded, judged);
    List<Report.Summary> summaries = Report.summarise(graded, null, null);
    double meanScore =
        summaries.stream()
            .filter(s -> s.score != null && s.n > 0)
            .mapToDouble(s -> s.score)
            .average()
            .orElse(0.0);
    boolean reportOk = !summaries.isEmpty() && meanScore >= 99.0;
    dims.add(
        new Dimension(
            "judge_report",
            "Judge→Report 均分 ≥ 99%",
            reportOk,
            Math.min(100.0, meanScore),
            String.format("mean=%.2f%% modes=%d", meanScore, summaries.size())));

    boolean dualModes =
        summaries.stream().anyMatch(s -> "agg".equals(s.mode))
            && summaries.stream().anyMatch(s -> "seq".equals(s.mode));
    dims.add(
        new Dimension(
            "dual_modes",
            "agg 与 seq 双模式均有 graded 行",
            dualModes,
            dualModes ? 100.0 : 0.0,
            "summaries=" + summaries.size()));

    // k=1: agg/seq are the same procedure — gap is a noise floor, not an effect.
    // k>=2: both modes must still clear the self-evolution bar (budgets already scaled above).
    Double aggScore =
        summaries.stream()
            .filter(s -> "agg".equals(s.mode) && s.k == kk && s.score != null)
            .map(s -> s.score)
            .findFirst()
            .orElse(null);
    Double seqScore =
        summaries.stream()
            .filter(s -> "seq".equals(s.mode) && s.k == kk && s.score != null)
            .map(s -> s.score)
            .findFirst()
            .orElse(null);
    if (kk == 1 && aggScore != null && seqScore != null) {
      double gap = Math.abs(aggScore - seqScore);
      boolean floorOk = gap <= 1.0; // deterministic demo → expect ~0
      dims.add(
          new Dimension(
              "noise_floor_k1",
              "k=1 时 agg/seq 分差为噪声地板（≤1pt）",
              floorOk,
              floorOk ? 100.0 : Math.max(0.0, 100.0 - gap),
              String.format("gap=%.2f (agg=%.2f seq=%.2f)", gap, aggScore, seqScore)));
    } else if (kk >= 2 && aggScore != null && seqScore != null) {
      boolean bothHigh = aggScore >= 99.0 && seqScore >= 99.0;
      dims.add(
          new Dimension(
              "modes_clear_at_k",
              "k≥2 时双模式均需越过噪声地板（≥99%）",
              bothHigh,
              bothHigh ? 100.0 : (aggScore + seqScore) / 2.0,
              String.format("agg=%.2f seq=%.2f R=%d P=%d", aggScore, seqScore, 4 * kk, 3 * kk)));
    }

    double overall =
        dims.stream().mapToDouble(Dimension::score).average().orElse(0.0);
    boolean ok = dims.stream().allMatch(Dimension::pass);
    return new Result(ok, Math.round(overall * 100.0) / 100.0, List.copyOf(dims), summaries);
  }

  public static String renderMarkdown(Result result) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Metamorph 自进化评测打分\n\n");
    sb.append("| 项 | 值 |\n|---|---|\n");
    sb.append("| 总评 | ").append(result.ok() ? "**PASS**" : "**FAIL**").append(" |\n");
    sb.append("| 综合分 | **").append(result.overall()).append("** / 100 |\n");
    sb.append("| 维度数 | ").append(result.dimensions().size()).append(" |\n\n");
    sb.append("## 维度明细\n\n");
    sb.append("| ID | 维度 | 通过 | 得分 | 细节 |\n");
    sb.append("|----|------|------|------|------|\n");
    for (Dimension d : result.dimensions()) {
      sb.append("| `")
          .append(d.id())
          .append("` | ")
          .append(d.title())
          .append(" | ")
          .append(d.pass() ? "✅" : "❌")
          .append(" | ")
          .append(String.format("%.1f", d.score()))
          .append(" | ")
          .append(d.detail().replace("|", "/"))
          .append(" |\n");
    }
    sb.append("\n## Report 汇总（mode×k）\n\n```\n");
    sb.append(Report.render(result.summaries(), "demo-oc eval", "demo"));
    sb.append("\n```\n\n");
    sb.append("## 口径说明\n\n");
    sb.append("- 评测流水线：Propose–Probe–Compose → Selection → Judge → Report。\n");
    sb.append("- 预算身份：R=4k、P=3k；mode 含 parallel（agg）与 sequential（seq）。\n");
    sb.append("- 自进化判据：基座不及格，且进化后 selection 通过同一准则。\n");
    sb.append("- k=1 时 agg/seq 同分差视为噪声地板；k≥2 的效应须越过该地板。\n");
    sb.append("- 本报告由离线 Demo 确定性跑出，无需 API Key。\n");
    sb.append("\n## 重新生成\n\n");
    sb.append("```bash\n");
    sb.append("mvn -q exec:java -Dexec.mainClass=com.metamorph.cli.MetamorphMain \\\n");
    sb.append("  -Dexec.args=\"eval --output runs/eval --k 1 --report docs/EVAL.md --json docs/eval-scorecard.json\"\n");
    sb.append("```\n");
    return sb.toString();
  }

  public static Path writeMarkdown(Result result, Path out) throws Exception {
    Files.createDirectories(out.getParent() == null ? Path.of(".") : out.getParent());
    Files.writeString(out, renderMarkdown(result), StandardCharsets.UTF_8);
    return out;
  }

  public static Path writeJson(Result result, Path out) throws Exception {
    Files.createDirectories(out.getParent() == null ? Path.of(".") : out.getParent());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("ok", result.ok());
    payload.put("overall", result.overall());
    List<Map<String, Object>> dims = new ArrayList<>();
    for (Dimension d : result.dimensions()) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", d.id());
      row.put("title", d.title());
      row.put("pass", d.pass());
      row.put("score", d.score());
      row.put("detail", d.detail());
      dims.add(row);
    }
    payload.put("dimensions", dims);
    Files.writeString(
        out, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(payload) + "\n", StandardCharsets.UTF_8);
    return out;
  }
}
