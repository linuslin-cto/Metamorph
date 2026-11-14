package com.metamorph.cli;

import com.metamorph.config.Config;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Prepare a Codex Responses-API bridge endpoint file and print .env hints. Offline stub: writes
 * {@code bridge.json}/{@code bridge.yaml} via {@link BridgeEndpoint} but does not launch LiteLLM.
 */
@Command(
    name = "bridge",
    mixinStandardHelpOptions = true,
    description =
        "Prepare Codex bridge endpoint files (offline; does not start LiteLLM)")
public final class BridgeCommand implements Callable<Integer> {
  @Option(names = "--model", description = "Vertex model ID to serve", defaultValue = "")
  String model;

  @Option(
      names = "--host",
      defaultValue = "127.0.0.1",
      description = "interface for the future LiteLLM bind; use 0.0.0.0 for containers")
  String host;

  @Option(names = "--port", defaultValue = "0", description = "0 = reuse saved port, else free")
  int port;

  @Option(
      names = "--root",
      description = "directory for bridge.json / bridge.yaml (default: dependencies/codex)")
  Path root;

  @Override
  public Integer call() throws Exception {
    String resolvedModel =
        (model == null || model.isBlank())
            ? Config.env("METAMORPH_CODEX_MODEL", Config.env("METAMORPH_SOLVER_MODEL_CX", "fixture-model"))
            : model;
    Path dir =
        root != null
            ? root.toAbsolutePath().normalize()
            : Config.projectRoot().resolve(Config.CODEX_DIST).toAbsolutePath().normalize();
    Files.createDirectories(dir);
    BridgeEndpoint.Endpoint endpoint =
        BridgeEndpoint.loadOrCreate(dir.resolve("bridge.json"), host, port);
    Path yaml = dir.resolve("bridge.yaml");
    Files.writeString(yaml, BridgeEndpoint.litellmYaml(resolvedModel), StandardCharsets.UTF_8);

    Map<String, String> settings = BridgeEndpoint.envHints(endpoint, resolvedModel, host);
    System.out.println("Wrote " + dir.resolve("bridge.json") + " and " + yaml);
    System.out.println("Add these to .env, then run setup with --substrate cx:");
    for (var e : settings.entrySet()) {
      System.out.println(e.getKey() + "=" + e.getValue());
    }
    System.out.println(
        "(LiteLLM not launched — install litellm and re-run a live bridge when ready.)");
    return 0;
  }
}
