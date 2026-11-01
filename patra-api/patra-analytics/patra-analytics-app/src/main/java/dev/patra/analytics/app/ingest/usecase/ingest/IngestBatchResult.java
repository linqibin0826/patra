package dev.patra.analytics.app.ingest.usecase.ingest;

/// 一批入库的结果。
///
/// @param inserted 写进去的条数（含被去重丢弃的重复批次）
/// @param rejected 被 ClickHouse 拒绝、转进 dlq 的条数
public record IngestBatchResult(int inserted, int rejected) {

  /// 创建结果。
  ///
  /// @param inserted 写进去的条数
  /// @param rejected 被拒的条数
  /// @return 结果
  public static IngestBatchResult of(int inserted, int rejected) {
    return new IngestBatchResult(inserted, rejected);
  }
}
