package com.patra.starter.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/// JPA 实体基类，提供审计和乐观锁功能。
///
/// **功能特性**：
///
/// - **雪花 ID**：应用层预分配，避免数据库自增以优化批量插入性能
/// - **审计字段**：自动填充 createdAt/createdBy/updatedAt/updatedBy
/// - **乐观锁**：通过 `@Version` 防止并发更新冲突
///
/// **软删除（可选）**：
///
/// 如需软删除功能，请继承 `SoftDeletableJpaEntity` 而非此类。
///
/// 使用示例：
///
/// ```java
/// @Entity
/// @Table(name = "cat_mesh_qualifier")
/// public class MeshQualifierEntity extends BaseJpaEntity {
///     @Column(name = "ui", nullable = false, unique = true)
///     private String ui;
///
///     @Column(name = "name", nullable = false)
///     private String name;
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see SoftDeletableJpaEntity
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseJpaEntity implements Serializable, IdAwareEntity {

  @Serial private static final long serialVersionUID = 1L;

  /// 默认排序：按最后更新时间降序，再按 ID 降序。
  ///
  /// 适用于大多数分页列表场景，确保最近更新的记录排在前面，
  /// 同一时间更新的记录按 ID 降序保证稳定排序。
  public static final Sort DEFAULT_SORT =
      Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

  /// 实体的主键 ID。
  ///
  /// 使用应用层预分配的雪花 ID（`SnowflakeIdGenerator.getId()`）。
  /// 不使用 `@GeneratedValue`，因为数据库自增会破坏 JPA 批量插入优化。
  @Id
  @Column(name = "id")
  private Long id;

  /// 用于存储备注或变更审计跟踪的 JSON 格式字符串。
  ///
  /// 此字段可用于记录与实体生命周期相关的重要事件或注释。
  @Column(name = "record_remarks")
  private String recordRemarks;

  /// 实体创建时间戳。
  ///
  /// 此字段在插入时由 Spring Data JPA Auditing 自动填充。
  @CreatedDate
  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  /// 创建实体的用户 ID。
  ///
  /// 此字段在插入时由 Spring Data JPA Auditing 自动填充，
  /// 需要配置 `AuditorAware` Bean 从安全上下文获取当前用户。
  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private Long createdBy;

  /// 创建实体的用户名称。
  ///
  /// 此字段提供了创建者的反规范化、易读的名称，有助于显示目的并避免额外的查询。
  /// 需要通过 `@PrePersist` 回调或自定义 Auditor 填充。
  @Column(name = "created_by_name", updatable = false)
  private String createdByName;

  /// 实体最后更新时间戳。
  ///
  /// 此字段在插入和更新时由 Spring Data JPA Auditing 自动填充。
  @LastModifiedDate
  @Column(name = "updated_at")
  private Instant updatedAt;

  /// 最后更新实体的用户 ID。
  ///
  /// 此字段在插入和更新时由 Spring Data JPA Auditing 自动填充。
  @LastModifiedBy
  @Column(name = "updated_by")
  private Long updatedBy;

  /// 最后更新实体的用户名称。
  ///
  /// 此字段提供了最后修改者的反规范化、易读的名称。
  /// 需要通过 `@PreUpdate` 回调或自定义 Auditor 填充。
  @Column(name = "updated_by_name")
  private String updatedByName;

  /// 用于乐观锁的版本号。
  ///
  /// 此字段由 JPA 自动管理，防止并发更新冲突。
  /// 初始值为 0，每次更新时自动递增。
  @Version
  @Column(name = "version")
  private Long version;

  /// 发起请求的客户端 IP 地址，以二进制格式存储。
  ///
  /// 将 IP 地址存储为字节数组，可以高效地存储 IPv4 和 IPv6 地址。
  @Column(name = "ip_address")
  private byte[] ipAddress;
}
