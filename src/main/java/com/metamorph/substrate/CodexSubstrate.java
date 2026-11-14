package com.metamorph.substrate;

import com.metamorph.config.Config;
import com.metamorph.core.spec.SubstrateLike;
import com.metamorph.core.tree.SurfaceSpec;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Codex CLI substrate stub: surface + scrubbed exec environment. */
public final class CodexSubstrate implements SubstrateLike {
  @Override
  public String name() {
    return "cx";
  }

  @Override
  public SurfaceSpec surface() {
    return CodexSurfaces.CODEX_SURFACE;
  }

  /** Reference {@code codex exec} environment; real launchers may refine further. */
  public Map<String, String> execEnv(Map<String, String> baseEnv, Path codexHome) {
    Map<String, String> env = new LinkedHashMap<>(Config.scrubEnv(baseEnv));
    env.remove("OPENAI_BASE_URL");
    env.put("CODEX_HOME", codexHome.toAbsolutePath().toString());
    String pathExtra = Path.of(Config.CODEX_DIST).resolve("codex-path").toString();
    String path = env.getOrDefault("PATH", "");
    env.put("PATH", path.isBlank() ? pathExtra : pathExtra + java.io.File.pathSeparator + path);
    return env;
  }

  /** Offline URL / binary checks; see {@link CodexPreflight}. */
  public CodexPreflight.Result preflight(boolean requireDist, boolean requireBridge) {
    return CodexPreflight.check(requireDist, requireBridge);
  }
}
