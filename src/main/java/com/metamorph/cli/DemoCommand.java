package com.metamorph.cli;

import com.metamorph.core.judge.Judge;
import com.metamorph.core.judge.Scored;
import com.metamorph.core.report.Report;
import com.metamorph.core.schedule.Schedule;
import com.metamorph.demo.DemoAdapter;
import com.metamorph.demo.DemoProposer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "demo", mixinStandardHelpOptions = true, description = "Offline Propose–Probe–Compose demo")
public final class DemoCommand implements Runnable {
  @Option(names = "--output", required = true)
  Path output;

  @Option(names = "--k", defaultValue = "2")
  int k;

  @Option(
      names = "--judge",
      defaultValue = "true",
      description = "After the sweep, judge selections and print a report")
  boolean judge;

  @Override
  public void run() {
    try {
      DemoAdapter adapter = new DemoAdapter(output);
      DemoProposer prop = new DemoProposer(adapter.spec().channels().get("proposer"));
      Map<String, Object> result =
          Schedule.runDomain(
              "accounting",
              adapter.tasks(),
              k,
              adapter,
              prop,
              adapter.spec().runsRoot(),
              output.resolve("library").resolve(String.valueOf(k)));
      System.out.println(
          "done=" + result.get("done") + " failed=" + result.get("failed") + " k=" + k);
      if (judge) {
        judgeAndReport(adapter, k);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void judgeAndReport(DemoAdapter adapter, int k) throws Exception {
    Path nsDir = adapter.spec().runsRoot().resolve("demo-oc-agg" + k);
    List<String> modes = List.of("agg", "seq");
    List<Map<String, Object>> sels = Judge.collectSelections(nsDir, modes);
    if (sels.isEmpty()) {
      System.out.println("nothing to judge under " + nsDir);
      return;
    }
    List<Scored> rows = Judge.judgeSelections(adapter, sels, 4);
    Path judged = nsDir.resolve("judged.json");
    List<Scored> merged = Judge.merge(Judge.loadRows(judged), rows);
    Judge.write(merged, judged);
    long graded = merged.stream().filter(Scored::graded).count();
    System.out.println(
        "judged.json: "
            + merged.size()
            + " row(s), "
            + graded
            + " graded, "
            + (merged.size() - graded)
            + " ungraded");
    Map<String, Integer> fellBack = new java.util.LinkedHashMap<>();
    Map<String, Double> usd = new java.util.LinkedHashMap<>();
    ReportCommand.spendAndFallbacks(nsDir, modes, fellBack, usd);
    String title = "demo-oc k=" + k + " modes=parallel,sequential";
    System.out.println(Report.render(Report.summarise(merged, fellBack, usd), title, "demo"));
  }

  public static void main(String[] args) {
    int code = new CommandLine(new DemoCommand()).execute(args);
    System.exit(code);
  }
}
