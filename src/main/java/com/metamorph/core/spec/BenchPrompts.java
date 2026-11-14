package com.metamorph.core.spec;

import java.util.Map;
import java.util.Objects;

/** The benchmark-shaped text the shared prompts interpolate. */
public record BenchPrompts(
    String work,
    String domainWord,
    String invLevers,
    String domLevers,
    String yardstick,
    String deliverableWord,
    String domainGuidance,
    String selectionChecks,
    String selectionLead,
    String selectionConformance,
    String promptPack) {

  public BenchPrompts {
    Objects.requireNonNull(work);
    domainWord = domainWord == null ? "domain" : domainWord;
    invLevers = invLevers == null ? "" : invLevers;
    domLevers = domLevers == null ? "" : domLevers;
    yardstick = yardstick == null ? "" : yardstick;
    deliverableWord = deliverableWord == null ? "deliverable" : deliverableWord;
    domainGuidance = domainGuidance == null ? "" : domainGuidance;
    selectionChecks = selectionChecks == null ? "" : selectionChecks;
    selectionLead = selectionLead == null ? "" : selectionLead;
    selectionConformance = selectionConformance == null ? "full" : selectionConformance;
    promptPack = promptPack == null ? "adapt" : promptPack;
    if (!promptPack.equals("adapt") && !promptPack.equals("tts")) {
      throw new IllegalArgumentException(
          "BenchPrompts.promptPack must be 'adapt' or 'tts', got '" + promptPack + "'");
    }
    Map<String, String> visible =
        Map.of(
            "work", work,
            "inv_levers", invLevers,
            "dom_levers", domLevers,
            "yardstick", yardstick,
            "domain_guidance", domainGuidance,
            "selection_checks", selectionChecks);
    for (var e : visible.entrySet()) {
      String low = e.getValue().toLowerCase();
      for (String bad : new String[] {"benchmark", "evaluation", "grader", "rubric", "score"}) {
        if (low.contains(bad)) {
          throw new IllegalArgumentException(
              "BenchPrompts."
                  + e.getKey()
                  + " must describe the WORK, not its measurement (found '"
                  + bad
                  + "'). Say what the practitioner needs.");
        }
      }
    }
  }

  public BenchPrompts(String work) {
    this(work, "domain", "", "", "", "deliverable", "", "", "", "full", "adapt");
  }
}
