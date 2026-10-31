package dev.patra.identity.config;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/// 每个测试方法结束后等事件发布登记表清空：本用例登记的安全邮件都已被 broker 收进捕获队列，下一个用例开头清队列时
/// 不会有晚到的事件落进它的队列。表一直不空（外送失败）时让这个用例失败。
///
/// 经 `META-INF/spring.factories` 对本 source set 的全部测试生效；上下文里没有
/// {@link IdentitySecurityMailITFixture}（没导入它的配置）或上下文没起来时什么也不做。
public class IdentitySecurityMailDrainTestExecutionListener extends AbstractTestExecutionListener {

  /// 测试方法结束后等登记表清空。
  ///
  /// @param testContext 测试上下文
  @Override
  public void afterTestMethod(TestContext testContext) {
    if (!testContext.hasApplicationContext()) {
      return;
    }
    testContext
        .getApplicationContext()
        .getBeanProvider(IdentitySecurityMailITFixture.class)
        .ifAvailable(IdentitySecurityMailITFixture::awaitRegistryDrained);
  }
}
