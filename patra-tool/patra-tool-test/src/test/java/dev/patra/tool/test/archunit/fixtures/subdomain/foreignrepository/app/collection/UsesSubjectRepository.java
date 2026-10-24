package dev.patra.tool.test.archunit.fixtures.subdomain.foreignrepository.app.collection;

import dev.patra.tool.test.archunit.fixtures.subdomain.foreignrepository.domain.subject.port.repository.SubjectRepository;

/// 测试夹具：越界使用底座仓储的 collection 类。
@SuppressWarnings("unused")
public class UsesSubjectRepository {
  SubjectRepository repository;
}
