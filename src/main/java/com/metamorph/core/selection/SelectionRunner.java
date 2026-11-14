package com.metamorph.core.selection;

import com.metamorph.core.context.ContextBuilder;
import com.metamorph.core.prompts.Prompts;
import com.metamorph.core.proposer.Proposer;
import com.metamorph.core.proposer.Reply;
import com.metamorph.core.spec.BenchmarkAdapter;
import com.metamorph.core.spec.RunView;
import com.metamorph.core.spec.Specs;
import com.metamorph.core.runmark.RunMark;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Choose an existing anonymized final output. */
public final class SelectionRunner {
  private static final Pattern CHOICE =
      Pattern.compile("(?s)```choice\\s*\\n?\\s*([A-Za-z0-9_-]+)\\s*\\n?\\s*```");
  private static final Pattern WHY = Pattern.compile("(?s)<why>(.*?)</why>");
  private static final Pattern EXP = Pattern.compile("(?s)<experience>(.*?)</experience>");

  private SelectionRunner() {}

  public static Map<String, RunView> selectionPool(
      String mode, int k, Map<String, RunView> rollouts, List<RunView> base) {
    List<String> labels = new ArrayList<>();
    if ("agg".equals(mode)) {
      for (int i = 1; i <= k; i++) {
        labels.add("agg" + i);
      }
    } else {
      for (int t = 1; t <= k; t++) {
        labels.add("c" + t);
      }
    }
    Map<String, RunView> out = new LinkedHashMap<>();
    for (String lbl : labels) {
      if (rollouts.containsKey(lbl)) {
        out.put(lbl, rollouts.get(lbl));
      }
    }
    return out;
  }

