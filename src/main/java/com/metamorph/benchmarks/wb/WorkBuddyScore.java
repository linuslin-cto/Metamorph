package com.metamorph.benchmarks.wb;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Offline WorkBuddy verifier score contract. Ports {@code score_from_payload} precedence: reward
 * &gt; overall &gt; test_pass_rate &gt; tests_passed/tests_total. Values outside [0,1] are rejected.
 */
public final class WorkBuddyScore {
  private WorkBuddyScore() {}

  public static Double scoreFromPayload(Map<String, Object> payload) {
    if (payload == null) {
      return null;
    }
    for (String key : new String[] {"reward", "overall", "test_pass_rate"}) {
      Double sc = unitFloat(payload.get(key));
      if (sc != null) {
        return sc;
      }
    }
    Integer passed = intOrNull(payload.get("tests_passed"));
    Integer total = intOrNull(payload.get("tests_total"));
    if (passed != null && total != null && total > 0) {
      return Math.max(0.0, Math.min(passed / (double) total, 1.0));
    }
    return null;
  }

  /** Cacheable judge row; {@code null} when the payload is missing/unparseable (never a fake zero). */
  public static Map<String, Object> toScoreRow(Map<String, Object> payload) {
    Double sc = scoreFromPayload(payload);
    if (sc == null) {
      return null;
    }
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("n_passed", (int) Math.round(sc * 100));
    row.put("n_criteria", 100);
    row.put("all_pass", sc >= 1.0);
    row.put("raw_score", sc);
    return row;
  }

  static Double unitFloat(Object v) {
    if (v == null) {
      return null;
    }
    double d;
    if (v instanceof Number n) {
      d = n.doubleValue();
    } else {
      try {
        d = Double.parseDouble(String.valueOf(v).strip());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    if (d < 0.0 || d > 1.0) {
      return null;
    }
    return d;
  }

  static Integer intOrNull(Object v) {
    if (v == null) {
      return null;
    }
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(v).strip());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
