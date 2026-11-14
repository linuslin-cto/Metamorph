package com.metamorph.core.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StreamOrderTest {
  private Map<String, List<String>> sample() {
    Map<String, List<String>> m = new LinkedHashMap<>();
    m.put("b", List.of("t2", "t1"));
    m.put("a", List.of("x"));
    m.put("c", List.of("u", "v", "w"));
    return m;
  }

  @Test
  void nullSeedUsesReferenceOrder() {
    List<Map.Entry<String, String>> got =
        Schedule.streamOrder(sample(), null, List.of("c", "a", "b"));
    assertEquals(
        List.of(
            Map.entry("c", "u"),
            Map.entry("c", "v"),
            Map.entry("c", "w"),
            Map.entry("a", "x"),
            Map.entry("b", "t1"),
            Map.entry("b", "t2")),
        got);
  }

  @Test
  void nullSeedRejectsMissingReferenceDomains() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Schedule.streamOrder(sample(), null, List.of("a", "b")));
  }

  @Test
  void sameSeedIsDeterministic() {
    var a = Schedule.streamOrder(sample(), 7, List.of());
    var b = Schedule.streamOrder(sample(), 7, List.of());
    assertEquals(a, b);
  }

  @Test
  void differentSeedsCanDiffer() {
    var a = Schedule.streamOrder(sample(), 1, List.of());
    var b = Schedule.streamOrder(sample(), 2, List.of());
    // Extremely unlikely to be identical for this set
    assertNotEquals(a, b);
  }

  @Test
  void droppingADomainLeavesOthersUnchangedUnderSeed() {
    Map<String, List<String>> full = sample();
    Map<String, List<String>> withoutC = new LinkedHashMap<>(full);
    withoutC.remove("c");
    var fullStream = Schedule.streamOrder(full, 99, List.of());
    var partial = Schedule.streamOrder(withoutC, 99, List.of());
    List<Map.Entry<String, String>> filtered =
        fullStream.stream().filter(e -> !"c".equals(e.getKey())).toList();
    assertEquals(partial, filtered);
  }

  @Test
  void defaultReferenceDomainOrderByCountThenName() {
    assertEquals(List.of("c", "b", "a"), Schedule.defaultReferenceDomainOrder(sample()));
  }
}
