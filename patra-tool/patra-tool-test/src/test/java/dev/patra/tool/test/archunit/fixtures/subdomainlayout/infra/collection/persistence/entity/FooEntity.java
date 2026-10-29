package dev.patra.tool.test.archunit.fixtures.subdomainlayout.infra.collection.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/// 测试夹具：按子域布局放在 `infra.collection.persistence.entity` 的 JPA 实体，验证规则接受 `layer.子域.x` 布局。
@Entity
public class FooEntity {
  @Id Long id;
}
