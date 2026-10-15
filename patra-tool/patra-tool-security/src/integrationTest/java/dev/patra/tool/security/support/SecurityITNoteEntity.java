package dev.patra.tool.security.support;

import dev.patra.tool.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// 集成测试专用实体：只为观察审计列，映射到表 `security_it_note`。
@Getter
@Setter
@Entity
@Table(name = "security_it_note")
public class SecurityITNoteEntity extends BaseJpaEntity {

  /// 任意内容。
  @Column(name = "content", length = 200)
  private String content;
}
