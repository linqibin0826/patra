package dev.patra.tool.jpa.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/// 集成测试专用实体：两个审计时间用 `@Generated`，写完由 Hibernate 读回。
///
/// 作为只读映射的对照，用来观察这种写法是否让批量插入退化成逐条执行。
@Getter
@Setter
@Entity
@Table(name = "jpa_it_generated_audit")
public class JpaITGeneratedAuditEntity {

  @Id
  @Column(name = "id")
  private Long id;

  @Column(name = "content")
  private String content;

  @Generated(event = EventType.INSERT)
  @Column(name = "created_at")
  private Instant createdAt;

  @Generated(event = {EventType.INSERT, EventType.UPDATE})
  @Column(name = "updated_at")
  private Instant updatedAt;
}
