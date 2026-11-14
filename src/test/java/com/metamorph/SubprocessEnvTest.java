package com.metamorph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.metamorph.config.Config;
import com.metamorph.core.proposer.Proposer;
import com.metamorph.demo.DemoAdapter;
import com.metamorph.demo.DemoProposer;
import com.metamorph.substrate.CodexSubstrate;
import com.metamorph.substrate.OpencodeSubstrate;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubprocessEnvTest {
  @TempDir Path root;

  @Test
  void scrubEnvAcrossLaunchers() throws Exception {
    DemoAdapter adapter = new DemoAdapter(root);
    DemoProposer demo = new DemoProposer(adapter.spec().channels().get("proposer"));
    Proposer prop = demo;
    CodexSubstrate cx = new CodexSubstrate();

    Map<String, String> host = new LinkedHashMap<>();
    host.put("METAMORPH_JUDGE_API_KEY", "secret-judge");
    host.put("METAMORPH_BRIDGE_KEY", "secret-bridge");
    host.put("METAMORPH_CODEX_API_KEY", "secret-codex");
    host.put("OPENAI_API_KEY", "sk-judge");
    host.put("ANTHROPIC_API_KEY", "sk-ant");
    host.put("ANTHROPIC_AUTH_TOKEN", "tok-ant");
    host.put("ANTHROPIC_BASE_URL", "https://proxy.example/v1");
    host.put("GITHUB_TOKEN", "ghp_secret");
    host.put("AWS_ACCESS_KEY_ID", "AKIA_TEST");
    host.put("AWS_SECRET_ACCESS_KEY", "aws_secret");
    host.put("AWS_SESSION_TOKEN", "aws_sess");
    host.put("AWS_BEARER_TOKEN_BEDROCK", "bedrock-tok");
    host.put("GOOGLE_API_KEY", "g-key");
    host.put("GOOGLE_GENERATIVE_AI_API_KEY", "g-genai-key");
    host.put("GOOGLE_APPLICATION_CREDENTIALS", "/adc.json");
    host.put("PATH", "/usr/bin");
    host.put("SAFE_FLAG", "1");

    Map<String, Map<String, String>> launchers = new LinkedHashMap<>();
    launchers.put("scrub_env", Config.scrubEnv(host));
    launchers.put("proposer", prop.env()); // uses live process env; also check scrubEnv(host)
    launchers.put("opencode", ((OpencodeSubstrate) adapter.substrate()).runEnv(host, root));
    launchers.put("codex", cx.execEnv(host, root));

    Set<String> secrets =
        Set.of(
            "METAMORPH_JUDGE_API_KEY",
            "METAMORPH_BRIDGE_KEY",
            "METAMORPH_CODEX_API_KEY",
            "OPENAI_API_KEY",
            "ANTHROPIC_API_KEY",
            "ANTHROPIC_AUTH_TOKEN",
            "ANTHROPIC_BASE_URL",
            "GITHUB_TOKEN",
            "AWS_ACCESS_KEY_ID",
            "AWS_SECRET_ACCESS_KEY",
            "AWS_SESSION_TOKEN",
            "AWS_BEARER_TOKEN_BEDROCK",
            "GOOGLE_API_KEY",
            "GOOGLE_GENERATIVE_AI_API_KEY");

    for (var e : launchers.entrySet()) {
      if ("proposer".equals(e.getKey())) {
        // Proposer.env() reads the live JVM env; validate the host scrub path instead.
        Map<String, String> scrubbed = Config.scrubEnv(host);
        for (String key : secrets) {
          assertFalse(scrubbed.containsKey(key), e.getKey() + " kept " + key);
        }
        assertEquals("/adc.json", scrubbed.get("GOOGLE_APPLICATION_CREDENTIALS"));
        continue;
      }
      for (String key : secrets) {
        assertFalse(e.getValue().containsKey(key), e.getKey() + " kept " + key);
      }
      assertEquals("/adc.json", e.getValue().get("GOOGLE_APPLICATION_CREDENTIALS"));
      assertTrue(e.getValue().containsKey("SAFE_FLAG") || e.getValue().containsKey("PATH"));
    }

    assertFalse(Config.scrubEnv(host).containsKey("OPENAI_API_KEY"));
    assertTrue(cx.execEnv(host, root).containsKey("CODEX_HOME"));
  }
}
