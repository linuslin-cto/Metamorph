package com.metamorph.core.tree;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metamorph.substrate.CodexSurfaces;
import com.metamorph.substrate.OpencodeSurfaces;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HarnessTreeTest {
  @TempDir Path root;

  @Test
  void allowsAlwaysOnSkillsPluginsAndRejectsEscapes() {
    SurfaceSpec oc = OpencodeSurfaces.OPENCODE_SURFACE;
    assertTrue(HarnessTree.isAllowed(oc, "systemprompt.md"));
    assertTrue(HarnessTree.isAllowed(oc, "scripts/summarize.py"));
    assertTrue(HarnessTree.isAllowed(oc, "skills/pack/SKILL.md"));
    assertTrue(HarnessTree.isAllowed(oc, "skills/pack/scripts/helper.py"));
    assertTrue(HarnessTree.isAllowed(oc, "sub_agents/reviewer/AGENT.md"));
    assertTrue(HarnessTree.isAllowed(oc, "plugin/enforce.ts"));
    assertFalse(HarnessTree.isAllowed(oc, "../escape.md"));
    assertFalse(HarnessTree.isAllowed(oc, "secrets/key.txt"));
    assertThrows(IllegalArgumentException.class, () -> HarnessTree.normalizeRel("../x"));

    SurfaceSpec cx = CodexSurfaces.CODEX_SURFACE;
    assertTrue(HarnessTree.isAllowed(cx, "AGENTS.md"));
    assertTrue(HarnessTree.isAllowed(cx, ".codex/hooks.json"));
    assertTrue(HarnessTree.isAllowed(cx, ".agents/skills/pack/SKILL.md"));
  }

  @Test
  void validateMinimalTreeAndWriteWithStubs() throws Exception {
    SurfaceSpec surface = OpencodeSurfaces.OPENCODE_SURFACE;
    Map<String, String> tree = new LinkedHashMap<>();
    for (String a : surface.alwaysOn()) {
      tree.put(a, "ok");
    }
    tree.put("scripts/summarize.py", "print(1)\n");
    HarnessTree.validate(surface, tree);

    Path dest = root.resolve("h");
    HarnessTree.writeTree(surface, tree, dest, true);
    assertTrue(Files.isRegularFile(dest.resolve("systemprompt.md")));
    assertTrue(Files.isRegularFile(dest.resolve("scripts/README.md")));

    List<String> rejected = new ArrayList<>();
    Files.writeString(dest.resolve("not-allowed.bin"), "x");
    Map<String, String> read = HarnessTree.readTree(surface, dest, rejected);
    assertTrue(read.containsKey("systemprompt.md"));
    assertTrue(rejected.stream().anyMatch(r -> r.contains("not-allowed.bin")));
  }
}
