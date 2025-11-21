package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MeSH 导入任务数据库实体，映射到表 {@code cat_mesh_import_task}。
 *
 * <p>表结构：管理 MeSH 数据导入任务的完整生命周期
 *
 * <p>关键字段说明：
 *
 * <ul>
 *   <li>{@code task_name} - 任务名称（如 "MeSH 2025 导入"）
 *   <li>{@code status} - 任务状态（PENDING/PROCESSING/SUCCESS/FAILED/CANCELLED）
 *   <li>{@code source_url} - NLM 数据源 URL
 *   <li>{@code xml_file_hash} - XML 文件 MD5 哈希（验证完整性）
 *   <li>{@code total_records} - 总记录数（约 350,000）
 *   <li>{@code processed_records} - 已处理记录数（用于断点续传）
 *   <li>{@code failed_batch_count} - 失败批次数（用于错误统计）
 * </ul>
 *
 * <p><b>设计原则</b>：
 *
 * <ul>
 *   <li>继承 {@link BaseDO}：获得10个审计字段（created_at、updated_at等）
 *   <li>雪花ID：主键使用 MyBatis-Plus 的 {@code IdType.ASSIGN_ID}
 *   <li>乐观锁：使用 {@code version} 字段防止并发修改
 *   <li>纯数据载体：不包含业务逻辑，只负责数据存储
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_mesh_import_task")
public class MeshImportTaskDO extends BaseDO {

  /** 任务名称（如 "MeSH 2025 导入"） */
  @TableField("task_name")
  private String taskName;

  /** 任务状态（PENDING/PROCESSING/SUCCESS/FAILED/CANCELLED） */
  @TableField("status")
  private String status;

  /** 数据源 URL（如 https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml） */
  @TableField("source_url")
  private String sourceUrl;

  /** XML 文件 MD5 哈希（用于验证文件完整性） */
  @TableField("xml_file_hash")
  private String xmlFileHash;

  /** 文件大小（字节，约 700MB） */
  @TableField("xml_file_size")
  private Long xmlFileSize;

  /** 总记录数（约 350,000） */
  @TableField("total_records")
  private Integer totalRecords;

  /** 已处理记录数（用于断点续传和进度显示） */
  @TableField("processed_records")
  private Integer processedRecords;

  /** 失败批次数（用于错误统计和告警） */
  @TableField("failed_batch_count")
  private Integer failedBatchCount;

  /** 最后错误信息（最多保存最近一次错误） */
  @TableField("last_error_message")
  private String lastErrorMessage;

  /** 开始时间（任务状态变为 PROCESSING 时记录） */
  @TableField("start_time")
  private Instant startTime;

  /** 结束时间（任务状态变为 SUCCESS/FAILED 时记录） */
  @TableField("end_time")
  private Instant endTime;
}
