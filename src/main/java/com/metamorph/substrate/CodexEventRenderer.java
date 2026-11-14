package com.metamorph.substrate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders Codex exec/rollout JSONL into the shared trajectory grammar. Ports {@code
 * upstream.substrate.codex.parse_items} / {@code render_events}.
 */
public final class CodexEventRenderer {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String TRUNC_FMT = "…(+%d chars — render truncation)";
  private static final String AGENTS_MD_TITLE = "# AGENTS.md instructions";

  private CodexEventRenderer() {}

  public static String clip(Object value, int n) {
    String s;
    if (value == null) {
      s = "";
    } else if (value instanceof JsonNode node) {
      if (node.isNull() || node.isMissingNode()) {
        s = "";
      } else if (node.isTextual() || node.isNumber() || node.isBoolean()) {
        s = node.asText();
      } else {
        s = node.toString();
      }
    } else {
      s = String.valueOf(value);
    }
    s = s.replace("\u0000", "");
    if (n <= 0 || s.length() <= n) {
      return s;
    }
    return s.substring(0, n) + String.format(TRUNC_FMT, s.length() - n);
  }

  public static String textOf(JsonNode obj) {
    if (obj == null || obj.isNull() || obj.isMissingNode()) {
      return "";
    }
    if (obj.isTextual()) {
      return obj.asText();
    }
    if (obj.isObject()) {
      for (String k : List.of("text", "message", "content", "summary")) {
        JsonNode v = obj.get(k);
        if (v != null && v.isTextual() && !v.asText().isBlank()) {
          return v.asText();
        }
        if (v != null && (v.isArray() || v.isObject())) {
          String t = textOf(v);
          if (!t.isBlank()) {
            return t;
          }
        }
      }
      return "";
    }
    if (obj.isArray()) {
      List<String> parts = new ArrayList<>();
      for (JsonNode x : obj) {
        String t = textOf(x);
        if (!t.isBlank()) {
          parts.add(t);
        }
      }
      return String.join("\n", parts);
    }
    return "";
  }

