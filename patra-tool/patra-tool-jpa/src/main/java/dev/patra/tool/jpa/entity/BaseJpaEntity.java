package dev.patra.tool.jpa.entity;

import dev.patra.tool.jpa.audit.UpdatedByResetListener;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.net.InetAddress;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/// 主表实体的基类：映射主表在业务列之外必备的列（库表规范第 3 节）。
///
/// 主表是有自己生命周期、会被按行修改、「谁改的」有意义的表；其他表用 `ValueObjectJpaEntity`。
///
/// | 列 | 怎么来 |
/// |---|---|
/// | `id` | 应用预分配的雪花 ID（`SnowflakeIdGenerator.getId()`），不用数据库自增，批量插入才能攒批 |
/// | `created_by` / `updated_by` | Spring Data 审计填当前操作人，系统写入为空（见 `UpdatedByResetListener`） |
/// | `ip_address` | 发起请求的客户端 IP，存成 PostgreSQL 的 `INET` |
/// | `record_remarks` | 给数据处理和人工核对写的备注 |
/// | `version` | 乐观锁 |
///
/// `created_at` / `updated_at` 不映射：它们由数据库触发器填（第 4 节），应用写了也会被忽略。查询要用时，实体自己加只读字段，
/// 不用 `@Generated`（它会让批量插入退化成逐条执行）。
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@MappedSuperclass
// 顺序不能换：先清空 updated_by，再由 Spring Data 审计按当前用户填；反过来会把刚填的操作人清掉。
@EntityListeners({UpdatedByResetListener.class, AuditingEntityListener.class})
public abstract class BaseJpaEntity implements Serializable, IdAwareEntity {

  @Serial private static final long serialVersionUID = 1L;

  /// 主键，应用预分配的雪花 ID。
  @Id
  @Column(name = "id")
  private Long id;

  /// 创建这一行的用户 ID，系统写入为空。
  @CreatedBy
  @Column(name = "created_by", updatable = false)
  private Long createdBy;

  /// 最后修改这一行的用户 ID，系统写入为空。
  @LastModifiedBy
  @Column(name = "updated_by")
  private Long updatedBy;

  /// 发起请求的客户端 IP。
  @Column(name = "ip_address")
  @JdbcTypeCode(SqlTypes.INET)
  private InetAddress ipAddress;

  /// 备注，JSON 字符串，给数据处理和人工核对用，只在库里直接写。
  ///
  /// 只读映射：仓储由聚合重建实体再合并保存时，重建出的实体不带备注，可写的话每次保存都会把库里的备注清空。
  @Column(name = "record_remarks", insertable = false, updatable = false)
  @JdbcTypeCode(SqlTypes.JSON)
  private String recordRemarks;

  /// 乐观锁版本号，新行为空，由 JPA 维护。
  @Version
  @Column(name = "version")
  private Long version;
}
