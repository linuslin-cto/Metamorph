package com.metamorph.cli;

import com.metamorph.core.eval.EvalScorecard;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "eval",
    mixinStandardHelpOptions = true,
    description = "Run offline self-evolution scorecard (Demo Propose–Probe–Compose → Judge→Report)")
public final class EvalCommand implements Callable<Integer> {
  @Option(names = "--output", defaultValue = "runs/eval", description = "working directory for the demo sweep")
  Path output;

  @Option(names = "--k", defaultValue = "1", description = "budget multiplier")
  int k;

  @Option(names = "--report", description = "write Markdown scorecard here (default: <output>/EVAL.md)")
  Path report;

  @Option(names = "--json", description = "optional JSON scorecard path")
  Path json;

  @Override
  public Integer call() throws Exception {
    EvalScorecard.Result result = EvalScorecard.runOfflineDemo(output.toAbsolutePath().normalize(), k);
    Path md = report != null ? report : output.resolve("EVAL.md");
    EvalScorecard.writeMarkdown(result, md);
    if (json != null) {
      EvalScorecard.writeJson(result, json);
    } else {
      EvalScorecard.writeJson(result, output.resolve("eval-scorecard.json"));
    }
    System.out.println(EvalScorecard.renderMarkdown(result));
    System.out.println("wrote " + md.toAbsolutePath());
    return result.ok() ? 0 : 1;
  }
}