  /** Stream-order events; thread-items deduped by {@code item.id} (last state, first position). */
  public static List<JsonNode> parseItems(Path path) {
    String raw;
    try {
      raw = Files.readString(path, StandardCharsets.UTF_8).replace("\u0000", "");
    } catch (Exception e) {
      ObjectNode err = JSON.createObjectNode();
      err.put("type", "_read_error");
      err.put("message", e.getMessage() == null ? e.toString() : e.getMessage());
      return List.of(err);
    }
    List<JsonNode> events = new ArrayList<>();
    Map<String, Integer> byId = new LinkedHashMap<>();
    for (String ln : raw.split("\\R")) {
      ln = ln.strip();
      if (!(ln.startsWith("{\"type\":") || ln.startsWith("{\"timestamp\":"))) {
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
      JsonNode item = ev.get("item");
      String iid =
          item != null && item.isObject() && item.has("id") ? item.path("id").asText(null) : null;
      if (iid != null) {
        Integer prev = byId.get(iid);
        if (prev != null) {
          events.set(prev, ev);
          continue;
        }
        byId.put(iid, events.size());
      }
      events.add(ev);
    }
    return events;
  }

  public static String renderEvents(Path path, int obsTrunc, int maxChars) {
    return renderEvents(path, obsTrunc, maxChars, 2000);
  }

  public static String renderEvents(Path path, int obsTrunc, int maxChars, int argsTrunc) {
    List<String> lines = new ArrayList<>();
    for (JsonNode ev : parseItems(path)) {
      String et = ev.path("type").asText("");
      if ("_read_error".equals(et)) {
        return "(failed to read trajectory: " + ev.path("message").asText("") + ")";
      }
      JsonNode item = ev.get("item");
      if (item != null && item.isObject()) {
        renderItem(item, lines, obsTrunc, argsTrunc);
        continue;
      }
      JsonNode payload = ev.get("payload");
      if (payload != null && payload.isObject()) {
        renderRolloutPayload(payload, lines, obsTrunc, argsTrunc);
        continue;
      }
      if ("turn.completed".equals(et)) {
        JsonNode usage = ev.get("usage");
        if (usage != null && usage.isObject()) {
          lines.add(
              String.format(
                  "[HARNESS] turn completed | tokens in=%s cached=%s out=%s reasoning=%s",
                  usage.path("input_tokens"),
                  usage.path("cached_input_tokens"),
                  usage.path("output_tokens"),
                  usage.path("reasoning_output_tokens")));
        }
        continue;
      }
      if ("turn.failed".equals(et)
          || "error".equals(et)
          || (ev.get("error") != null && !ev.get("error").isNull())) {
        String msg = textOf(ev.get("error"));
        if (msg.isBlank()) {
          msg = textOf(ev);
        }
        if (msg.isBlank()) {
          msg = ev.toString();
          if (msg.length() > 300) {
            msg = msg.substring(0, 300);
          }
        }
        lines.add("[ERROR] " + clip(msg, 1000));
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

  private static void renderItem(JsonNode item, List<String> lines, int obsTrunc, int argsTrunc) {
    String itype = item.path("type").asText("");
    if ("command_execution".equals(itype)) {
      lines.add("[ACTION] shell: " + clip(item.path("command").asText(""), argsTrunc));
      String status = item.path("status").asText("");
      JsonNode exitNode = item.get("exit_code");
      Integer exitCode = exitNode != null && exitNode.isNumber() ? exitNode.asInt() : null;
      boolean failed = "failed".equals(status) || (exitCode != null && exitCode != 0);
      String obs = clip(item.path("aggregated_output").asText(""), obsTrunc);
      if ("in_progress".equals(status) && obs.isBlank()) {
        obs = "(no output captured — command still in_progress at end of stream)";
      }
      String suffix = (exitCode == null || exitCode == 0) ? "" : " | exit_code=" + exitCode;
      lines.add("[OBSERVATION" + (failed ? " ERROR" : "") + "] " + obs + suffix);
      return;
    }
    if ("agent_message".equals(itype)) {
      String txt = item.path("text").asText("");
      if (!txt.isBlank()) {
        lines.add("[ASSISTANT] " + clip(txt, obsTrunc));
      }
      return;
    }
    if ("reasoning".equals(itype)) {
      String txt = textOf(item);
      if (!txt.isBlank()) {
        lines.add("[REASONING] " + clip(txt, obsTrunc));
      }
      return;
    }
    if ("todo_list".equals(itype)) {
      JsonNode rows = item.get("items");
      if (rows instanceof ArrayNode arr && !arr.isEmpty()) {
        List<String> plan = new ArrayList<>();
        for (JsonNode r : arr) {
          if (!r.isObject()) {
            continue;
          }
          boolean done = r.path("completed").asBoolean(false);
          plan.add("[" + (done ? "x" : " ") + "] " + clip(r.path("text").asText(""), 200));
        }
        if (!plan.isEmpty()) {
          lines.add("[ASSISTANT] plan: " + String.join(" | ", plan));
        }
      }
      return;
    }
    if ("error".equals(itype)) {
      lines.add("[HARNESS] " + clip(item.path("message").asText(""), 1000));
      return;
    }
    if ("user_message".equals(itype) || "message".equals(itype)) {
      emitUser(lines, textOf(item), obsTrunc);
      return;
    }
    String txt = textOf(item);
    if (!txt.isBlank()) {
      lines.add("[HARNESS] " + (itype.isBlank() ? "item" : itype) + ": " + clip(txt, obsTrunc));
    }
  }

  private static void renderRolloutPayload(
      JsonNode payload, List<String> lines, int obsTrunc, int argsTrunc) {
    String ptype = payload.path("type").asText("");
    if ("function_call".equals(ptype)) {
      JsonNode args = payload.get("arguments");
      if (args != null && args.isTextual()) {
        try {
          args = JSON.readTree(args.asText());
        } catch (Exception ignored) {
          // keep string
        }
      }
      Object cmd =
          args != null && args.isObject() && args.has("cmd")
              ? args.get("cmd")
              : args;
      String name = payload.path("name").asText("shell");
      if (name.isBlank()) {
        name = "shell";
      }
      lines.add("[ACTION] " + name + ": " + clip(cmd == null ? args : cmd, argsTrunc));
    } else if ("function_call_output".equals(ptype)) {
      JsonNode out = payload.get("output");
      String t = textOf(out);
      if (t.isBlank() && out != null) {
        t = out.isTextual() ? out.asText() : out.toString();
      }
      lines.add("[OBSERVATION] " + clip(t, obsTrunc));
    } else if ("message".equals(ptype)) {
      String role = payload.path("role").asText("");
      String txt = textOf(payload);
      if ("user".equals(role)) {
        emitUser(lines, txt, obsTrunc);
      } else if ("assistant".equals(role)) {
        emitIf(lines, "ASSISTANT", txt, obsTrunc);
      } else {
        emitIf(lines, "HARNESS " + (role.isBlank() ? "message" : role), txt, obsTrunc);
      }
    } else if ("user_message".equals(ptype)) {
      emitUser(lines, textOf(payload), obsTrunc);
    } else if ("agent_message".equals(ptype)) {
      emitIf(lines, "ASSISTANT", textOf(payload), obsTrunc);
    } else if ("reasoning".equals(ptype)) {
      emitIf(lines, "REASONING", textOf(payload), obsTrunc);
    }
  }

  private static void emitIf(List<String> lines, String tag, String txt, int trunc) {
    if (txt != null && !txt.isBlank()) {
      emit(lines, "[" + tag + "] " + clip(txt, trunc));
    }
  }

  private static void emitUser(List<String> lines, String txt, int obsTrunc) {
    if (txt == null) {
      return;
    }
    String stripped = txt.stripLeading();
    if (stripped.startsWith(AGENTS_MD_TITLE)) {
      emit(lines, "[HARNESS AGENTS.md] " + clip(txt, obsTrunc));
    } else {
      emitIf(lines, "USER", txt, obsTrunc);
    }
  }

  private static void emit(List<String> lines, String line) {
    if (lines.isEmpty() || !lines.get(lines.size() - 1).equals(line)) {
      lines.add(line);
    }
  }
}
