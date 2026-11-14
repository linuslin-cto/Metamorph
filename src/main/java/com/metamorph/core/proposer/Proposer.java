package com.metamorph.core.proposer;

import com.metamorph.config.Config;
import com.metamorph.core.spec.ChannelSpec;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs agentic sessions on one channel. Subclasses implement transport. */
public abstract class Proposer {
  protected final ChannelSpec spec;
  protected final List<Reply> calls = new ArrayList<>();

  protected Proposer(ChannelSpec spec) {
    if (spec.model().isBlank()) {
      throw new IllegalArgumentException(
          "Set METAMORPH_IMPROVER_MODEL before starting model sessions");
    }
    this.spec = spec;
  }

  public ChannelSpec spec() {
    return spec;
  }

  public List<Reply> calls() {
    return Collections.unmodifiableList(calls);
  }

  /** Scrubbed subprocess environment for model sessions (channel-specific Vertex keys). */
  public Map<String, String> env() {
    Map<String, String> e = new LinkedHashMap<>(Config.scrubEnv());
    String project = Config.env("GOOGLE_CLOUD_PROJECT", Config.env("METAMORPH_VERTEX_PROJECT", ""));
    String region = Config.env("CLOUD_ML_REGION", Config.env("METAMORPH_VERTEX_REGION", "us-central1"));
    if ("claude_cli".equals(spec.kind())) {
      e.put("CLAUDE_CODE_USE_VERTEX", "1");
      e.put("ANTHROPIC_VERTEX_PROJECT_ID", project);
      e.put("CLOUD_ML_REGION", region);
      e.put("ANTHROPIC_MODEL", spec.model());
    } else {
      e.put("GOOGLE_VERTEX_PROJECT", project);
      e.put("GOOGLE_VERTEX_LOCATION", region);
      e.put("VERTEXAI_LOCATION", region);
    }
    return e;
  }

  public abstract Reply call(
      String mission, Path contextDir, String label, String harvest, int maxAttempts);

  public Reply call(String mission, Path contextDir, String label) {
    return call(mission, contextDir, label, "context/harness", 2);
  }
}
