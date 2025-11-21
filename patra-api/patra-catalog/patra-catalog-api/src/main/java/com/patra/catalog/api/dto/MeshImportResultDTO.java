package com.patra.catalog.api.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MeSH 导入任务结果响应对象。
 *
 * <p>封装导入任务的响应信息，包含任务基本信息和状态。
 *
 * <p><b>字段说明</b>：
 *
 * <ul>
 *   <li>{@code taskId} - 任务 ID（雪花 ID）
 *   <li>{@code taskName} - 任务名称
 *   <li>{@code status} - 任务状态（PENDING/PROCESSING/SUCCESS/FAILED）
 *   <li>{@code startTime} - 开始时间
 *   <li>{@code message} - 响应消息（成功或失败原因）
 * </ul>
 *
 * <p><b>使用示例</b>：
 *
 * <pre>{@code
 * MeshImportResultDTO result = MeshImportResultDTO.builder()
 *     .taskId("1234567890")
 *     .taskName("2025年MeSH数据首次导入")
 *     .status("PROCESSING")
 *     .startTime(Instant.now())
 *     .message("任务已启动，正在下载 XML 文件...")
 *     .build();
 * }</pre>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeshImportResultDTO {

  /**
   * 任务 ID（雪花 ID）。
   *
   * <p>用于后续查询任务进度和状态
   */
  private String taskId;

  /**
   * 任务名称。
   *
   * <p>示例："2025年MeSH数据首次导入"
   */
  private String taskName;

  /**
   * 任务状态。
   *
   * <p>可能的值：
   *
   * <ul>
   *   <li>PENDING - 待处理（任务已创建，等待执行）
   *   <li>PROCESSING - 处理中（任务正在执行）
   *   <li>SUCCESS - 成功（任务已完成）
   *   <li>FAILED - 失败（任务失败）
   * </ul>
   */
  private String status;

  /**
   * 开始时间。
   *
   * <p>任务开始执行的时间戳
   */
  private Instant startTime;

  /**
   * 响应消息。
   *
   * <p>描述任务的当前状态或失败原因
   *
   * <p>示例：
   *
   * <ul>
   *   <li>"任务已启动，正在下载 XML 文件..."
   *   <li>"任务已完成，成功导入 350000 条记录"
   *   <li>"任务失败：网络连接超时"
   * </ul>
   */
  private String message;
}
