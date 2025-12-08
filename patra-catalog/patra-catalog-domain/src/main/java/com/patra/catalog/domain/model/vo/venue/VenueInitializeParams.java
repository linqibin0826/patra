package com.patra.catalog.domain.model.vo.venue;

import java.util.List;

/// Venue 批量导入参数值对象。
///
/// 封装批量导入所需的参数，用于 `VenueInitializeBatchPort.launchImport()` 方法调用。
///
/// **参数说明**：
///
/// - `partitionUrls`：分区文件 URL 列表（.gz 压缩），至少包含一个 URL
///
/// **流式处理特性**：
///
/// - 无磁盘落盘，ItemReader 在切换文件时按需建立 HTTP 连接
/// - 传递 URL 列表给 Job，由 ItemReader 负责流式下载
///
/// **设计说明**：
///
/// 导入操作设计为「一次性初始化」语义，不支持增量或覆盖模式。
/// 每次导入都创建新的 Job 实例，相同参数不会复用旧实例。
///
/// @author linqibin
/// @since 0.1.0
public record VenueInitializeParams(List<String> partitionUrls) {

  /// 创建导入参数。
  ///
  /// @param partitionUrls 分区文件 URL 列表
  public VenueInitializeParams {
    if (partitionUrls == null || partitionUrls.isEmpty()) {
      throw new IllegalArgumentException("partitionUrls 不能为空");
    }
  }

  /// 创建导入参数。
  ///
  /// @param partitionUrls 分区文件 URL 列表
  /// @return 导入参数
  public static VenueInitializeParams of(List<String> partitionUrls) {
    return new VenueInitializeParams(partitionUrls);
  }

  /// 获取分区 URL 的逗号分隔字符串。
  ///
  /// 用于 Spring Batch Job 参数序列化。
  ///
  /// @return 逗号分隔的 URL 字符串
  public String getPartitionUrlsAsString() {
    return String.join(",", partitionUrls);
  }

  /// 获取分区数量。
  ///
  /// @return 分区数量
  public int getPartitionCount() {
    return partitionUrls.size();
  }
}
