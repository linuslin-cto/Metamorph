package com.metamorph.core.tree;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** The editable surface of one agent framework: paths, caps and per-surface rules. */
public record SurfaceSpec(
    String name,
    List<String> alwaysOn,
    int alwaysOnMax,
    String alwaysOnCapReason,
    String skillDir,
    String skillNameRule,
    String triggerHint,
    String autotriggerFile,
    String triggerLine,
    boolean subagentRequiresMode,
    String pluginDir,
    String hooksFile,
    Set<String> hookEvents,
    boolean compileSkillScripts,
    boolean rejectEmptyAlwaysOn,
    List<Stub> stubs) {

  public record Stub(String rel, String body) {}

  public SurfaceSpec {
    Objects.requireNonNull(name);
    alwaysOn = List.copyOf(alwaysOn);
    hookEvents = hookEvents == null ? Set.of() : Set.copyOf(hookEvents);
    stubs = stubs == null ? List.of() : List.copyOf(stubs);
  }

  public static Builder builder(String name) {
    return new Builder(name);
  }

  public static final class Builder {
    private final String name;
    private List<String> alwaysOn = List.of();
    private int alwaysOnMax = 24_000;
    private String alwaysOnCapReason = "";
    private String skillDir = "skills";
    private String skillNameRule = "must_match";
    private String triggerHint = "";
    private String autotriggerFile = "guardrails.md";
    private String triggerLine = "- When {desc}, load the `{name}` skill.\n";
    private boolean subagentRequiresMode = true;
    private String pluginDir;
    private String hooksFile;
    private Set<String> hookEvents = Set.of();
    private boolean compileSkillScripts;
    private boolean rejectEmptyAlwaysOn;
    private List<Stub> stubs = List.of();

    private Builder(String name) {
      this.name = name;
    }

    public Builder alwaysOn(String... paths) {
      this.alwaysOn = List.of(paths);
      return this;
    }

    public Builder alwaysOnMax(int v) {
      this.alwaysOnMax = v;
      return this;
    }

    public Builder alwaysOnCapReason(String v) {
      this.alwaysOnCapReason = v;
      return this;
    }

    public Builder skillDir(String v) {
      this.skillDir = v;
      return this;
    }

    public Builder skillNameRule(String v) {
      this.skillNameRule = v;
      return this;
    }

    public Builder triggerHint(String v) {
      this.triggerHint = v;
      return this;
    }

    public Builder autotriggerFile(String v) {
      this.autotriggerFile = v;
      return this;
    }

    public Builder triggerLine(String v) {
      this.triggerLine = v;
      return this;
    }

    public Builder subagentRequiresMode(boolean v) {
      this.subagentRequiresMode = v;
      return this;
    }

    public Builder pluginDir(String v) {
      this.pluginDir = v;
      return this;
    }

    public Builder hooksFile(String v) {
      this.hooksFile = v;
      return this;
    }

    public Builder hookEvents(Set<String> v) {
      this.hookEvents = v;
      return this;
    }

    public Builder compileSkillScripts(boolean v) {
      this.compileSkillScripts = v;
      return this;
    }

    public Builder rejectEmptyAlwaysOn(boolean v) {
      this.rejectEmptyAlwaysOn = v;
      return this;
    }

    public Builder stubs(List<Stub> v) {
      this.stubs = v;
      return this;
    }

    public SurfaceSpec build() {
      return new SurfaceSpec(
          name,
          alwaysOn,
          alwaysOnMax,
          alwaysOnCapReason,
          skillDir,
          skillNameRule,
          triggerHint,
          autotriggerFile,
          triggerLine,
          subagentRequiresMode,
          pluginDir,
          hooksFile,
          hookEvents,
          compileSkillScripts,
          rejectEmptyAlwaysOn,
          stubs);
    }
  }
}
