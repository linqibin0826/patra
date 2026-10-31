package dev.patra.identity.app.account.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;

import dev.patra.identity.domain.account.model.vo.mail.OutgoingMail;

/// 比对交给发信端口的邮件：事件 ID 每次构造随机生成，比对时忽略它，其余字段逐个相等。
public final class OutgoingMailMatchers {

  private OutgoingMailMatchers() {}

  /// 除事件 ID 外与期望相同的邮件。
  ///
  /// @param expected 期望的邮件
  /// @param <T> 邮件类型
  /// @return Mockito 参数匹配
  public static <T extends OutgoingMail> T sameMailAs(T expected) {
    return argThat(
        actual -> {
          try {
            assertThat(actual)
                .usingRecursiveComparison()
                .ignoringFields("eventId")
                .isEqualTo(expected);
            return true;
          } catch (AssertionError e) {
            return false;
          }
        });
  }
}
