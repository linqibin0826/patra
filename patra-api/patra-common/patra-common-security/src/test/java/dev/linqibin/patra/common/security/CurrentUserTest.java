package dev.linqibin.patra.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// CurrentUser 单元测试。
@DisplayName("CurrentUser 单元测试")
class CurrentUserTest {

  @Test
  @DisplayName("工厂方法创建的当前用户带齐四个字段")
  void should_expose_all_fields_when_created_by_factory() {
    CurrentUser user = CurrentUser.of(1001L, 2001L, AccountType.USER, ClientType.WEB);

    assertThat(user.userId()).isEqualTo(1001L);
    assertThat(user.sessionId()).isEqualTo(2001L);
    assertThat(user.accountType()).isEqualTo(AccountType.USER);
    assertThat(user.clientType()).isEqualTo(ClientType.WEB);
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, -1L})
  @DisplayName("用户 ID 不是正数时拒绝创建")
  void should_reject_non_positive_user_id(long userId) {
    assertThatThrownBy(() -> CurrentUser.of(userId, 2001L, AccountType.USER, ClientType.WEB))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("userId");
  }

  @ParameterizedTest
  @ValueSource(longs = {0L, -1L})
  @DisplayName("会话 ID 不是正数时拒绝创建")
  void should_reject_non_positive_session_id(long sessionId) {
    assertThatThrownBy(() -> CurrentUser.of(1001L, sessionId, AccountType.USER, ClientType.WEB))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sessionId");
  }

  @Test
  @DisplayName("账号类型为空时拒绝创建")
  void should_reject_null_account_type() {
    assertThatThrownBy(() -> CurrentUser.of(1001L, 2001L, null, ClientType.WEB))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("accountType");
  }

  @Test
  @DisplayName("客户端类型为空时拒绝创建")
  void should_reject_null_client_type() {
    assertThatThrownBy(() -> CurrentUser.of(1001L, 2001L, AccountType.USER, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("clientType");
  }
}
