package com.patra.catalog.app.usecase.meshimport.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/// MeSH 导入任务结果响应对象。
/// 
/// 封装导入任务的响应信息，包含任务基本信息和状态。
/// 
/// **字段说明**：
/// 
/// - `taskId` - 任务 ID（雪花 ID）
///   - `taskName` - 任务名称
///   - `status` - 任务状态（PENDING/PROCESSING/SUCCESS/FAILED）
///   - `startTime` - 开始时间
///   - `message` - 响应消息（成功或失败原因）
/// 
/// **使用示例**：
/// 
/// ```java
/// MeshImportResultDTO result = MeshImportResultDTO.builder()
///     .taskId("1234567890")
///     .taskName("2025年MeSH数据首次导入")
///     .status("PROCESSING")
///     .startTime(Instant.now())
///     .message("任务已启动，正在下载 XML 文件...")
///     .build();
/// ```
/// 
/// @author linqibin
/// @since 0.2.0
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeshImportResultDTO {

  /// 任务 ID（雪花 ID）。
/// 
/// 用于后续查询任务进度和状态
  private String taskId;

  /// 任务名称。
/// 
/// 示例："2025年MeSH数据首次导入"
  private String taskName;

  /// 任务状态。
/// 
/// 可能的值：
/// 
/// - PENDING - 待处理（任务已创建，等待执行）
///   - PROCESSING - 处理中（任务正在执行）
///   - SUCCESS - 成功（任务已完成）
///   - FAILED - 失败（任务失败）
/// 
  private String status;

  /// 开始时间。
/// 
/// 任务开始执行的时间戳
  private Instant startTime;

  /// 响应消息。
/// 
/// 描述任务的当前状态或失败原因
/// 
/// 示例：
/// 
/// - "任务已启动，正在下载 XML 文件..."
///   - "任务已完成，成功导入 350000 条记录"
///   - "任务失败：网络连接超时"
/// 
  private String message;
}
