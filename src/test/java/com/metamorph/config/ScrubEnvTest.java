package com.metamorph.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScrubEnvTest {
  @Test
  void dropsPinnedSecretsButKeepsPathAndAllowlistedKeys() {
    Map<String, String> host = new LinkedHashMap<>();
    for (String key : Config.SCRUB_ENV) {
      host.put(key, "secret-" + key);
    }
    host.put("PATH", "/bin");
    host.put("HOME", "/home/u");
    host.put("SAFE_FLAG", "1");
    host.put("OPENAI_API_KEY", "should-drop");

    Map<String, String> scrubbed = Config.scrubEnv(host);
    for (String key : Config.SCRUB_ENV) {
      assertFalse(scrubbed.containsKey(key), "kept " + key);
    }
    assertEquals("/bin", scrubbed.get("PATH"));
    assertEquals("/home/u", scrubbed.get("HOME"));
    assertEquals("1", scrubbed.get("SAFE_FLAG"));

    Map<String, String> kept =
        Config.scrubEnv(host, Set.of("OPENAI_API_KEY", "METAMORPH_JUDGE_API_KEY"));
    assertEquals("should-drop", kept.get("OPENAI_API_KEY"));
    assertEquals("secret-METAMORPH_JUDGE_API_KEY", kept.get("METAMORPH_JUDGE_API_KEY"));
    assertFalse(kept.containsKey("ANTHROPIC_API_KEY"));
  }

  @Test
  void scrubEnvSetCoversCommonProviderCredentials() {
    assertTrue(Config.SCRUB_ENV.contains("OPENAI_API_KEY"));
    assertTrue(Config.SCRUB_ENV.contains("ANTHROPIC_API_KEY"));
    assertTrue(Config.SCRUB_ENV.contains("GITHUB_TOKEN"));
    assertTrue(Config.SCRUB_ENV.contains("AWS_SECRET_ACCESS_KEY"));
    assertTrue(Config.SCRUB_ENV.contains("METAMORPH_JUDGE_API_KEY"));
    assertTrue(Config.SCRUB_ENV.contains("SLACK_BOT_TOKEN"));
  }
}
