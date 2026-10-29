package dev.patra.catalog.infra.publication.batch;

import dev.patra.tool.batch.autoconfigure.BatchProperties;

/// PubMed 导入作业（baseline 与增量同步）共用的 chunk 大小约束。
///
/// 一个 chunk 的文献 ID 会整体作为批量删除的 `IN` 参数，过大会超出 PostgreSQL 单语句
/// 65535 个绑定参数的限制，所以配置值有上限。
///
/// @author linqibin
/// @since 0.11.0
public final class PubmedChunkSize {

  private static final int DEFAULT_CHUNK_SIZE = 500;

  private static final int MAX_CHUNK_SIZE = 10_000;

  private PubmedChunkSize() {}

  /// 按配置解析 chunk 大小，未配置或非正数时取默认值 500。
  ///
  /// @param batchProperties 批处理配置，可空
  /// @return chunk 大小
  /// @throws IllegalArgumentException 配置值超过 10000 时抛出
  public static int resolve(BatchProperties batchProperties) {
    if (batchProperties != null && batchProperties.chunk() != null) {
      int configuredSize = batchProperties.chunk().defaultSize();
      if (configuredSize > MAX_CHUNK_SIZE) {
        throw new IllegalArgumentException(
            "chunk.default-size [%d] 超过上限 %d：一个 chunk 的文献 ID 会整体作为 deleteByPublicationIdIn 的 IN 参数，"
                    .formatted(configuredSize, MAX_CHUNK_SIZE)
                + "过大会超出 PostgreSQL 单语句 65535 个绑定参数的限制");
      }
      if (configuredSize > 0) {
        return configuredSize;
      }
    }
    return DEFAULT_CHUNK_SIZE;
  }
}
