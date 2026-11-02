package dev.patra.analytics.simulator.truth;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import lombok.Builder;
import tools.jackson.databind.json.JsonMapper;

/// 一次运行的摘要 `summary.json`（spec §7.2）。
///
/// @param mode `backfill` 或 `live`
/// @param run 运行号
/// @param seed 种子
/// @param fingerprint 参数指纹（实时压测为空串）
/// @param deliveredEvents 已确认送达的事件数（不含重发）
/// @param resentEvents 重发的条数
/// @param failedEvents 明确没送达的事件数
/// @param uncertainEvents 结果不明的事件数（实时压测里重试用尽于超时或 5xx，可能已入库）
/// @param sessionsByKind 各类会话数
/// @param botEventsByReason 模拟器自己标的爬虫事件数（只有灌历史有）
/// @param eventsByDay 每天送达的事件数（不含重发）
@Builder
public record RunSummary(
    String mode,
    int run,
    long seed,
    String fingerprint,
    long deliveredEvents,
    long resentEvents,
    long failedEvents,
    long uncertainEvents,
    Map<String, Long> sessionsByKind,
    Map<String, Long> botEventsByReason,
    Map<String, Long> eventsByDay) {

  /// 拷贝成按键排序的只读表（写出的 JSON 稳定）。
  public RunSummary {
    sessionsByKind = sorted(sessionsByKind);
    botEventsByReason = sorted(botEventsByReason);
    eventsByDay = sorted(eventsByDay);
  }

  /// 写出。
  ///
  /// @param file 路径
  /// @param jsonMapper JSON
  public void write(Path file, JsonMapper jsonMapper) {
    try {
      Files.writeString(file, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(this));
    } catch (IOException e) {
      throw new UncheckedIOException("写运行摘要失败：" + file, e);
    }
  }

  /// 读入。
  ///
  /// @param file 路径
  /// @param jsonMapper JSON
  /// @return 摘要
  public static RunSummary read(Path file, JsonMapper jsonMapper) {
    try {
      return jsonMapper.readValue(Files.readString(file), RunSummary.class);
    } catch (IOException e) {
      throw new UncheckedIOException("读运行摘要失败：" + file, e);
    }
  }

  /// 按键排序的只读拷贝。
  ///
  /// @param map 表
  /// @return 拷贝
  private static Map<String, Long> sorted(Map<String, Long> map) {
    return Collections.unmodifiableMap(new TreeMap<>(map != null ? map : Map.of()));
  }
}
