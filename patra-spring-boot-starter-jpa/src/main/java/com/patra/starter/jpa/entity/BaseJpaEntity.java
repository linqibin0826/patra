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
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/// JPA 实体基类，提供审计、乐观锁和软删除功能。
///
/// **功能特性**：
///
/// - **雪花 ID**：应用层预分配，避免数据库自增以优化批量插入性能
/// - **审计字段**：自动填充 createdAt/createdBy/updatedAt/updatedBy
/// - **乐观锁**：通过 `@Version` 防止并发更新冲突
/// - **软删除**：使用 `@SQLRestriction` 自动过滤已删除记录
///
/// **软删除实现**（时间戳策略）：
///
/// - 使用 `@SQLRestriction("deleted_at IS NULL")` 自动过滤已删除记录
/// - 应用层通过设置 `deletedAt = Instant.now()` 实现软删除
/// - 通过 `@SQLRestriction` 自动过滤，无需手动添加查询条件
/// - 需要查询已删除记录时，使用 Native Query 或自定义 Repository 方法
///
/// **为什么不用 @SoftDelete**：
///
/// Hibernate 6.6 的 `@SoftDelete` 设计为布尔型策略（`0/1`、`Y/N`），
/// 不原生支持时间戳策略。对于时间戳软删除，`@SQLRestriction` 是更好的选择。
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
///     // ...
/// }
///
/// // 软删除实体
/// entity.setDeletedAt(Instant.now());
/// repository.save(entity);
///
/// // 查询自动排除已删除记录
/// repository.findAll(); // WHERE deleted_at IS NULL
/// ```
///
/// @author linqibin
/// @since 0.1.0
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_at IS NULL")
public abstract class BaseJpaEntity implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

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

  /// 实体软删除时间戳。
  ///
  /// - `null`：记录未删除（活跃状态）
  /// - 非 `null`：记录已删除，值为删除时间
  ///
  /// 通过 `@SQLRestriction("deleted_at IS NULL")` 自动过滤已删除记录。
  /// 应用层执行软删除时，设置此字段为 `Instant.now()` 并调用 `save()`。
  @Column(name = "deleted_at")
  private Instant deletedAt;

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

  /// 执行软删除。
  ///
  /// 设置 `deletedAt` 为当前时间戳，标记记录为已删除。
  /// 调用此方法后，需要调用 `repository.save(entity)` 持久化更改。
  public void softDelete() {
    this.deletedAt = Instant.now();
  }

  /// 检查实体是否已被软删除。
  ///
  /// @return 如果 `deletedAt` 不为 null，返回 `true`
  public boolean isDeleted() {
    return this.deletedAt != null;
  }
}
