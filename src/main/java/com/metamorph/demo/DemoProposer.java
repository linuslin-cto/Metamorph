package com.metamorph.demo;

import com.metamorph.core.proposer.Proposer;
import com.metamorph.core.proposer.Reply;
import com.metamorph.core.spec.ChannelSpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/** Scripted edits and selection; no model service is used. */
public class DemoProposer extends Proposer {
  public DemoProposer(ChannelSpec spec) {
    super(spec);
  }

  @Override
  public Reply call(String mission, Path contextDir, String label, String harvest, int maxAttempts) {
    try {
      Path context = contextDir;
      Path workspace = null;
      String text;
      if (label.startsWith("select.")) {
        BigDecimal expected = sumLedger(context.resolve("source/ledger.csv"));
        String totalStr = String.format(Locale.US, "%.2f", expected);
        String chosen;
        try (Stream<Path> stream = Files.list(context.resolve("candidates"))) {
          chosen =
              stream
                  .filter(p -> p.getFileName().toString().endsWith(".md"))
                  .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                  .filter(
                      path -> {
                        try {
                          return Files.readString(path).contains("Total: " + totalStr);
                        } catch (Exception e) {
                          return false;
                        }
                      })
                  .map(p -> p.getFileName().toString().replaceFirst("\\.md$", ""))
                  .findFirst()
                  .orElseThrow(
                      () -> new IllegalStateException("no matching candidate for Total: " + totalStr));
        }
        text =
            "<why>The signed total matches the source ledger.</why>\n```choice\n"
                + chosen
                + "\n```";
      } else if (label.startsWith("experience.")) {
        String cur = Files.readString(context.resolve("harness/scripts/summarize.py"));
        String base = Files.readString(context.resolve("base_harness/scripts/summarize.py"));
        boolean changed = !cur.equals(base);
        text =
            "<edits>\ncomponent: scripts/summarize.py\ncandidates: inv1\n"
                + "change: dropped the positive-only filter from the amount sum\n"
                + "observed: the probe total included the credits the base run omitted\n"
                + "verdict: "
                + (changed ? "helped" : "no observed effect")
                + "\n</edits>\n\n"
                + (changed
                    ? "<experience>\ncue: When a ledger includes credits and debits\n"
                        + "lesson: Include both signs when summing in scripts/summarize.py.\n"
                        + "evidence: The scripts/summarize.py row -- removing the positive-only"
                        + " filter changed the probe total to include the credits omitted by the"
                        + " base trajectory.\n"
                        + "confidence: high for this observed arithmetic contrast\n</experience>"
                    : "NO_ENTRY");
      } else {
        workspace = context.resolve("harness");
        Path script = workspace.resolve("scripts/summarize.py");
        String body = Files.readString(script, StandardCharsets.UTF_8);
        Files.writeString(script, body.replace(" if amount > 0", ""), StandardCharsets.UTF_8);
        text =
            "<notes>Keep signed amounts: the base total omitted credits in the source"
                + " ledger.</notes>";
      }
      Reply reply = new Reply(label, text, true, 1, workspace);
      calls.add(reply);
      return reply;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static BigDecimal sumLedger(Path csv) throws Exception {
    BigDecimal total = BigDecimal.ZERO;
    boolean header = true;
    for (String line : Files.readAllLines(csv)) {
      if (header) {
        header = false;
        continue;
      }
      if (line.isBlank()) {
        continue;
      }
      total = total.add(new BigDecimal(line.strip()));
    }
    return total;
  }
}
