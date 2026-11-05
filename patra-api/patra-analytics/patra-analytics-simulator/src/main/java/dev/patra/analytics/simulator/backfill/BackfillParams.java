package dev.patra.analytics.simulator.backfill;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

/// 灌历史的参数（spec §8）。
///
/// @param run 运行号
/// @param seed 种子
/// @param events 事件总数（不含 1% 重发）
/// @param days 铺几天（到 `endDay` 为止往前）
/// @param endDay 最后一天（北京时间，含）；命令行默认昨天，进参数指纹：试灌与正式灌跨过午夜也是同一个窗口
/// @param onlyDays 只写最早的几天；`null` 全写
/// @param fromDay 从这天起才写，之前的天只生成（续跑）；`null` 从头写
/// @param articles 文献池文件
/// @param model 模型参数文件；`null` 用内置默认值
public record BackfillParams(
    int run,
    long seed,
    long events,
    int days,
    LocalDate endDay,
    Integer onlyDays,
    LocalDate fromDay,
    Path articles,
    Path model) {

  private static final String BUILTIN_MODEL = "/simulator/traffic-model.json";

  /// 参数指纹：种子、总数、天数、最后一天、模型与文献池文件内容的 SHA-256 前 8 位十六进制；不含 `onlyDays`、`fromDay`，
  /// 所以试灌与正式灌、续跑的同一块令牌相同（内容也相同），而改了参数令牌就变，不会被当成重复整块丢掉。
  ///
  /// @return 指纹
  public String fingerprint() {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(
          (seed + "|" + events + "|" + days + "|" + endDay + "|").getBytes(StandardCharsets.UTF_8));
      digest.update(modelBytes());
      digest.update(Files.readAllBytes(articles));
      return HexFormat.of().formatHex(digest.digest()).substring(0, 8);
    } catch (IOException e) {
      throw new UncheckedIOException("算参数指纹失败", e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /// 模型参数文件的内容。
  ///
  /// @return 字节
  private byte[] modelBytes() throws IOException {
    if (model != null) {
      return Files.readAllBytes(model);
    }
    try (InputStream in = BackfillParams.class.getResourceAsStream(BUILTIN_MODEL)) {
      return in.readAllBytes();
    }
  }
}
