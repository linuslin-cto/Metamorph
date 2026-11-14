package com.metamorph.substrate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders OpenCode {@code --format json} JSONL trajectories into the shared grammar used by
 * JobBench / LAB offline assertions. Ports {@code upstream.substrate.opencode.render_events}.
 */
public final class OpencodeEventRenderer {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String TRUNC_FMT = "…(+%d chars — render truncation)";
  private static final Map<String, String> TEXT_TAGS =
      Map.of("text", "ASSISTANT", "reasoning", "THINKING");

  private OpencodeEventRenderer() {}

  public static String clip(Object value, int n) {
    String s = value == null ? "" : String.valueOf(value).replace("\u0000", "");
    if (n <= 0 || s.length() <= n) {
      return s;
    }
    return s.substring(0, n) + String.format(TRUNC_FMT, s.length() - n);
  }

  public static String renderEvents(Path path, int obsTrunc, int maxChars) {
    return renderEvents(path, obsTrunc, maxChars, 400);
  }

  public static String renderEvents(Path path, int obsTrunc, int maxChars, int argsTrunc) {
    String raw;
    try {
      raw = Files.readString(path, StandardCharsets.UTF_8);
    } catch (Exception e) {
      return "(failed to read trajectory: " + e.getMessage() + ")";
    }
    List<String> lines = new ArrayList<>();
    int argsCap = Math.max(0, argsTrunc);
    for (String ln : raw.split("\\R")) {
      ln = ln.strip();
      if (!ln.startsWith("{")) {
        continue;
      }
      JsonNode ev;
      try {
        ev = JSON.readTree(ln);
      } catch (Exception ignored) {
        continue;
      }
      if (!ev.isObject()) {
        continue;
      }
      JsonNode err = ev.get("error");
      if (err != null && err.isObject()) {
        String msg = err.path("data").path("message").asText("");
        if (msg.isBlank()) {
          msg = err.path("message").asText("");
        }
        if (msg.isBlank()) {
          msg = err.toString();
          if (msg.length() > 300) {
            msg = msg.substring(0, 300);
          }
        }
        lines.add("[ERROR] " + clip(msg, 300));
        continue;
      }
      JsonNode part = ev.has("part") && ev.get("part").isObject() ? ev.get("part") : ev;
      String ptype = textOr(part, "type", textOr(ev, "type", ""));
      if (TEXT_TAGS.containsKey(ptype)) {
        String txt = textOr(part, "text", textOr(part, "content", ""));
        if (!txt.isBlank()) {
          lines.add("[" + TEXT_TAGS.get(ptype) + "] " + clip(txt, obsTrunc));
        }
      } else if (isToolType(ptype)) {
        String name =
            firstNonBlank(
                textOr(part, "tool", ""),
                textOr(ev, "tool", ""),
                textOr(part, "name", ""),
                textOr(part.path("toolInvocation"), "toolName", ""),
                "tool");
        JsonNode state =
            part.has("state") && part.get("state").isObject() ? part.get("state") : part;
        String status = textOr(state, "status", "");
        JsonNode tin =
            firstPresent(state.get("input"), part.get("input"), part.get("args"));
        JsonNode tout =
            firstPresent(state.get("output"), state.get("result"), part.get("output"));
        String args = "";
        if (tin != null && !tin.isNull()) {
          try {
            args = JSON.writeValueAsString(tin);
          } catch (Exception e) {
            args = String.valueOf(tin);
          }
        }
        lines.add("[ACTION] " + name + "(" + clip(args, argsCap) + ")");
        if ("error".equals(status)) {
          lines.add("[OBSERVATION ERROR] " + clip(textOr(state, "error", ""), obsTrunc));
        } else if (tout != null && !tout.isNull()) {
          String out =
              tout.isTextual() ? tout.asText() : tout.toString();
          lines.add("[OBSERVATION] " + clip(out, obsTrunc));
        }
      }
    }
    String rendered = String.join("\n", lines).strip();
    if (rendered.isEmpty()) {
      return "(no parseable events in trajectory)";
    }
    if (rendered.length() > maxChars) {
      int dropped = rendered.length() - maxChars;
      rendered = rendered.substring(0, maxChars) + "\n" + String.format(TRUNC_FMT, dropped);
    }
    return rendered;
  }

  private static boolean isToolType(String ptype) {
    return "tool".equals(ptype)
        || "tool_use".equals(ptype)
        || "tool_call".equals(ptype)
        || "tool-invocation".equals(ptype);
  }

  private static String textOr(JsonNode n, String field, String fallback) {
    if (n == null || n.isMissingNode() || n.isNull()) {
      return fallback;
    }
    JsonNode v = n.get(field);
    if (v == null || v.isNull() || !v.isValueNode()) {
      return fallback;
    }
    String s = v.asText("");
    return s.isEmpty() ? fallback : s;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return "";
  }

  private static JsonNode firstPresent(JsonNode... nodes) {
    for (JsonNode n : nodes) {
      if (n != null && !n.isNull() && !n.isMissingNode()) {
        return n;
      }
    }
    return null;
  }
}
