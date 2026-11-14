package com.metamorph.benchmarks.jb;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * JobBench OpenCode rollout isolation: per-workspace npm/pip/tmp sandbox and OpenCode
 * {@code external_directory} fence. Ports {@code jb_opencode_runner._isolation_env} /
 * {@code _permission} / {@code _sandbox}.
 */
public final class JobBenchIsolation {
  public static final Path DEFAULT_TEMP_BASE = Path.of("/tmp/hevo_ws");

  private static final ObjectMapper JSON = new ObjectMapper();

  private JobBenchIsolation() {}

  /** This rollout's npm prefix, Python user base and temp dir; short for AF_UNIX socket paths. */
  public static Path sandbox(Path workspace) {
    return sandbox(workspace, DEFAULT_TEMP_BASE);
  }

  public static Path sandbox(Path workspace, Path tempBase) {
    String digest = sha1Hex(workspace.getFileName().toString()).substring(0, 12);
    Path parent = workspace.getParent() == null ? tempBase : workspace.getParent();
    return parent.resolve("sb-" + digest);
  }

  public static void stageSandbox(Path workspace) throws Exception {
    Path sb = sandbox(workspace);
    if (Files.exists(sb)) {
      deleteRecursive(sb);
    }
    Files.createDirectories(sb.resolve("npm-global"));
    Files.createDirectories(sb.resolve("python-user"));
    Files.createDirectories(sb.resolve("tmp"));
  }

  /** Send npm -g / pip --user / temp files into this rollout's sandbox. */
  public static Map<String, String> isolationEnv(Path workspace, String path) {
    return isolationEnv(workspace, path, File.separatorChar == '\\' ? ";" : ":");
  }

  public static Map<String, String> isolationEnv(Path workspace, String path, String pathSep) {
    Path sb = sandbox(workspace);
    Path npm = sb.resolve("npm-global");
    Path user = sb.resolve("python-user");
    Map<String, String> env = new LinkedHashMap<>();
    env.put("NPM_CONFIG_PREFIX", npm.toString());
    env.put("PYTHONUSERBASE", user.toString());
    env.put("TMPDIR", sb.resolve("tmp").toString());
    StringBuilder pb = new StringBuilder();
    for (String p :
        new String[] {npm.resolve("bin").toString(), user.resolve("bin").toString(), path}) {
      if (p != null && !p.isBlank()) {
        if (!pb.isEmpty()) {
          pb.append(pathSep);
        }
        pb.append(p);
      }
    }
    env.put("PATH", pb.toString());
    return env;
  }

  public static String permission(Path workspace) throws Exception {
    return permission(workspace, DEFAULT_TEMP_BASE);
  }

  public static String permission(Path workspace, Path tempBase) throws Exception {
    Map<String, String> external = new LinkedHashMap<>();
    external.put("*", "deny");
    external.put("/tmp", "allow");
    external.put("/tmp/*", "allow");
    external.put("/tmp/**", "allow");
    external.put(posix(tempBase) + "/*", "allow");
    external.put(posix(tempBase) + "/**", "deny");
    external.put(posix(sandbox(workspace, tempBase)) + "/**", "allow");
    external.put(posix(workspace) + "/**", "allow");
    return JSON.writeValueAsString(Map.of("external_directory", external));
  }

  /**
   * OpenCode v1.14.18-style external_directory verdict: request is {@code parent/*}; last matching
   * rule wins. Glob compile matches Python {@code re.escape(...).replace(r"\*", ".*")}.
   */
  public static boolean allows(String permissionJson, Path path) throws Exception {
    @SuppressWarnings("unchecked")
    Map<String, Object> root = JSON.readValue(permissionJson, Map.class);
    @SuppressWarnings("unchecked")
    Map<String, String> rules = (Map<String, String>) root.get("external_directory");
    String request = posix(path.getParent()) + "/*";
    String verdict = null;
    for (var e : rules.entrySet()) {
      if (Pattern.compile(globToRegex(posix(e.getKey())), Pattern.DOTALL).matcher(request).matches()) {
        verdict = e.getValue();
      }
    }
    return "allow".equals(verdict);
  }

  static String posix(Object path) {
    return String.valueOf(path).replace('\\', '/');
  }

  /** Escape like Python re.escape, then turn glob stars/questions into regex. */
  static String globToRegex(String pattern) {
    return com.metamorph.util.GlobMatch.permissionGlobToRegex(pattern);
  }

  private static String sha1Hex(String s) {
    try {
      byte[] dig =
          MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(dig);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void deleteRecursive(Path root) throws Exception {
    try (var walk = Files.walk(root)) {
      for (Path p : walk.sorted((a, b) -> b.compareTo(a)).toList()) {
        Files.deleteIfExists(p);
      }
    }
  }

  /** Windows-friendly File.separator stand-in without importing java.io in callers. */
  private static final class File {
    static final char separatorChar = java.io.File.separatorChar;
  }
}
