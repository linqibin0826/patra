package com.patra.catalog.infra.adapter.batch.venue;

import com.patra.starter.batch.core.JobParams;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// OpenAlex Venue 导入 Job 参数。
///
/// 用于 OpenAlex Venue 批量导入任务的强类型参数。
///
/// **临时文件下载特性**：
///
/// - 使用 `partitionUrls` 存储分区文件 HTTP URL 列表
/// - ItemReader 按需从远程 URL 下载每个分区文件到临时目录
/// - ItemReader 在切换文件和 close() 时自动清理临时文件
///
/// **与 MeshImportJobParams 的差异**：
///
/// - 使用 `partitionUrls` 字符串（逗号分隔）代替单文件 URL
/// - 无需版本号（OpenAlex 通过 updated_date 分区管理版本）
/// - 新增 `partitionCount` 用于 Job 参数展示
///
/// **断点续传支持**：
///
/// - `partitionCount` 作为标识参数，相同分区数量视为同一 JobInstance
/// - `partitionUrls` 为非标识参数，URL 变化不影响 JobInstance 标识
///
/// @author linqibin
/// @since 0.1.0
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueInitializeJobParams implements JobParams {

  /// 非标识参数字段名（不参与 JobInstance 标识）。
  private static final Set<String> NON_IDENTIFYING_KEYS = Set.of("partitionUrls");

  /// 分区文件 URL 列表（逗号分隔的字符串）。
  ///
  /// 存储格式：`"https://openalex.s3.amazonaws.com/.../part_000.gz,..."`
  ///
  /// **注意**：此字段为非标识参数。
  private String partitionUrls;

  /// 分区数量（用于 Job 参数展示和日志）。
  ///
  /// **注意**：此字段为标识参数，作为 JobInstance 的唯一标识依据。
  private Integer partitionCount;

  /// 从逗号分隔的 URL 字符串解析为列表。
  ///
  /// @return URL 列表
  public List<String> parsePartitionUrls() {
    if (partitionUrls == null || partitionUrls.isBlank()) {
      return List.of();
    }
    return Arrays.stream(partitionUrls.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
  }

  @Override
  public Set<String> getNonIdentifyingKeys() {
    return NON_IDENTIFYING_KEYS;
  }
}
