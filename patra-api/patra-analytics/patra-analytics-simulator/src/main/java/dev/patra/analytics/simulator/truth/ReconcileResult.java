package dev.patra.analytics.simulator.truth;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.Builder;
import tools.jackson.databind.json.JsonMapper;

/// 对账结果 `reconcile.json`（spec §7.3）。
///
/// @param truthViewers 真值的看过人数之和（各天各篇）
/// @param actualViewers ClickHouse 算出的看过人数之和
/// @param viewersError 看过的误差：Σ|ClickHouse − 真值| ÷ Σ真值
/// @param truthFulltext 真值的读过全文人数之和
/// @param actualFulltext ClickHouse 的读过全文人数之和
/// @param fulltextError 读过全文的误差
/// @param mismatchedRows 两边不一致的（天，文献）行数
/// @param sampleMismatches 不一致的前 10 行
/// @param writtenRows 写入的行数（含重发，模拟器自己记的）
/// @param deliveredEvents 送达的事件数（不含重发）
/// @param uncertainEvents 结果不明的事件数（可能已入库）
/// @param uniqueEvents ClickHouse 里按天 `uniqExact(event_id)` 之和
/// @param passed 两个误差都 ≤ 1%，且 ClickHouse 的去重条数在「送达」到「送达 + 结果不明」之间
@Builder(toBuilder = true)
public record ReconcileResult(
    long truthViewers,
    long actualViewers,
    double viewersError,
    long truthFulltext,
    long actualFulltext,
    double fulltextError,
    int mismatchedRows,
    List<String> sampleMismatches,
    long writtenRows,
    long deliveredEvents,
    long uncertainEvents,
    long uniqueEvents,
    boolean passed) {

  /// 防御性拷贝。
  public ReconcileResult {
    sampleMismatches = sampleMismatches != null ? List.copyOf(sampleMismatches) : List.of();
  }

  /// 写出。
  ///
  /// @param file 路径
  /// @param jsonMapper JSON
  public void write(Path file, JsonMapper jsonMapper) {
    try {
      Files.writeString(file, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(this));
    } catch (IOException e) {
      throw new UncheckedIOException("写对账结果失败：" + file, e);
    }
  }

  /// 读入。
  ///
  /// @param file 路径
  /// @param jsonMapper JSON
  /// @return 结果
  public static ReconcileResult read(Path file, JsonMapper jsonMapper) {
    try {
      return jsonMapper.readValue(Files.readString(file), ReconcileResult.class);
    } catch (IOException e) {
      throw new UncheckedIOException("读对账结果失败：" + file, e);
    }
  }
}
