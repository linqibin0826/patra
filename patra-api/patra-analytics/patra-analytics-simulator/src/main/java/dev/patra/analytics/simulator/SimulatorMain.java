package dev.patra.analytics.simulator;

import dev.patra.analytics.simulator.backfill.DiskFullException;
import dev.patra.analytics.simulator.command.BackfillCommand;
import dev.patra.analytics.simulator.command.LiveCommand;
import dev.patra.analytics.simulator.command.ReconcileCommand;
import dev.patra.analytics.simulator.command.ReportCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/// 流量模拟器入口（PAP-171）。退出码：成功 0、对账未通过 1、参数错误 2、ClickHouse 磁盘到红线 3、其他错误 4。
@Command(
    name = "simulator",
    mixinStandardHelpOptions = true,
    description = "Patra 行为数据流量模拟器：灌历史、实时压测、对账、报告",
    subcommands = {
      BackfillCommand.class,
      LiveCommand.class,
      ReconcileCommand.class,
      ReportCommand.class
    })
public final class SimulatorMain {

  /// 参数错误
  static final int USAGE = 2;

  /// 磁盘到红线
  static final int DISK_FULL = 3;

  /// 其他错误
  static final int FAILED = 4;

  /// 入口。
  ///
  /// @param args 参数
  public static void main(String[] args) {
    System.exit(commandLine().execute(args));
  }

  /// 装配命令行（执行异常映射到退出码，消息打到错误输出）。
  ///
  /// @return 命令行
  public static CommandLine commandLine() {
    return new CommandLine(new SimulatorMain())
        .setExecutionExceptionHandler(
            (error, command, parseResult) -> {
              command.getErr().println(error.getMessage());
              if (error instanceof DiskFullException) {
                return DISK_FULL;
              }
              if (error instanceof IllegalArgumentException) {
                return USAGE;
              }
              error.printStackTrace(command.getErr());
              return FAILED;
            });
  }
}
