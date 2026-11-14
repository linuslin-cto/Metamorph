package com.metamorph.core.proposer;

public class ProposerError extends RuntimeException {
  private final int attempts;
  private final double costUsd;
  private final Reply failedReply;

  public ProposerError(String message) {
    this(message, 0, 0.0, null);
  }

  public ProposerError(String message, int attempts, double costUsd) {
    this(message, attempts, costUsd, null);
  }

  public ProposerError(String message, int attempts, double costUsd, Reply failedReply) {
    super(message);
    this.attempts = attempts;
    this.costUsd = costUsd;
    this.failedReply = failedReply;
  }

  public int attempts() {
    return attempts;
  }

  public double costUsd() {
    return costUsd;
  }

  public Reply failedReply() {
    return failedReply;
  }
}
