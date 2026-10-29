package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import dev.patra.identity.app.account.usecase.deletion.DeletionRunResult;
import dev.patra.identity.app.account.usecase.deletion.ExecuteDueAccountDeletionsCommand;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.tool.cqrs.CommandBus;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/// 注销到期执行时单个用户失败不影响其他：用 spy 只让第一个用户删凭据时抛异常，它的事务回滚、留在冷静期，
/// 第二个照常注销。
@DisplayName("注销到期执行的失败隔离")
class AccountDeletionIsolationIT extends IdentityFlowITSupport {

  @MockitoSpyBean private UserPasswordCredentialRepository credentials;

  @Autowired CommandBus commandBus;

  @Test
  @DisplayName("一个用户删凭据失败：它仍在冷静期、邮箱还在；另一个照样注销；结果里恰好一个失败")
  void should_finish_other_users_when_one_fails() {
    long failing = requestDeletion("flow.del.iso.a@example.com", "u-del-iso-a");
    long passing = requestDeletion("flow.del.iso.b@example.com", "u-del-iso-b");
    doThrow(new IllegalStateException("boom")).when(credentials).deleteByUserId(failing);
    clock.set(T0.plus(Duration.ofDays(16)));

    DeletionRunResult result = commandBus.handle(ExecuteDueAccountDeletionsCommand.of());

    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.completed()).isGreaterThanOrEqualTo(1);
    Map<String, Object> kept =
        jdbcTemplate.queryForMap("SELECT status, email FROM idn_user WHERE id = ?", failing);
    assertThat(kept.get("status")).isEqualTo("PENDING_DELETION");
    assertThat(kept.get("email")).isEqualTo("flow.del.iso.a@example.com");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM idn_user WHERE id = ?", String.class, passing))
        .isEqualTo("DELETED");
  }

  /// 注册并发起注销。
  ///
  /// @param email 邮箱
  /// @param username 用户名
  /// @return 用户 ID
  private long requestDeletion(String email, String username) {
    long userId = userIdOf(register(email, username));
    RawResponse requested =
        post(
            "/me/deletion",
            Map.of("username", username),
            assertionFor(userId, latestSessionIdOf(userId)));
    assertThat(requested.status()).as(requested.body()).isEqualTo(200);
    return userId;
  }
}
