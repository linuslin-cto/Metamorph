package com.metamorph.util;

import java.util.regex.Pattern;

/**
 * Pathname-style and Python-{@code fnmatch}-style glob helpers used by leak tripwires and OpenCode
 * permission fences.
 */
public final class GlobMatch {
  private GlobMatch() {}

  /**
   * Python {@code fnmatch.fnmatch} stand-in: {@code *} / {@code ?} match any characters including
   * {@code /}; character classes are not implemented (leak patterns are simple).
   */
  public static boolean fnmatch(String name, String pattern) {
    if (name == null || pattern == null) {
      return false;
    }
    return Pattern.compile(fnmatchToRegex(pattern), Pattern.DOTALL).matcher(name).matches();
  }

  /** True when rel matches pattern or a starred parent prefix (Python leak tripwire). */
  public static boolean leakMatch(String rel, String pattern) {
    String pat = pattern == null ? "" : pattern.replace('\\', '/').replaceFirst("^\\./+", "");
    String path = rel == null ? "" : rel.replace('\\', '/');
    return fnmatch(path, pat) || fnmatch(path, "*/" + pat);
  }

  /**
   * OpenCode permission globs: escape regex metas, then {@code *} → {@code .*} and {@code ?} → {@code
   * .}. Same contract as {@code JobBenchIsolation.globToRegex}.
   */
  public static String permissionGlobToRegex(String pattern) {
    StringBuilder out = new StringBuilder("^");
    String p = pattern == null ? "" : pattern;
    for (int i = 0; i < p.length(); i++) {
      char c = p.charAt(i);
      switch (c) {
        case '*' -> out.append(".*");
        case '?' -> out.append('.');
        case '\\', '.', '[', ']', '{', '}', '(', ')', '+', '-', '^', '$', '|' ->
            out.append('\\').append(c);
        default -> out.append(c);
      }
    }
    return out.append('$').toString();
  }

  static String fnmatchToRegex(String pattern) {
    StringBuilder out = new StringBuilder("^");
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      switch (c) {
        case '*' -> out.append(".*");
        case '?' -> out.append('.');
        case '\\', '.', '[', ']', '{', '}', '(', ')', '+', '-', '^', '$', '|' ->
            out.append('\\').append(c);
        default -> out.append(c);
      }
    }
    return out.append('$').toString();
  }
}
