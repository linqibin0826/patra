package dev.patra.tool.jpa.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/// 集成测试专用实体：两个审计时间由数据库填，实体只读映射、不带生成注解。
///
/// 这是库表规范打算采用的写法。插入和更新语句都不带这两列，写完也不回读，
/// 所以内存里的值在重新加载之前一直是空的。
@Getter
@Setter
@Entity
@Table(name = "jpa_it_readonly_audit")
public class JpaITReadOnlyAuditEntity {

  @Id
  @Column(name = "id")
  private Long id;

  @Column(name = "content")
  private String content;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
  private Instant updatedAt;
}
