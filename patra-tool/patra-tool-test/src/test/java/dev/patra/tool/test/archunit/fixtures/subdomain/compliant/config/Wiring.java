package dev.patra.tool.test.archunit.fixtures.subdomain.compliant.config;

import dev.patra.tool.test.archunit.fixtures.subdomain.compliant.app.collection.CollectFavoriteHandler;
import dev.patra.tool.test.archunit.fixtures.subdomain.compliant.app.subject.SubjectInternal;

/// 测试夹具：组装根，引用两个子域的内部类，不受子域规则管。
@SuppressWarnings("unused")
public class Wiring {
  CollectFavoriteHandler handler;
  SubjectInternal subjectInternal;
}
