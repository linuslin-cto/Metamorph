package com.metamorph.substrate;

import com.metamorph.core.tree.SurfaceSpec;
import java.util.List;

/** OpenCode editable surface (c1..c8). */
public final class OpencodeSurfaces {
  private OpencodeSurfaces() {}

  public static final SurfaceSpec OPENCODE_SURFACE =
      SurfaceSpec.builder("opencode")
          .alwaysOn(
              "systemprompt.md",
              "guardrails.md",
              "LongTermMEMORY.md",
              "tool_descriptions/tool_guidance.md")
          .alwaysOnMax(24_000)
          .alwaysOnCapReason("every token here competes with the agent's own output budget")
          .skillDir("skills")
          .skillNameRule("must_match")
          .triggerHint(
              "Fix it by adding a trigger line naming `{name}` to systemprompt.md or"
                  + " guardrails.md, e.g. \"When <the situation this skill handles>, load the"
                  + " `{name}` skill.\"")
          .autotriggerFile("guardrails.md")
          .triggerLine("- When {desc}, load the `{name}` skill.\n")
          .subagentRequiresMode(true)
          .pluginDir("plugin")
          .hooksFile(null)
          .stubs(
              List.of(
                  new SurfaceSpec.Stub(
                      "scripts/README.md",
                      "<!-- c7: deterministic helpers, run as `python3 scripts/<name>.py`. "
                          + "Add one by emitting scripts/<name>.py. -->\n"),
                  new SurfaceSpec.Stub(
                      "sub_agents/README.md",
                      "<!-- c5: delegated subagents, invoked with the `task` tool in their OWN"
                          + " context. Add one as sub_agents/<name>/AGENT.md with frontmatter"
                          + " `mode: subagent` and `description:`. -->\n"),
                  new SurfaceSpec.Stub(
                      "plugin/README.md",
                      "<!-- c8: OpenCode plugins. Acts INSIDE the tool loop -- the only lever that"
                          + " can enforce something the model cannot skip. Add one as"
                          + " plugin/<name>.ts. -->\n"),
                  new SurfaceSpec.Stub(
                      "skills/README.md",
                      "<!-- c6: reusable workflows loaded on demand via the `skill` tool. Add one"
                          + " as skills/<name>/SKILL.md with frontmatter `name:` matching the"
                          + " directory and a `description:`. -->\n")))
          .build();

  /** Exactly one {@code {{ instruction }}} at end; no other Jinja brace pairs. */
  public static void assertJinjaSafe(String composed) {
    String placeholder = "{{ instruction }}";
    int n = 0;
    int from = 0;
    while (true) {
      int i = composed.indexOf(placeholder, from);
      if (i < 0) {
        break;
      }
      n++;
      from = i + placeholder.length();
    }
    if (n != 1) {
      throw new IllegalArgumentException(
          "composed template must contain the '{{ instruction }}' placeholder exactly once (found "
              + n
              + ")");
    }
    String without = composed.replaceFirst(java.util.regex.Pattern.quote(placeholder), "");
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("\\{\\{|\\{%|\\{#").matcher(without);
    if (m.find()) {
      throw new IllegalArgumentException(
          "always-on text or a skill/subagent description contains a Jinja brace pair ("
              + m.group()
              + ")");
    }
  }
}
