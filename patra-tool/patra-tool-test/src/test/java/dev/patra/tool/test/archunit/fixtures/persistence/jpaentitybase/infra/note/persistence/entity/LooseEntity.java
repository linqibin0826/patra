package dev.patra.tool.test.archunit.fixtures.persistence.jpaentitybase.infra.note.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/// 夹具：实体没有继承 `BaseJpaEntity` 或 `ValueObjectJpaEntity`，违规。
@Entity
public class LooseEntity {

  @Id private Long id;
}
