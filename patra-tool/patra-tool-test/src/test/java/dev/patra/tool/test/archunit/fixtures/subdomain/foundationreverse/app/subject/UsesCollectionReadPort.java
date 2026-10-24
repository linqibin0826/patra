package dev.patra.tool.test.archunit.fixtures.subdomain.foundationreverse.app.subject;

import dev.patra.tool.test.archunit.fixtures.subdomain.foundationreverse.domain.collection.port.read.CollectionReadPort;

/// 测试夹具：反向依赖 collection 的底座类。
@SuppressWarnings("unused")
public class UsesCollectionReadPort {
  CollectionReadPort readPort;
}
