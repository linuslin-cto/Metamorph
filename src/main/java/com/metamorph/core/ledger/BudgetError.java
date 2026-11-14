package com.metamorph.core.ledger;

/** The ledger did not land on the budget identity. */
public class BudgetError extends AssertionError {
  public BudgetError(String message) {
    super(message);
  }
}
