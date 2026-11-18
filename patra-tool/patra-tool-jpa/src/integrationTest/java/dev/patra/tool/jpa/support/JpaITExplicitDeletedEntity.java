package dev.patra.tool.jpa.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/// 集成测试专用实体：`deleted_at` 是普通字段，没有 `@SoftDelete`，即库表规范要求的写法。
@Getter
@Setter
@Entity
@Table(name = "jpa_it_explicit_deleted")
public class JpaITExplicitDeletedEntity {

  @Id
  @Column(name = "id")
  private Long id;

  @Column(name = "content")
  private String content;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
