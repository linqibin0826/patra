package dev.patra.identity.config;

import dev.patra.identity.config.IdentityMailITSpringConfig.MailDrain;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/// 每个测试方法结束后等本上下文的在途邮件发完，免得它们落进下一个测试的收件箱。
///
/// 经 `META-INF/spring.factories` 对本 source set 的全部测试生效；上下文里没有 {@link MailDrain}（没导入
/// {@link IdentityMailITSpringConfig}）或上下文没起来时什么也不做。
public class IdentityMailDrainTestExecutionListener extends AbstractTestExecutionListener {

  /// 测试方法结束后等在途邮件。
  ///
  /// @param testContext 测试上下文
  @Override
  public void afterTestMethod(TestContext testContext) {
    if (!testContext.hasApplicationContext()) {
      return;
    }
    testContext
        .getApplicationContext()
        .getBeanProvider(MailDrain.class)
        .ifAvailable(MailDrain::awaitIdle);
  }
}
