package com.metamorph.substrate;

import com.metamorph.config.Config;
import com.metamorph.config.ConfigError;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline Codex substrate readiness checks (URL shape + binary presence). Ports the non-network
 * parts of {@code CodexSubstrate.preflight}; connection probes are optional.
 */
public final class CodexPreflight {
  private CodexPreflight() {}

  public record Result(boolean ok, List<String> errors) {}

  public static String baseUrl() {
    return Config.env("METAMORPH_CODEX_BASE_URL", "");
  }

  public static String apiKey() {
    return Config.env("METAMORPH_CODEX_API_KEY", "");
  }

  public static String containerUrl() {
    return Config.env("METAMORPH_CODEX_CONTAINER_URL", "");
  }

  /** Validate HTTP(S) Responses URL shape without opening a socket. */
  public static void validateHttpUrl(String name, String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ConfigError(name + " is required for Codex Responses endpoint");
    }
    URI uri;
    try {
      uri = URI.create(raw.strip());
    } catch (Exception e) {
      throw new ConfigError(name + " must be an HTTP(S) URL");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
    if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null || uri.getHost().isBlank()) {
      throw new ConfigError(name + " must be an HTTP(S) URL");
    }
  }

  public static Path codexBinary() {
    return Path.of(Config.CODEX_DIST).resolve("codex");
  }

  public static Result check(boolean requireDist, boolean requireBridge) {
    List<String> errors = new ArrayList<>();
    try {
      Config.require("METAMORPH_CODEX_BASE_URL", "Codex Responses endpoint");
      Config.require("METAMORPH_CODEX_API_KEY", "Codex endpoint authentication");
      validateHttpUrl("METAMORPH_CODEX_BASE_URL", baseUrl());
    } catch (ConfigError e) {
      errors.add(e.getMessage());
    }
    if (requireDist && !Files.isRegularFile(codexBinary())) {
      errors.add(
          "Codex is not installed; run setup with --substrate cx (expected binary at "
              + codexBinary()
              + ")");
    }
    if (requireBridge && (containerUrl() == null || containerUrl().isBlank())) {
      errors.add(
          "Set METAMORPH_CODEX_CONTAINER_URL to a container-accessible Responses endpoint");
    } else if (requireBridge) {
      try {
        validateHttpUrl("METAMORPH_CODEX_CONTAINER_URL", containerUrl());
      } catch (ConfigError e) {
        errors.add(e.getMessage());
      }
    }
    return new Result(errors.isEmpty(), List.copyOf(errors));
  }
}
