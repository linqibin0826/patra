package dev.patra.tool.test.archunit.fixtures.misplaced.app.collection;

import dev.patra.tool.test.archunit.fixtures.misplaced.infra.collection.persistence.entity.LeakedEntity;

/// 测试夹具：越界引用 JPA 实体的 app 类。
@SuppressWarnings("unused")
public class UsesLeakedEntity {
  LeakedEntity entity;
}
