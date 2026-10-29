package dev.patra.tool.test.archunit.fixtures.misplaced.domain.collection;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/// 测试夹具：放错在 domain 层的 JPA 实体。
@Entity
public class MisplacedEntity {
  @Id Long id;
}
