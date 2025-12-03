package com.patra.catalog.infra.batch.venue;

import com.patra.starter.batch.core.JobParams;
import java.nio.file.Path;
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
/// **与 MeshImportJobParams 的差异**：
///
/// - 使用 `filePaths` 字符串（逗号分隔）代替单文件路径
/// - 无需版本号（OpenAlex 通过 updated_date 分区管理版本）
/// - 新增 `fileCount` 用于 Job 参数展示
///
/// **断点续传支持**：
///
/// - `fileCount` 作为标识参数，相同文件数量视为同一 JobInstance
/// - `filePaths` 和 `tempFiles` 为非标识参数，临时路径变化不影响续传
///
/// @author linqibin
/// @since 0.1.0
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VenueImportJobParams implements JobParams {

  /// 非标识参数字段名（不参与 JobInstance 标识）。
  private static final Set<String> NON_IDENTIFYING_KEYS = Set.of("filePaths", "tempFiles");

  /// 分区文件路径列表（逗号分隔的字符串）。
  ///
  /// 存储格式：`"/path/to/part_000.gz,/path/to/part_001.gz,..."`
  ///
  /// **注意**：此字段为非标识参数，路径变化不影响 JobInstance 标识。
  private String filePaths;

  /// 文件数量（用于 Job 参数展示和日志）。
  ///
  /// **注意**：此字段为标识参数，作为 JobInstance 的唯一标识依据。
  private Integer fileCount;

  /// 是否为临时文件（Job 完成后需要清理）。
  ///
  /// **注意**：此字段为非标识参数。
  private String tempFiles;

  /// 从逗号分隔的路径字符串解析为 Path 列表。
  ///
  /// @return Path 列表
  public List<Path> parseFilePaths() {
    if (filePaths == null || filePaths.isBlank()) {
      return List.of();
    }
    return Arrays.stream(filePaths.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(Path::of)
        .toList();
  }

  /// 检查是否为临时文件。
  ///
  /// @return 如果是临时文件返回 true
  public boolean isTempFiles() {
    return "true".equalsIgnoreCase(tempFiles);
  }

  @Override
  public Set<String> getNonIdentifyingKeys() {
    return NON_IDENTIFYING_KEYS;
  }
}
