package dev.patra.tool.test.archunit.fixtures.subdomain.foundationextras.app.collection;

import dev.patra.tool.test.archunit.fixtures.subdomain.foundationextras.domain.subject.exception.SubjectUnavailableException;
import dev.patra.tool.test.archunit.fixtures.subdomain.foundationextras.domain.subject.model.user.UserCard;
import dev.patra.tool.test.archunit.fixtures.subdomain.foundationextras.domain.subject.port.user.UserDirectoryPort;

/// 测试夹具：collection 用底座的名片端口、名片和异常。
@SuppressWarnings("unused")
public class UsesFoundationExtras {
  UserDirectoryPort users;
  UserCard card;
  SubjectUnavailableException failure;
}
