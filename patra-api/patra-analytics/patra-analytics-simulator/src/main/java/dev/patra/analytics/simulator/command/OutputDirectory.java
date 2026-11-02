package dev.patra.analytics.simulator.command;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/// 每次运行的输出目录 `{out}/run-NNN`：参数、真值、摘要、每秒统计、对账结果都在这里。
public final class OutputDirectory {

  private OutputDirectory() {}

  /// 建好目录。
  ///
  /// @param root 输出根目录
  /// @param run 运行号
  /// @return 目录
  public static Path prepare(Path root, int run) {
    Path dir = root.resolve("run-%03d".formatted(run));
    try {
      Files.createDirectories(dir);
    } catch (IOException e) {
      throw new UncheckedIOException("建输出目录失败：" + dir, e);
    }
    return dir;
  }

  /// 写 `params.json`（不含密码）。
  ///
  /// @param dir 目录
  /// @param params 参数
  /// @param jsonMapper JSON
  public static void writeParams(Path dir, Map<String, Object> params, JsonMapper jsonMapper) {
    try {
      Files.writeString(
          dir.resolve("params.json"),
          jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(params));
    } catch (IOException e) {
      throw new UncheckedIOException("写 params.json 失败", e);
    }
  }
}
