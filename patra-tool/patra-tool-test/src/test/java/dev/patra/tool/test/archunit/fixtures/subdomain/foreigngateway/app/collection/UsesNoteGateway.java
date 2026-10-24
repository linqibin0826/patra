package dev.patra.tool.test.archunit.fixtures.subdomain.foreigngateway.app.collection;

import dev.patra.tool.test.archunit.fixtures.subdomain.foreigngateway.domain.note.port.gateway.NoteGateway;

/// 测试夹具：越界使用 note 驱动端口的 collection 类。
@SuppressWarnings("unused")
public class UsesNoteGateway {
  NoteGateway gateway;
}
