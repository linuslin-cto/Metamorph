package com.metamorph.core.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LedgerTest {
  @Test
  void budgetIdentityForK2() {
    Ledger ledger = new Ledger("agg", "t1", 2);
    for (int i = 1; i <= 2; i++) {
      ledger.rollout("base" + i);
    }
    for (String lbl : new String[] {"inv1", "dom1", "inv2", "dom2"}) {
      ledger.session(lbl);
    }
    for (String lbl : new String[] {"inv1", "dom1", "inv2", "dom2"}) {
      ledger.rollout(lbl);
    }
    for (String lbl : new String[] {"agg1", "agg2"}) {
      ledger.session(lbl);
    }
    for (String lbl : new String[] {"agg1", "agg2"}) {
      ledger.rollout(lbl);
    }
    ledger.assertIdentity();
    assertEquals(8, ledger.R());
    assertEquals(6, ledger.P());
  }

  @Test
  void duplicateLabelFails() {
    Ledger ledger = new Ledger("agg", "t1", 1);
    ledger.rollout("base1");
    ledger.rollout("base1");
    assertThrows(BudgetError.class, ledger::assertIdentity);
  }

  @Test
  void duplicateLabelMessageExplainsSilentOverwrite() {
    Ledger ledger = new Ledger("seq", "task-x", 1);
    ledger.rollout("base1");
    ledger.session("inv1");
    ledger.rollout("inv1");
    ledger.session("dom1");
    ledger.rollout("dom1");
    ledger.session("c1");
    ledger.rollout("c1");
    // Extra duplicate session with an otherwise-complete budget.
    ledger.session("inv1");
    BudgetError err = assertThrows(BudgetError.class, ledger::assertIdentity);
    String msg = err.getMessage();
    assertTrue(msg.contains("DUPLICATE labels"));
    assertTrue(msg.contains("inv1"));
    assertTrue(msg.contains("silently overwrote"));
    assertTrue(msg.contains("seq-1 task-x"));
  }
}
