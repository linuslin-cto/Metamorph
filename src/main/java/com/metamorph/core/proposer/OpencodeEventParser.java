package com.metamorph.core.proposer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses OpenCode JSONL event streams for answer text, cost, and terminal errors.
 *
 * <p>Mirrors the protocol stand-ins in {@code tests/test_proposer_session_errors.py}.
 */
public final class OpencodeEventParser {
  private static final ObjectMapper JSON = new ObjectMapper();

  private OpencodeEventParser() {}

  public record ParsedSession(String text, double costUsd, String error) {
    public boolean failed() {
      return error != null && !error.isBlank();
    }
  }

  public static ParsedSession parse(String stdout, String answerFileText) {
    String text = answerFileText == null ? "" : answerFileText.strip();
    double cost = 0.0;
    String error = "";
    List<String> textParts = new ArrayList<>();
    if (stdout != null) {
      for (String line : stdout.split("\\R")) {
        String raw = line.strip();
        if (raw.isEmpty()) {
          continue;
        }
        try {
          JsonNode n = JSON.readTree(raw);
          String type = n.path("type").asText("");
          if ("text".equals(type)) {
            String t = n.path("part").path("text").asText("");
            if (!t.isBlank()) {
              textParts.add(t);
            }
          } else if ("step_finish".equals(type) || "step-finish".equals(type)) {
            JsonNode part = n.path("part");
            if (part.has("cost")) {
              cost = part.path("cost").asDouble(cost);
            }
          } else if ("error".equals(type)) {
            JsonNode err = n.path("error");
            String msg = err.path("data").path("message").asText("");
            if (msg.isBlank()) {
              msg = err.path("message").asText(err.path("name").asText("error"));
            }
            error = msg;
          }
        } catch (Exception ignored) {
          // non-JSON lines ignored
        }
      }
    }
    if (text.isBlank() && !textParts.isEmpty()) {
      text = String.join("", textParts).strip();
    }
    if (!error.isBlank()) {
      // Session failed: do not treat partial text as a usable reply.
      return new ParsedSession("", cost, error);
    }
    return new ParsedSession(text, cost, "");
  }

  /**
   * Raise {@link ProposerError} when the session failed or produced no answer; otherwise return a
   * successful {@link Reply}.
   */
  public static Reply toReply(String label, ParsedSession parsed, int attempts) {
    if (parsed.failed()) {
      Reply bad =
          new Reply(label, "", false, parsed.costUsd(), 0.0, attempts, false, "opencode", parsed.error(), null, null);
      throw new ProposerError(parsed.error(), attempts, parsed.costUsd(), bad);
    }
    if (parsed.text().isBlank()) {
      throw new ProposerError("empty reply from session", attempts, parsed.costUsd());
    }
    return new Reply(label, parsed.text(), true, parsed.costUsd(), 0.0, attempts, false, "opencode", "", null, null);
  }
}
