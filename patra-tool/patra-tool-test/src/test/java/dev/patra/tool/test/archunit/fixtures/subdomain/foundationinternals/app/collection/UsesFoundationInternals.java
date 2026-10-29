package dev.patra.tool.test.archunit.fixtures.subdomain.foundationinternals.app.collection;

import dev.patra.tool.test.archunit.fixtures.subdomain.foundationinternals.app.subject.SubjectInternal;
import dev.patra.tool.test.archunit.fixtures.subdomain.foundationinternals.domain.subject.port.repository.SubjectRepository;
import dev.patra.tool.test.archunit.fixtures.subdomain.foundationinternals.infra.subject.support.SubjectSqlFragments;

/// 测试夹具：使用底座仓储、app 协作类与 infra 辅助类的 collection 类。
@SuppressWarnings("unused")
public class UsesFoundationInternals {
  SubjectRepository repository;
  SubjectInternal internal;
  SubjectSqlFragments fragments;
}
