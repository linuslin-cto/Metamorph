package com.metamorph.core.spec;

import java.util.Objects;
import java.util.Set;

/** How to reach one model, on one proposer/selector channel. */
public record ChannelSpec(String kind, String model, String effort, int steps, int timeout) {
  public static final Set<String> CHANNELS = Set.of("claude_cli", "opencode");

  public ChannelSpec {
    Objects.requireNonNull(kind);
    Objects.requireNonNull(model);
    effort = effort == null ? "high" : effort;
    if (steps <= 0) {
      steps = 120;
    }
    if (timeout <= 0) {
      timeout = 3600;
    }
    if (!CHANNELS.contains(kind)) {
      throw new IllegalArgumentException(
          "unknown channel '" + kind + "'; expected one of " + CHANNELS);
    }
  }

  public ChannelSpec(String kind, String model) {
    this(kind, model, "high", 120, 3600);
  }
}
