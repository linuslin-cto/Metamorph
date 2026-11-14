package com.metamorph.core.selection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Selection {
  public String taskId;
  public String mode;
  public int k;
  public String chosenId = "";
  public String chosenLabel = "";
  public String chosenRunId = "";
  public String chosenRunDir = "";
  public boolean chosenIsBase;
  public String why = "";
  public Map<String, String> idMap = new LinkedHashMap<>();
  public List<String> dropped = new ArrayList<>();
  public int nCandidates;
  public boolean ok;
  public boolean fallback;
  public String error = "";
  public double usd;
  public Map<String, Object> experience = new LinkedHashMap<>();
  public boolean trivial;
  public String evidence = "deliverable";
  public String harnessFingerprint = "";

  public Map<String, Object> toMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("task_id", taskId);
    m.put("mode", mode);
    m.put("k", k);
    m.put("chosen_id", chosenId);
    m.put("chosen_label", chosenLabel);
    m.put("chosen_run_id", chosenRunId);
    m.put("chosen_run_dir", chosenRunDir);
    m.put("chosen_is_base", chosenIsBase);
    m.put("why", why);
    m.put("id_map", idMap);
    m.put("dropped", dropped);
    m.put("n_candidates", nCandidates);
    m.put("ok", ok);
    m.put("fallback", fallback);
    m.put("error", error);
    m.put("usd", usd);
    m.put("experience", experience);
    m.put("trivial", trivial);
    m.put("evidence", evidence);
    m.put("harness_fingerprint", harnessFingerprint);
    return m;
  }
}
