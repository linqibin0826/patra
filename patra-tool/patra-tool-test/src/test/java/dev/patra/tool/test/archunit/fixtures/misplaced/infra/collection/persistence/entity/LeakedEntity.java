package dev.patra.tool.test.archunit.fixtures.misplaced.infra.collection.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/// 测试夹具：位置正确、但被 app 层引用的 JPA 实体。
@Entity
public class LeakedEntity {
  @Id Long id;
}
