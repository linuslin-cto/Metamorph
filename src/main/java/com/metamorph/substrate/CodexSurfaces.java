package com.metamorph.substrate;

import com.metamorph.core.tree.SurfaceSpec;
import java.util.List;
import java.util.Set;

/** Codex editable surface (c1..c8). */
public final class CodexSurfaces {
  private CodexSurfaces() {}

  public static final SurfaceSpec CODEX_SURFACE =
      SurfaceSpec.builder("codex")
          .alwaysOn("AGENTS.md")
          .alwaysOnMax(8_000)
          .alwaysOnCapReason(
              "AGENTS.md is injected whole into EVERY turn, and codex additionally truncates it"
                  + " silently at 32,768 bytes")
          .skillDir(".agents/skills")
          .skillNameRule("overrides_dir")
          .triggerHint(
              "Fix it by adding a trigger line naming `{name}` to AGENTS.md, e.g. \"When <the"
                  + " situation this skill handles>, read .agents/skills/{name}/SKILL.md and follow"
                  + " it.\"")
          .autotriggerFile("AGENTS.md")
          .triggerLine("- When {desc}, read .agents/skills/{name}/SKILL.md and follow it.\n")
          .subagentRequiresMode(false)
          .pluginDir(null)
          .hooksFile(".codex/hooks.json")
          .hookEvents(
              Set.of(
                  "PreToolUse",
                  "PostToolUse",
                  "PermissionRequest",
                  "PreCompact",
                  "PostCompact",
                  "SessionStart",
                  "SubagentStart",
                  "SubagentStop",
                  "UserPromptSubmit",
                  "Stop"))
          .compileSkillScripts(true)
          .rejectEmptyAlwaysOn(true)
          .stubs(
              List.of(
                  new SurfaceSpec.Stub(
                      "scripts/README.md",
                      "<!-- c7: deterministic helpers, run as `python3 scripts/<name>.py`. "
                          + "Add one by emitting scripts/<name>.py. -->\n"),
                  new SurfaceSpec.Stub(
                      "sub_agents/README.md",
                      "<!-- c5: delegated codex agent roles, each spawned in its OWN context. Add"
                          + " one as sub_agents/<name>/AGENT.md with frontmatter `description:`;"
                          + " the runner installs it as a [agents.<name>] role. -->\n"),
                  new SurfaceSpec.Stub(
                      ".codex/README.md",
                      "<!-- c8: codex lifecycle hooks -- the only lever that acts INSIDE the tool"
                          + " loop, so the model cannot skip it. Add ONE file, .codex/hooks.json,"
                          + " Claude-Code hook schema: {\"hooks\": {\"PostToolUse\": [{\"matcher\":"
                          + " \"Bash\", \"hooks\": [{\"type\": \"command\", \"command\":"
                          + " \"...\"}]}]}}. A PostToolUse command that prints"
                          + " {\"decision\":\"block\",\"reason\":\"...\"} feeds the reason back to"
                          + " the model. -->\n"),
                  new SurfaceSpec.Stub(
                      ".agents/skills/README.md",
                      "<!-- c6: reusable workflows. Add one as .agents/skills/<name>/SKILL.md with"
                          + " YAML frontmatter: `description:` is REQUIRED (no frontmatter = skill"
                          + " silently dropped); `name:` is optional and must match the directory."
                          + " Only name+description are advertised to the agent; the body is read"
                          + " on demand. -->\n")))
          .build();
}
