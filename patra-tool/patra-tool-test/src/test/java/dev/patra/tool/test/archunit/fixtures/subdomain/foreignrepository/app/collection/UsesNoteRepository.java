package dev.patra.tool.test.archunit.fixtures.subdomain.foreignrepository.app.collection;

import dev.patra.tool.test.archunit.fixtures.subdomain.foreignrepository.domain.note.port.repository.NoteRepository;

/// 测试夹具：越界使用 note 仓储的 collection 类。
@SuppressWarnings("unused")
public class UsesNoteRepository {
  NoteRepository repository;
}
