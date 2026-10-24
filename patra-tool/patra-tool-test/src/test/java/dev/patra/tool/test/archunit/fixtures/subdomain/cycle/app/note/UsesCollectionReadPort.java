package dev.patra.tool.test.archunit.fixtures.subdomain.cycle.app.note;

import dev.patra.tool.test.archunit.fixtures.subdomain.cycle.domain.collection.port.read.CollectionReadPort;

/// 测试夹具：note 用 collection 的读端口，与上一个类构成环。
@SuppressWarnings("unused")
public class UsesCollectionReadPort {
  CollectionReadPort readPort;
}
