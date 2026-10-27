package dev.patra.tool.test.archunit.fixtures.subdomain.notereads.app.note;

import dev.patra.tool.test.archunit.fixtures.subdomain.notereads.domain.collection.port.read.CollectionReadPort;

/// 测试夹具：note 用 collection 的读端口。
@SuppressWarnings("unused")
public class UsesCollectionReadPort {
  CollectionReadPort readPort;
}
