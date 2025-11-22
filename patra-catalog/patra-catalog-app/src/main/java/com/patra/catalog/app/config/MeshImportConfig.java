package com.patra.catalog.app.config;

import java.time.Duration;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/// MeSH 数据导入配置属性。
///
/// 配置前缀: `patra.catalog.mesh.import`
///
/// 提供 MeSH 数据导入任务的核心配置,包括:
///
/// - 数据源 URL 配置
///   - 批次大小配置 (表级别可定制)
///   - 下载超时和重试配置
///   - 数据量验证的预期值配置
///
/// @author Patra Lin
/// @since 0.1.0
@Data
@Component
@ConfigurationProperties(prefix = "patra.catalog.mesh.import")
public class MeshImportConfig {

  /// NLM 官方 MeSH 数据源 URL。
  ///
  /// 默认值: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml
  ///
  /// 说明: 支持通过 Nacos Config 动态修改,无需重启应用
  private String sourceUrl =
      "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml";

  /// 默认批次大小 (当表没有特定配置时使用)。
  ///
  /// 默认值: 1000
  ///
  /// 说明: 平衡内存使用和数据库性能的推荐值
  private Integer defaultBatchSize = 1000;

  /// 表级别批次大小配置映射。
  ///
  /// 配置格式:
  /// ```
  ///
  /// patra.catalog.mesh.import.batch-size-map.descriptor=1000
  /// patra.catalog.mesh.import.batch-size-map.qualifier=100
  /// patra.catalog.mesh.import.batch-size-map.tree-number=1500
  /// patra.catalog.mesh.import.batch-size-map.entry-term=2000
  /// patra.catalog.mesh.import.batch-size-map.concept=2000
  ///
  /// ```
  ///
  /// 说明: 根据表的数据量和复杂度调整批次大小,优化导入性能
  private Map<String, Integer> batchSizeMap =
      Map.of(
          "descriptor", 1000, // 主题词表 (约 35,000 条)
          "qualifier", 100, // 限定词表 (约 80 条,数据量小)
          "tree-number", 1500, // 树形编号表 (约 80,000 条)
          "entry-term", 2000, // 入口术语表 (约 250,000 条,数据量大)
          "concept", 2000 // 概念表 (约 180,000 条,数据量大)
          );

  /// XML 文件下载超时时间。
  ///
  /// 默认值: 10 分钟 (600秒)
  ///
  /// 说明: MeSH XML 文件约 300MB,根据网络条件调整
  private Duration downloadTimeout = Duration.ofMinutes(10);

  /// 批次处理失败时的最大重试次数。
  ///
  /// 默认值: 3 次
  ///
  /// 说明: 超过此次数将标记批次为 FAILED 状态
  private Integer retryMaxAttempts = 3;

  /// 预期数据量配置 (用于数据完整性验证)。
  ///
  /// 配置格式:
  /// ```
  ///
  /// patra.catalog.mesh.import.expected-counts.descriptor=35000
  /// patra.catalog.mesh.import.expected-counts.qualifier=80
  /// patra.catalog.mesh.import.expected-counts.tree-number=80000
  /// patra.catalog.mesh.import.expected-counts.entry-term=250000
  /// patra.catalog.mesh.import.expected-counts.concept=180000
  ///
  /// ```
  ///
  /// 说明: 导入完成后验证实际数量与预期的差异,超过 5% 生成警告
  private Map<String, Integer> expectedCounts =
      Map.of(
          "descriptor", 35000,
          "qualifier", 80,
          "tree-number", 80000,
          "entry-term", 250000,
          "concept", 180000);

  /// 数据量差异容忍百分比 (超过此值生成警告)。
  ///
  /// 默认值: 5.0 (即 5%)
  ///
  /// 说明: 允许合理的数据更新范围,避免误报
  private Double countDifferenceThreshold = 5.0;

  /// 预期 XML 文件大小（字节）。
  ///
  /// 默认值: 734_003_200 (约 700 MB)
  ///
  /// 说明: 用于下载后的文件完整性初步验证。由于 NLM 官方不提供 MD5 校验和，
  /// 我们使用文件大小作为完整性验证的第一道防线。允许 ±10% 的合理波动范围。
  ///
  /// 参考值来源: 2025年1月实际下载的 desc2025.xml 文件大小
  private Long expectedFileSize = 734_003_200L;

  /// 文件大小差异容忍百分比 (超过此值验证失败)。
  ///
  /// 默认值: 10.0 (即 10%)
  ///
  /// 说明: 允许文件大小在合理范围内波动（如 NLM 更新数据），超过此阈值认为文件可能损坏
  private Double fileSizeDifferenceThreshold = 10.0;

  /// 获取指定表的批次大小。
  ///
  /// 查找顺序:
  ///
  /// @param tableName 表名 (如 "descriptor", "qualifier")
  /// @return 批次大小
  public Integer getBatchSizeForTable(String tableName) {
    return batchSizeMap.getOrDefault(tableName, defaultBatchSize);
  }

  /// 获取指定表的预期记录数。
  ///
  /// @param tableName 表名 (如 "descriptor", "qualifier")
  /// @return 预期记录数 (找不到时返回 0)
  public Integer getExpectedCountForTable(String tableName) {
    return expectedCounts.getOrDefault(tableName, 0);
  }
}
