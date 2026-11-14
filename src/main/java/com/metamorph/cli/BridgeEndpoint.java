package com.metamorph.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Offline Codex bridge endpoint bookkeeping: reuse saved port/key so setup-written Codex config
 * stays valid. Ports {@code upstream.cli.bridge._endpoint} without starting LiteLLM.
 */
public final class BridgeEndpoint {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final SecureRandom RNG = new SecureRandom();

  public static final String LITELLM_CONFIG =
      """
      model_list:
        - model_name: %s
          litellm_params:
            model: vertex_ai/%s
            vertex_project: os.environ/GOOGLE_CLOUD_PROJECT
            vertex_location: os.environ/GOOGLE_CLOUD_LOCATION
      litellm_settings:
        drop_params: true
      general_settings:
        master_key: os.environ/METAMORPH_BRIDGE_KEY
      """;

  private BridgeEndpoint() {}

  public record Endpoint(int port, String key) {}

  public static int freePort(String host) throws Exception {
    try (ServerSocket s = new ServerSocket(0, 0, java.net.InetAddress.getByName(host))) {
      return s.getLocalPort();
    }
  }

  public static String newKey() {
    byte[] buf = new byte[18];
    RNG.nextBytes(buf);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }

  /** Reuse saved port/key when present; otherwise allocate. Persists {@code bridge.json}. */
  public static Endpoint loadOrCreate(Path path, String host, int port) throws Exception {
    Map<String, Object> saved = Map.of();
    if (Files.isRegularFile(path)) {
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = JSON.readValue(Files.readString(path), Map.class);
        saved = parsed == null ? Map.of() : parsed;
      } catch (Exception ignored) {
        saved = Map.of();
      }
    }
    int chosen =
        port > 0
            ? port
            : saved.get("port") instanceof Number n ? n.intValue() : freePort(host == null ? "127.0.0.1" : host);
    String key =
        saved.get("key") instanceof String k && !k.isBlank() ? k : newKey();
    Endpoint endpoint = new Endpoint(chosen, key);
    Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("port", endpoint.port());
    payload.put("key", endpoint.key());
    Files.writeString(path, JSON.writeValueAsString(payload) + "\n", StandardCharsets.UTF_8);
    try {
      Set<PosixFilePermission> perms =
          EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      Files.setPosixFilePermissions(path, perms);
    } catch (UnsupportedOperationException ignored) {
      // Windows: ACL not POSIX; best-effort skip.
    }
    return endpoint;
  }

  public static String litellmYaml(String model) {
    String m = model == null || model.isBlank() ? "fixture-model" : model;
    return LITELLM_CONFIG.formatted(m, m);
  }

  public static Map<String, String> envHints(Endpoint endpoint, String model, String host) {
    Map<String, String> settings = new LinkedHashMap<>();
    settings.put("METAMORPH_CODEX_BASE_URL", "http://127.0.0.1:" + endpoint.port() + "/v1");
    settings.put("METAMORPH_CODEX_API_KEY", endpoint.key());
    settings.put("METAMORPH_CODEX_MODEL", model == null ? "" : model);
    if (host != null && !host.equals("127.0.0.1")) {
      settings.put(
          "METAMORPH_CODEX_CONTAINER_URL",
          "http://host.docker.internal:" + endpoint.port() + "/v1");
    }
    return settings;
  }
}
