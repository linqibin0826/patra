package dev.patra.tool.test.archunit.fixtures.subdomain.cycle.app.collection;

import dev.patra.tool.test.archunit.fixtures.subdomain.cycle.domain.note.port.read.NoteReadPort;

/// 测试夹具：collection 用 note 的读端口。
@SuppressWarnings("unused")
public class UsesNoteReadPort {
  NoteReadPort readPort;
}
