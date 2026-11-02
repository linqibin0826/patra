package dev.patra.analytics.simulator.command;

import dev.patra.analytics.simulator.event.SyntheticIds;
import java.nio.file.Path;
import picocli.CommandLine.Option;

/// 各命令共用的运行号、种子、输出目录。
public final class RunOptions {

  @Option(names = "--run", required = true, description = "运行号，1～899（进设备 ID 与用户 ID，对账据此只取本次运行）")
  int run;

  @Option(names = "--seed", defaultValue = "42", description = "种子，默认 ${DEFAULT-VALUE}")
  long seed;

  @Option(
      names = "--out",
      defaultValue = "simulator-out",
      description = "输出根目录，默认 ${DEFAULT-VALUE}")
  Path out;

  /// 校验运行号（在连接任何外部服务之前）。
  ///
  /// @throws IllegalArgumentException 运行号越界
  void validate() {
    new SyntheticIds(run);
  }

  /// 本次运行的输出目录 `{out}/run-NNN`。
  ///
  /// @return 目录
  Path runDirectory() {
    return OutputDirectory.prepare(out, run);
  }
}
