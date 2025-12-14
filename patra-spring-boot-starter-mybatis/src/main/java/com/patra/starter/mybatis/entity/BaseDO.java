package com.patra.starter.mybatis.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// 数据对象（DO）的抽象基类，提供审计、乐观锁和软删除的通用字段。
///
/// 此类旨在被所有持久化实体（DO）继承，以确保对常见关注点的一致处理， 如跟踪创建/更新元数据、管理并发修改以及支持逻辑删除。它与 MyBatis-Plus 的自动字段填充功能集成。
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public abstract class BaseDO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 实体的主键ID。
  ///
  /// 使用 MyBatis-Plus 的 {@link IdType#ASSIGN_ID}，通常配置为分布式ID生成器（如雪花ID）以确保系统范围内的唯一性。
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  /// 用于存储备注或变更审计跟踪的JSON格式字符串。
  ///
  ///
  // 此字段可用于记录与实体生命周期相关的重要事件或注释。例如：`[{"timestamp":"2025-10-11T10:00:00Z","user":"admin","action":"Created"`]}
  /// 建议使用 Jackson 的 `JsonNode` 结合自定义 TypeHandler 来管理此字段。
  @TableField(value = "record_remarks")
  private String recordRemarks;

  /// 实体创建时间戳。
  ///
  /// 此字段在插入时由 {@link MetaObjectHandler} 自动填充。
  @TableField(value = "created_at", fill = FieldFill.INSERT)
  private Instant createdAt;

  /// 创建实体的用户ID。
  ///
  /// 此字段应在插入时由 {@link MetaObjectHandler} 填充，通常通过从安全上下文中获取当前用户的ID。
  @TableField(value = "created_by", fill = FieldFill.INSERT)
  private Long createdBy;

  /// 创建实体的用户名称。
  ///
  /// 此字段提供了创建者的反规范化、易读的名称，有助于显示目的并避免额外的查询。
  /// 应在插入时由 {@link MetaObjectHandler} 填充。
  @TableField(value = "created_by_name", fill = FieldFill.INSERT)
  private String createdByName;

  /// 实体最后更新时间戳。
  ///
  /// 此字段在插入和更新时由 {@link MetaObjectHandler} 自动填充。
  @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
  private Instant updatedAt;

  /// 最后更新实体的用户ID。
  ///
  /// 此字段应在插入和更新时由 {@link MetaObjectHandler} 填充，通常通过从安全上下文中获取当前用户的ID。
  @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
  private Long updatedBy;

  /// 最后更新实体的用户名称。
  ///
  /// 此字段提供了最后修改者的反规范化、易读的名称。应在插入和更新时由 {@link MetaObjectHandler} 填充。
  @TableField(value = "updated_by_name", fill = FieldFill.INSERT_UPDATE)
  private String updatedByName;

  /// 用于乐观锁的版本号。
  ///
  /// 此字段由 MyBatis-Plus 的 {@link OptimisticLockerInnerInterceptor} 管理，以防止并发更新冲突。
  /// 插入时由 {@link MetaObjectHandler} 自动填充初始值 1，每次更新时自动递增。
  @Version
  @TableField(value = "version", fill = FieldFill.INSERT)
  private Long version;

  /// 发起请求的客户端IP地址，以二进制格式存储。
  ///
  /// 将IP地址存储为字节数组，可以高效地存储 IPv4 和 IPv6 地址。
  @TableField(value = "ip_address")
  private byte[] ipAddress;

  /// 实体逻辑删除的时间戳。
  ///
  /// 此字段实现软删除功能。`null` 表示实体处于活动状态，
  /// 有时间戳值表示实体已在该时刻被删除。
  /// MyBatis-Plus 查询将自动过滤 `deleted_at IS NOT NULL` 的记录。
  @TableLogic
  @TableField(value = "deleted_at")
  private Instant deletedAt;
}
