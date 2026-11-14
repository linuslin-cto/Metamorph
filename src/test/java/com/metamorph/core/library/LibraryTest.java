package com.metamorph.core.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metamorph.substrate.OpencodeSurfaces;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryTest {
  @TempDir Path root;

  @Test
  void hasExperienceRequiresCueLessonEvidence() {
    assertFalse(Library.hasExperience(null));
    assertFalse(Library.hasExperience(Map.of("cue", "a")));
    assertFalse(Library.hasExperience(Map.of("cue", "c", "lesson", "l", "evidence", "  ")));
    assertFalse(Library.hasExperience(Map.of("cue", "c", "lesson", 1, "evidence", "e")));
    assertTrue(
        Library.hasExperience(Map.of("cue", "c", "lesson", "l", "evidence", "e")));
  }

  @Test
  void parseFieldsReadsCueLessonEvidenceConfidence() {
    Map<String, String> fields =
        Library.parseFields(
            "cue: when totals disagree\n"
                + "lesson: prefer the source table\n"
                + "evidence: agg1 vs base1 trajectory\n"
                + "confidence: medium, one contrast\n");
    assertEquals("when totals disagree", fields.get("cue"));
    assertEquals("prefer the source table", fields.get("lesson"));
    assertEquals("agg1 vs base1 trajectory", fields.get("evidence"));
    assertEquals("medium, one contrast", fields.get("confidence"));
    assertTrue(Library.hasExperience(new LinkedHashMap<>(fields)));
    assertTrue(Library.parseFields("NO_ENTRY").isEmpty());
    Map<String, String> incomplete = Library.parseFields("lesson: x\nevidence: y\nconfidence: high");
    assertEquals(Map.of("lesson", "x", "evidence", "y", "confidence", "high"), incomplete);
    assertFalse(Library.hasExperience(incomplete));
  }

  @Test
  void addRetainAndEntries() throws Exception {
    var surface = OpencodeSurfaces.OPENCODE_SURFACE;
    Library lib = new Library(root.resolve("agg"), "accounting", surface);
    Map<String, String> tree = new LinkedHashMap<>();
    for (String a : surface.alwaysOn()) {
      tree.put(a, "x");
    }
    tree.put("scripts/summarize.py", "print(1)\n");
    Map<String, Object> exp = Map.of("cue", "credits", "lesson", "sum both", "evidence", "contrast");
    assertNotNull(lib.add("ledger-1", tree, "why", "agg1", "rid", exp));
    assertEquals(List.of("ledger-1"), lib.entries());
    assertNotNull(lib.contextDir());
    assertTrue(Files.isRegularFile(lib.index()));

    lib.add("ledger-2", tree, "why2", "agg1", "rid2", exp);
    assertEquals(List.of("ledger-1", "ledger-2"), lib.entries());
    lib.retain(List.of("ledger-1"));
    assertEquals(List.of("ledger-1"), lib.entries());
  }

  @Test
  void domainDirCollapsesSeparators() {
    assertEquals("a__b", Library.domainDir("a / b"));
    assertEquals("unknown", Library.domainDir("  "));
  }

  @Test
  void emptyLibraryHasNoContextDir() {
    Library lib = new Library(root, "empty", OpencodeSurfaces.OPENCODE_SURFACE);
    assertNull(lib.contextDir());
  }
}
