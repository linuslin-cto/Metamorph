package com.metamorph.core.spec;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Static description of one (benchmark, substrate) cell. */
public record BenchSpec(
    String name,
    Path runsRoot,
    String installMode,
    BenchPrompts prompts,
    Map<String, ChannelSpec> channels,
    String domainWord,
    List<String> leakGlobs,
    boolean allowSubagents,
    Set<String> reservedScripts,
    String proposerDeliverables,
    boolean stateDeliverable,
    int obsTrunc) {

  public static final Set<String> INSTALL_MODES =
      Set.of("workspace_agents_md", "home_dir_jinja", "workspace_config", "codex_root");

  public BenchSpec {
    Objects.requireNonNull(name);
    Objects.requireNonNull(runsRoot);
    Objects.requireNonNull(prompts);
    Objects.requireNonNull(channels);
    domainWord = domainWord == null ? "domain" : domainWord;
    leakGlobs = leakGlobs == null ? List.of() : List.copyOf(leakGlobs);
    reservedScripts = reservedScripts == null ? Set.of() : Set.copyOf(reservedScripts);
    proposerDeliverables = proposerDeliverables == null ? "contents" : proposerDeliverables;
    if (obsTrunc <= 0) {
      obsTrunc = 50_000;
    }
    if (!INSTALL_MODES.contains(installMode)) {
      throw new IllegalArgumentException(
          "unknown install_mode '" + installMode + "'; expected one of " + INSTALL_MODES);
    }
    if (!proposerDeliverables.equals("contents") && !proposerDeliverables.equals("names")) {
      throw new IllegalArgumentException(
          "BenchSpec.proposerDeliverables must be 'contents' or 'names'");
    }
    if (!channels.containsKey("proposer") || !channels.containsKey("selector")) {
      throw new IllegalArgumentException("BenchSpec.channels must define proposer and selector");
    }
    if (obsTrunc < 10_000) {
      throw new IllegalArgumentException(
          "obs_trunc=" + obsTrunc + " is in the range that fabricates defects; use >= 10_000");
    }
  }

  public static Builder builder(String name, Path runsRoot, BenchPrompts prompts) {
    return new Builder(name, runsRoot, prompts);
  }

  public static final class Builder {
    private final String name;
    private final Path runsRoot;
    private final BenchPrompts prompts;
    private String installMode = "workspace_config";
    private Map<String, ChannelSpec> channels = Map.of();
    private String domainWord = "domain";
    private List<String> leakGlobs = List.of();
    private boolean allowSubagents;
    private Set<String> reservedScripts = Set.of();
    private String proposerDeliverables = "contents";
    private boolean stateDeliverable;
    private int obsTrunc = 50_000;

    private Builder(String name, Path runsRoot, BenchPrompts prompts) {
      this.name = name;
      this.runsRoot = runsRoot;
      this.prompts = prompts;
    }

    public Builder installMode(String v) {
      this.installMode = v;
      return this;
    }

    public Builder channels(Map<String, ChannelSpec> v) {
      this.channels = v;
      return this;
    }

    public Builder leakGlobs(String... globs) {
      this.leakGlobs = List.of(globs);
      return this;
    }

    public Builder domainWord(String v) {
      this.domainWord = v;
      return this;
    }

    public Builder proposerDeliverables(String v) {
      this.proposerDeliverables = v;
      return this;
    }

    public Builder allowSubagents(boolean v) {
      this.allowSubagents = v;
      return this;
    }

    public BenchSpec build() {
      return new BenchSpec(
          name,
          runsRoot,
          installMode,
          prompts,
          channels,
          domainWord,
          leakGlobs,
          allowSubagents,
          reservedScripts,
          proposerDeliverables,
          stateDeliverable,
          obsTrunc);
    }
  }
}
