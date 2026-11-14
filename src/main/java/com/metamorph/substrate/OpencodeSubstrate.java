package com.metamorph.substrate;

import com.metamorph.config.Config;
import com.metamorph.core.spec.SubstrateLike;
import com.metamorph.core.tree.SurfaceSpec;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OpencodeSubstrate implements SubstrateLike {
  @Override
  public String name() {
    return "oc";
  }

  @Override
  public SurfaceSpec surface() {
    return OpencodeSurfaces.OPENCODE_SURFACE;
  }

  /** Scrubbed environment for OpenCode solver subprocesses. */
  public Map<String, String> runEnv(Map<String, String> baseEnv, Path workDir) {
    Map<String, String> env = new LinkedHashMap<>(Config.scrubEnv(baseEnv));
    env.put("METAMORPH_OPENCODE_WORK", workDir.toAbsolutePath().toString());
    return env;
  }
}