  public static Map<String, String> assignIds(List<String> labels, String taskId, int k, String mode)
      throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-1");
    byte[] dig = md.digest((taskId + "|" + k + "|" + mode).getBytes(StandardCharsets.UTF_8));
    long seed = 0;
    for (int i = 0; i < 6; i++) {
      seed = (seed << 8) | (dig[i] & 0xff);
    }
    List<String> ordered = new ArrayList<>(labels);
    ordered.sort(String::compareTo);
    Random rng = new Random(seed);
    for (int i = ordered.size() - 1; i > 0; i--) {
      int j = rng.nextInt(i + 1);
      String tmp = ordered.get(i);
      ordered.set(i, ordered.get(j));
      ordered.set(j, tmp);
    }
    Map<String, String> out = new LinkedHashMap<>();
    for (int i = 0; i < ordered.size(); i++) {
      out.put(anonId(i), ordered.get(i));
    }
    return out;
  }

  private static String anonId(int index) {
    String out = "";
    int n = index + 1;
    while (n > 0) {
      int rem = (n - 1) % 26;
      out = (char) ('A' + rem) + out;
      n = (n - 1) / 26;
    }
    return out;
  }

  public static Selection runSelection(
      String taskId,
      String mode,
      int k,
      Map<String, RunView> rollouts,
      List<RunView> base,
      BenchmarkAdapter adapter,
      Proposer prop,
      Path workDir,
      Path libraryDir)
      throws Exception {
    Selection sel = new Selection();
    sel.taskId = taskId;
    sel.mode = mode;
    sel.k = k;
    Map<String, RunView> candidates = selectionPool(mode, k, rollouts, base);
    Map<String, RunView> pool = new LinkedHashMap<>();
    Map<String, String> artifacts = new LinkedHashMap<>();
    for (var e : candidates.entrySet()) {
      RunView rv = e.getValue();
      if (!(rv.ok() && rv.delivered())) {
        sel.dropped.add(e.getKey());
        continue;
      }
      String rendered = adapter.deliverableText(rv.runDir());
      if (rendered == null || rendered.isBlank()) {
        sel.dropped.add(e.getKey());
        continue;
      }
      pool.put(e.getKey(), rv);
      artifacts.put(e.getKey(), rendered);
    }
    sel.nCandidates = pool.size();
    if (pool.isEmpty()) {
      sel.error = "no deliverable candidates";
      return sel;
    }
    if (pool.size() == 1) {
      var only = pool.entrySet().iterator().next();
      fillChoice(sel, "A", only.getKey(), only.getValue(), workDir);
      sel.trivial = true;
      sel.ok = true;
      sel.why = "only one candidate delivered";
      return sel;
    }

    Map<String, String> idMap = assignIds(new ArrayList<>(pool.keySet()), taskId, k, mode);
    sel.idMap = idMap;
    Path ctx = workDir.resolve("context").resolve("select." + mode);
    if (Files.exists(ctx)) {
      deleteRecursive(ctx);
    }
    Files.createDirectories(ctx);
    Files.writeString(ctx.resolve("task.md"), adapter.taskPrompt(taskId));
    ContextBuilder.copySourcePublic(adapter, taskId, ctx);
    Path candRoot = ctx.resolve("candidates");
    Files.createDirectories(candRoot);
    for (var e : idMap.entrySet()) {
      String anon = e.getKey();
      String label = e.getValue();
      Files.writeString(candRoot.resolve(anon + ".md"), artifacts.get(label));
      Path assets = candRoot.resolve(anon);
      Files.createDirectories(assets);
      for (var f : adapter.deliverableFiles(pool.get(label).runDir()).entrySet()) {
        Files.writeString(assets.resolve(f.getKey()), f.getValue());
      }
    }
    String mission = Prompts.select(Specs.resolvePrompts(adapter, taskId), pool.size(), "finals");
    Reply reply = prop.call(mission, ctx, "select." + mode + k, null, 2);
    Matcher cm = CHOICE.matcher(reply.text());
    if (!cm.find()) {
      sel.error = "no ```choice``` fence";
      sel.fallback = true;
      var first = pool.entrySet().iterator().next();
      fillChoice(sel, "A", first.getKey(), first.getValue(), workDir);
      sel.ok = true;
      return sel;
    }
    String chosenId = cm.group(1).strip();
    String label = idMap.get(chosenId);
    if (label == null || !pool.containsKey(label)) {
      sel.error = "unknown choice " + chosenId;
      sel.fallback = true;
      var first = pool.entrySet().iterator().next();
      fillChoice(sel, "A", first.getKey(), first.getValue(), workDir);
      sel.ok = true;
      return sel;
    }
    Matcher wm = WHY.matcher(reply.text());
    sel.why = wm.find() ? wm.group(1).strip() : "";
    fillChoice(sel, chosenId, label, pool.get(label), workDir);
    sel.ok = true;
    sel.usd = reply.costUsd();

    // reflection / experience (best-effort)
    try {
      Path expCtx = workDir.resolve("context").resolve("experience." + mode);
      if (Files.exists(expCtx)) {
        deleteRecursive(expCtx);
      }
      Files.createDirectories(expCtx);
      Path harness = workDir.resolve("harnesses").resolve(label);
      Path baseH = workDir.resolve("harnesses").resolve("base");
      if (Files.isDirectory(harness)) {
        copyDir(harness, expCtx.resolve("harness"));
      }
      if (Files.isDirectory(baseH)) {
        copyDir(baseH, expCtx.resolve("base_harness"));
      }
      ContextBuilder.copySourcePublic(adapter, taskId, expCtx);
      Reply exp =
          prop.call(
              Prompts.experience(Specs.resolvePrompts(adapter, taskId)),
              expCtx,
              "experience." + mode + ".a1",
              null,
              2);
      if (!"NO_ENTRY".equals(exp.text().strip())) {
        Matcher em = EXP.matcher(exp.text());
        String body = em.find() ? em.group(1).strip() : exp.text().strip();
        sel.experience.put("raw", body);
        sel.experience.put("text", exp.text());
        putField(sel.experience, body, "cue");
        putField(sel.experience, body, "lesson");
        putField(sel.experience, body, "evidence");
        putField(sel.experience, body, "confidence");
        Matcher edits = Pattern.compile("(?s)<edits>(.*?)</edits>").matcher(exp.text());
        if (edits.find()) {
          sel.experience.put("edits", edits.group(1).strip());
        }
      }
    } catch (Exception ignored) {
    }
    return sel;
  }

  private static void putField(Map<String, Object> experience, String body, String field) {
    Matcher m =
        Pattern.compile("(?im)^\\s*" + Pattern.quote(field) + "\\s*:\\s*(.+)$").matcher(body);
    if (m.find()) {
      experience.put(field, m.group(1).strip());
    }
  }

  private static void fillChoice(
      Selection sel, String id, String label, RunView rv, Path workDir) throws Exception {
    sel.chosenId = id;
    sel.chosenLabel = label;
    sel.chosenRunId = rv.runId();
    sel.chosenRunDir = rv.runDir().toString();
    sel.chosenIsBase = label.startsWith("base");
    Path harness = workDir.resolve("harnesses").resolve(label);
    if (Files.isDirectory(harness)) {
      sel.harnessFingerprint = RunMark.fingerprint(harness);
    }
  }

  private static void deleteRecursive(Path root) throws Exception {
    Files.walk(root)
        .sorted((a, b) -> b.compareTo(a))
        .forEach(
            p -> {
              try {
                Files.deleteIfExists(p);
              } catch (Exception ignored) {
              }
            });
  }

  private static void copyDir(Path from, Path to) throws Exception {
    Files.walk(from)
        .forEach(
            p -> {
              try {
                Path t = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                  Files.createDirectories(t);
                } else {
                  Files.createDirectories(t.getParent());
                  Files.copy(p, t);
                }
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }
}
