package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import dev.patra.identity.domain.session.port.store.SessionStorePort;
import dev.patra.identity.infra.session.store.SessionStoreUnavailableException;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/// 改密码时换令牌失败的回滚：事务里最后一步的 Redis 失败要把前面的数据库写全部退回去。
/// 用 spy 只让 `rotate` 抛「Redis 不可用」，前面的失败限制、撤销其余会话照常走真实 Redis。
@DisplayName("改密码时换令牌失败的回滚")
class PasswordChangeRollbackIT extends IdentityFlowITSupport {

  @MockitoSpyBean private SessionStorePort sessions;

  @Test
  @DisplayName("换令牌时 Redis 不可用：503；密码没改成（旧密码仍能登录、新密码不能）、本机令牌仍有效、不发「密码已修改」")
  void should_roll_back_password_change_when_session_rotation_fails() {
    String email = "flow.pwd.rollback@example.com";
    RawResponse registered = register(email, "u-flow-pwd-rollback");
    long userId = userIdOf(registered);
    String token = sessionTokenOf(registered);
    long sessionId = latestSessionIdOf(userId);
    waitForMails(1);
    doThrow(new SessionStoreUnavailableException(new IllegalStateException("redis down")))
        .when(sessions)
        .rotate(any(), anyLong(), anyLong(), any());

    RawResponse changed =
        exchange(
            HttpMethod.PUT,
            "/me/password",
            Map.of("currentPassword", PASSWORD, "newPassword", "Another-Horse-03"),
            assertionFor(userId, sessionId));

    assertThat(changed.status()).as(changed.body()).isEqualTo(503);
    assertThat(redis.hasKey(sessionKey(token))).as("本机令牌没被碰").isTrue();
    assertThat(login(email, PASSWORD).status()).as("旧密码仍能登录").isEqualTo(200);
    assertThat(login(email, "Another-Horse-03").status()).as("新密码没生效").isEqualTo(401);
    assertThat(Arrays.stream(greenMail.getReceivedMessages()).map(IdentityFlowITSupport::subjectOf))
        .doesNotContain("你的 Patra 密码已修改");
  }
}
