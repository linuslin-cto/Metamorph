package com.metamorph.substrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metamorph.core.tree.SurfaceSpec;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CodexSurfacesTest {
  @Test
  void surfaceMatchesPythonAlwaysOnHooksAndStubBodies() {
    SurfaceSpec s = CodexSurfaces.CODEX_SURFACE;
    assertEquals(java.util.List.of("AGENTS.md"), s.alwaysOn());
    assertEquals(8_000, s.alwaysOnMax());
    assertTrue(s.rejectEmptyAlwaysOn());
    assertTrue(s.compileSkillScripts());
    assertEquals(".codex/hooks.json", s.hooksFile());
    assertTrue(s.hookEvents().contains("PostToolUse"));
    assertEquals(
        java.util.Set.of(
            "scripts/README.md",
            "sub_agents/README.md",
            ".codex/README.md",
            ".agents/skills/README.md"),
        s.stubs().stream().map(SurfaceSpec.Stub::rel).collect(Collectors.toSet()));
    assertTrue(
        s.stubs().stream()
            .anyMatch(st -> st.rel().equals(".codex/README.md") && st.body().contains("hooks.json")));
    assertTrue(
        s.stubs().stream()
            .anyMatch(
                st ->
                    st.rel().equals(".agents/skills/README.md")
                        && st.body().contains("description:` is REQUIRED")));
  }
}
