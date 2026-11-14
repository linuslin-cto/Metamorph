package com.metamorph.cli;

import com.metamorph.core.judge.Judge;
import com.metamorph.core.judge.Scored;
import com.metamorph.core.spec.BenchPrompts;
import com.metamorph.core.spec.BenchSpec;
import com.metamorph.core.spec.BenchmarkAdapter;
import com.metamorph.core.spec.ChannelSpec;
import com.metamorph.core.spec.SubstrateLike;
import com.metamorph.substrate.OpencodeSubstrate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Judge selected runs under a namespace directory into {@code judged.json}.
 *
 * <p>Offline default uses {@code scores.json} cache scoring (LAB-style baseline path).
 */
@Command(name = "judge", description = "Judge selected runs under a namespace into judged.json")
public final class JudgeCommand implements Runnable {
  @Option(
      names = "--ns-dir",
      required = true,
      description = "Namespace directory containing */summary.json")
  Path nsDir;

  @Option(names = "--modes", defaultValue = "agg,seq", description = "Comma list of modes")
  String modes;

  @Option(names = "--parallel", defaultValue = "8")
  int parallel;

  @Override
  public void run() {
    try {
      if (!Files.isDirectory(nsDir)) {
        throw new IllegalStateException("nothing to judge: " + nsDir + " does not exist");
      }
      List<String> modeList =
          Arrays.stream(modes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
      List<Map<String, Object>> sels = Judge.collectSelections(nsDir, modeList);
      if (sels.isEmpty()) {
        throw new IllegalStateException(
            "nothing to judge: no selections for modes " + modeList + " under " + nsDir);
      }
      System.out.println("judging " + sels.size() + " selection(s) under " + nsDir.getFileName() + " ...");
      BenchmarkAdapter adapter = new CacheScoreAdapter(nsDir.getParent() == null ? nsDir : nsDir.getParent());
      List<Scored> rows = Judge.judgeSelections(adapter, sels, parallel);
      Path out = nsDir.resolve("judged.json");
      List<Scored> merged = Judge.merge(Judge.loadRows(out), rows);
      Judge.write(merged, out);
      long graded = merged.stream().filter(Scored::graded).count();
      System.out.println(
          "judged.json: "
              + merged.size()
              + " row(s), "
              + graded
              + " graded, "
              + (merged.size() - graded)
              + " ungraded (judge failure or no pick — NOT zeros)");
      System.out.println("wrote " + out);
      if (rows.isEmpty() || rows.stream().anyMatch(r -> !r.graded())) {
        System.exit(2);
      }
    } catch (Exception e) {
      System.err.println(e.getMessage() == null ? e.toString() : e.getMessage());
      System.exit(1);
    }
  }

  /** Offline adapter that grades from cached {@code scores.json} beside the run. */
  static final class CacheScoreAdapter implements BenchmarkAdapter {
    private final BenchSpec spec;
    private final OpencodeSubstrate substrate = new OpencodeSubstrate();

    CacheScoreAdapter(Path runsRoot) {
      ChannelSpec ch = new ChannelSpec("opencode", "cache/scores");
      this.spec =
          BenchSpec.builder("cache", runsRoot, new BenchPrompts("cached scores"))
              .installMode("workspace_config")
              .channels(Map.of("proposer", ch, "selector", ch))
              .build();
    }

    @Override
    public BenchSpec spec() {
      return spec;
    }

    @Override
    public SubstrateLike substrate() {
      return substrate;
    }

    @Override
    public List<String> tasks(String split) {
      return List.of();
    }

    @Override
    public String domainOf(String taskId) {
      return "";
    }

    @Override
    public String taskPrompt(String taskId) {
      return "";
    }

    @Override
    public Path taskFiles(String taskId) {
      return null;
    }

    @Override
    public Path execute(String taskId, Path harnessDir, String runId, Integer timeout) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean delivered(Path runDir) {
      return true;
    }

    @Override
    public String trajectory(Path runDir, int obsTrunc) {
      return "";
    }

    @Override
    public Map<String, String> deliverableFiles(Path runDir) {
      return Map.of();
    }

    @Override
    public String deliverableText(Path runDir) {
      return "";
    }

    @Override
    public Path baseHarness() {
      return Path.of(".");
    }

    @Override
    public void validateTree(Map<String, String> tree) {}

    @Override
    public Map<String, Object> score(Path runDir, String taskId) {
      try {
        return Judge.scoreFromCache(runDir);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
