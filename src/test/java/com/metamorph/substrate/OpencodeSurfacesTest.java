package com.metamorph.substrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metamorph.core.tree.SurfaceSpec;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OpencodeSurfacesTest {
  @Test
  void surfaceMatchesPythonAlwaysOnAndStubKeys() {
    SurfaceSpec s = OpencodeSurfaces.OPENCODE_SURFACE;
    assertEquals(
        java.util.List.of(
            "systemprompt.md",
            "guardrails.md",
            "LongTermMEMORY.md",
            "tool_descriptions/tool_guidance.md"),
        s.alwaysOn());
    assertEquals(24_000, s.alwaysOnMax());
    assertEquals(
        java.util.Set.of(
            "scripts/README.md", "sub_agents/README.md", "plugin/README.md", "skills/README.md"),
        s.stubs().stream().map(SurfaceSpec.Stub::rel).collect(Collectors.toSet()));
    assertTrue(s.stubs().stream().anyMatch(st -> st.body().contains("plugin/<name>.ts")));
    assertTrue(s.stubs().stream().anyMatch(st -> st.body().contains("mode: subagent")));
  }

  @Test
  void jinjaSafeRequiresSingleTrailingInstructionPlaceholder() {
    OpencodeSurfaces.assertJinjaSafe("prelude\n{{ instruction }}");
    assertThrows(IllegalArgumentException.class, () -> OpencodeSurfaces.assertJinjaSafe("no ph"));
    assertThrows(
        IllegalArgumentException.class,
        () -> OpencodeSurfaces.assertJinjaSafe("{{ instruction }}\n{{ instruction }}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> OpencodeSurfaces.assertJinjaSafe("bad {{ foo }} here\n{{ instruction }}"));
  }
}
