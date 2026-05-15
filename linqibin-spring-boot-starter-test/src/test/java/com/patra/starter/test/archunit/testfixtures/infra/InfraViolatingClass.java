package com.patra.starter.test.archunit.testfixtures.infra;

import org.springframework.transaction.annotation.Transactional;

/// 测试夹具：infra 包下使用 @Transactional 的违规类。
///
/// 根据六边形架构规则，@Transactional 应该只在 app 层使用。
/// 此类用于测试 ArchUnit 能正确检测 infra 层的事务注解违规。
@SuppressWarnings("unused")
@Transactional
public class InfraViolatingClass {
  // 违规：infra 层不应使用 @Transactional
}
