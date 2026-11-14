package com.metamorph.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "metamorph",
    mixinStandardHelpOptions = true,
    version = "Metamorph 0.1.0",
    description = "Metamorph — recursive agent harnessing for an Open World",
    subcommands = {
      DemoCommand.class,
      CheckCommand.class,
      JudgeCommand.class,
      ReportCommand.class,
      BridgeCommand.class,
      SetupCommand.class,
      EvalCommand.class
    })
public final class MetamorphMain implements Runnable {
  @Override
  public void run() {
    new CommandLine(this).usage(System.out);
  }

  public static void main(String[] args) {
    int code = new CommandLine(new MetamorphMain()).execute(args);
    System.exit(code);
  }
}
