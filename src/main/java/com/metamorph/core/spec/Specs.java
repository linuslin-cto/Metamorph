package com.metamorph.core.spec;

import com.metamorph.config.Config;
import java.util.Objects;

/** Channel and prompt resolvers. */
public final class Specs {
  private Specs() {}

  public static ChannelSpec resolveChannelModel(String kind, String model) {
    return resolveChannelModel(kind, model, "high", 120, 3600);
  }

  public static ChannelSpec resolveChannelModel(
      String kind, String model, String effort, int steps, int timeout) {
    boolean useDefault = kind == null && model == null;
    if ((kind == null) != (model == null)) {
      throw new IllegalArgumentException(
          "channel and model must be overridden TOGETHER; got kind="
              + kind
              + ", model="
              + model);
    }
    if (kind == null) {
      kind = Config.DEFAULT_PROPOSER_CHANNEL;
    }
    if (!ChannelSpec.CHANNELS.contains(kind)) {
      throw new IllegalArgumentException(
          "unknown channel '" + kind + "'; expected one of " + ChannelSpec.CHANNELS);
    }
    if (model == null) {
      model = Config.PROPOSER_MODEL_BY_CHANNEL.getOrDefault(kind, "");
    }
    if (model.isBlank()) {
      if (useDefault) {
        return new ChannelSpec(kind, "", effort, steps, timeout);
      }
      throw new IllegalArgumentException("model override must not be empty");
    }
    if ("opencode".equals(kind) && !model.contains("/")) {
      throw new IllegalArgumentException(
          "opencode channel needs a provider-prefixed model id, got '" + model + "'");
    }
    if ("claude_cli".equals(kind) && model.contains("/")) {
      throw new IllegalArgumentException(
          "claude_cli channel takes a bare Anthropic id, got '" + model + "'");
    }
    return new ChannelSpec(kind, model, effort, steps, timeout);
  }

  public static BenchPrompts resolvePrompts(BenchmarkAdapter adapter, String taskId) {
    BenchPrompts prompts = adapter.promptsFor(taskId);
    return Objects.requireNonNull(prompts, "prompts_for returned null");
  }
}
