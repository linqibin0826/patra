package dev.patra.tool.test.archunit.fixtures.subdomain.compliant.app.collection;

import dev.patra.tool.test.archunit.fixtures.subdomain.compliant.domain.subject.model.enums.SubjectType;
import dev.patra.tool.test.archunit.fixtures.subdomain.compliant.domain.subject.model.read.SubjectView;
import dev.patra.tool.test.archunit.fixtures.subdomain.compliant.domain.subject.model.vo.SubjectId;
import dev.patra.tool.test.archunit.fixtures.subdomain.compliant.domain.subject.port.gateway.SubjectGateway;
import dev.patra.tool.test.archunit.fixtures.subdomain.compliant.domain.subject.port.read.SubjectReadPort;

/// 测试夹具：只经底座公开包使用底座的 collection 处理器。
@SuppressWarnings("unused")
public class CollectFavoriteHandler {
  SubjectReadPort readPort;
  SubjectGateway gateway;

  /// 用底座的值对象与读模型。
  ///
  /// @param id 对象 ID
  /// @param type 对象类型
  /// @return 读模型
  SubjectView handle(SubjectId id, SubjectType type) {
    return null;
  }
}
