package com.metamorph.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GlobMatchTest {
  @Test
  void fnmatchAndLeakMatchNestedAnswerKeys() {
    assertTrue(GlobMatch.fnmatch("reference_answer.txt", "reference_answer.txt"));
    assertTrue(GlobMatch.leakMatch("nested/reference_answer.txt", "reference_answer.txt"));
    assertTrue(GlobMatch.leakMatch("./gold/key.json", "gold/key.json"));
    assertFalse(GlobMatch.leakMatch("notes.txt", "reference_answer.txt"));
    assertTrue(GlobMatch.fnmatch("a/b/c", "*/c"));
  }

  @Test
  void permissionGlobDoesNotBreakOnEscapedDots() {
    String re = GlobMatch.permissionGlobToRegex("/tmp/hevo_ws/**");
    assertTrue(Pattern.compile(re, Pattern.DOTALL).matcher("/tmp/hevo_ws/a/b").matches());
    assertFalse(Pattern.compile(re, Pattern.DOTALL).matcher("/tmp/other/a").matches());
    // Naive ** → .* then * → [^/]* would corrupt this to .[^/]* 
    String starStar = GlobMatch.permissionGlobToRegex("**/score.json");
    assertTrue(Pattern.compile(starStar, Pattern.DOTALL).matcher("a/b/score.json").matches());
  }
}
