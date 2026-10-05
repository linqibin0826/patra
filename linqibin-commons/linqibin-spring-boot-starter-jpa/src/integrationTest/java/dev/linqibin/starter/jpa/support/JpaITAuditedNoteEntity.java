package dev.linqibin.starter.jpa.support;

import dev.linqibin.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/// 集成测试专用实体：只为观察审计列，映射到表 `jpa_it_audited_note`。
@Getter
@Setter
@Entity
@Table(name = "jpa_it_audited_note")
public class JpaITAuditedNoteEntity extends BaseJpaEntity {

  /// 任意内容。
  @Column(name = "content", length = 200)
  private String content;
}
