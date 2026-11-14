package com.metamorph.core.recursion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metamorph.core.spec.BenchPrompts;
import com.metamorph.core.spec.BenchSpec;
import com.metamorph.core.spec.BenchmarkAdapter;
import com.metamorph.core.spec.ChannelSpec;
import com.metamorph.core.spec.SubstrateLike;
import com.metamorph.substrate.OpencodeSubstrate;
import com.metamorph.substrate.OpencodeSurfaces;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptTreeTest {
  @TempDir Path root;

  @Test
  void unchangedTreeIsKeptBaseAndValid() {
    Map<String, String> tree = baseTree();
    Recursion.Accept got =
        Recursion.acceptTree(OpencodeSurfaces.OPENCODE_SURFACE, adapter(false), tree, tree, "inv1");
    assertTrue(got.keptBase());
    assertEquals("", got.error());
  }

  @Test
  void modificationClearsKeptBase() {
    Map<String, String> fallback = baseTree();
    Map<String, String> neu = new LinkedHashMap<>(fallback);
    neu.put("systemprompt.md", "changed prompt\n");
    Recursion.Accept got =
        Recursion.acceptTree(
            OpencodeSurfaces.OPENCODE_SURFACE, adapter(false), neu, fallback, "dom1");
    assertFalse(got.keptBase());
    assertEquals("", got.error());
  }

  @Test
  void validateTreeFailureSurfacesError() {
    Map<String, String> fallback = baseTree();
    Map<String, String> neu = new LinkedHashMap<>(fallback);
    neu.put("systemprompt.md", "optimize the rubric score\n");
    Recursion.Accept got =
        Recursion.acceptTree(
            OpencodeSurfaces.OPENCODE_SURFACE, adapter(false), neu, fallback, "agg1");
    assertTrue(got.error().contains("grader") || got.error().contains("rubric"));
  }

  @Test
  void adapterValidateTreeFailureSurfacesError() {
    Map<String, String> fallback = baseTree();
    Map<String, String> neu = new LinkedHashMap<>(fallback);
    neu.put("systemprompt.md", "ok but missing cue\n");
    Recursion.Accept got =
        Recursion.acceptTree(
            OpencodeSurfaces.OPENCODE_SURFACE, adapter(true), neu, fallback, "agg1");
    assertTrue(got.error().contains("output/"));
  }

  private static Map<String, String> baseTree() {
    Map<String, String> tree = new LinkedHashMap<>();
    for (String name : OpencodeSurfaces.OPENCODE_SURFACE.alwaysOn()) {
      tree.put(name, "");
    }
    tree.put("systemprompt.md", "write deliverables under output/\n");
    return tree;
  }

  private BenchmarkAdapter adapter(boolean requireOutputCue) {
    return new BenchmarkAdapter() {
      private final OpencodeSubstrate substrate = new OpencodeSubstrate();
      private final BenchSpec spec =
          BenchSpec.builder(
                  "demo",
                  root.resolve("runs"),
                  new BenchPrompts("task"))
              .installMode("workspace_config")
              .channels(
                  Map.of(
                      "proposer",
                      new ChannelSpec("opencode", "fixture"),
                      "selector",
                      new ChannelSpec("opencode", "fixture")))
              .build();

      @Override
      public BenchSpec spec() {
        return spec;
      }

      @Override
      public SubstrateLike substrate() {
        return substrate;
      }

      @Override
      public List<String> tasks(String split) {
        return List.of();
      }

      @Override
      public String domainOf(String taskId) {
        return "";
      }

      @Override
      public String taskPrompt(String taskId) {
        return "";
      }

      @Override
      public Path taskFiles(String taskId) {
        return null;
      }

      @Override
      public Path execute(String taskId, Path harnessDir, String runId, Integer timeout) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean delivered(Path runDir) {
        return false;
      }

      @Override
      public String trajectory(Path runDir, int obsTrunc) {
        return "";
      }

      @Override
      public Map<String, String> deliverableFiles(Path runDir) {
        return Map.of();
      }

      @Override
      public String deliverableText(Path runDir) {
        return "";
      }

      @Override
      public Path baseHarness() {
        return Path.of(".");
      }

      @Override
      public void validateTree(Map<String, String> tree) {
        if (requireOutputCue && !tree.getOrDefault("systemprompt.md", "").contains("output/")) {
          throw new IllegalArgumentException(
              "the rendered prompt must say deliverables go to `output/<filename>`");
        }
      }
    };
  }
}
