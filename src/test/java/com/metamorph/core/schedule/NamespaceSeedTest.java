package com.metamorph.core.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NamespaceSeedTest {
  @Test
  void seedAndTagAppearInNamespaceString() {
    assertEquals("lab-oc-agg1", Schedule.ns("lab", "oc", "agg", 1, null, ""));
    assertEquals("lab-oc-agg1-s42", Schedule.ns("lab", "oc", "agg", 1, 42, ""));
    assertEquals("lab-oc-agg1-s-3", Schedule.ns("lab", "oc", "agg", 1, -3, ""));
    assertEquals("lab-oc-agg1-s42-exp", Schedule.ns("lab", "oc", "agg", 1, 42, "exp"));
    assertEquals("demo-oc-seq2", new Namespace("demo", "oc", "seq", 2).toString());
  }

  @Test
  void tagMustNotSpellSeedSegmentOrLeadWithDash() {
    assertThrows(IllegalArgumentException.class, () -> Schedule.validateTag("s7"));
    assertThrows(IllegalArgumentException.class, () -> Schedule.validateTag("s42-extra"));
    assertThrows(IllegalArgumentException.class, () -> Schedule.validateTag("-bad"));
    assertThrows(IllegalArgumentException.class, () -> Schedule.ns("lab", "oc", "agg", 1, 1, "s9"));
    Schedule.validateTag("exp.v2");
    Schedule.validateTag("run_1");
  }

  @Test
  void taskDirUsesSeededNamespace(@TempDir Path root) {
    Namespace ns = new Namespace("lab", "oc", "agg", 1, 7, "t");
    Path dir = ns.taskDir(root, "contracts/task-1");
    assertTrue(dir.toString().replace('\\', '/').contains("lab-oc-agg1-s7-t"));
    assertTrue(dir.getFileName().toString().equals("contracts__task-1"));
  }

  @Test
  void runIdIncludesNamespaceTaskAndLabel() {
    Namespace ns = new Namespace("demo", "oc", "agg", 2, 3, "");
    assertEquals("demo-oc-agg2-s3/ledger-1/base1", ns.runId("ledger-1", "base1"));
  }
}
