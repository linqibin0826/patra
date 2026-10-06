package dev.linqibin.patra.identity.app.usecase.register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.linqibin.patra.identity.domain.exception.EmailAlreadyRegisteredException;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import dev.linqibin.patra.identity.domain.model.aggregate.User;
import dev.linqibin.patra.identity.domain.model.aggregate.UserPasswordCredential;
import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import dev.linqibin.patra.identity.domain.policy.PasswordPolicy;
import dev.linqibin.patra.identity.domain.port.hashing.PasswordHashingPort;
import dev.linqibin.patra.identity.domain.port.repository.UserPasswordCredentialRepository;
import dev.linqibin.patra.identity.domain.port.repository.UserRepository;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/// RegisterUserHandler 单元测试。
@DisplayName("RegisterUserHandler 单元测试")
class RegisterUserHandlerTest {

  private static final PasswordHash HASH =
      PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA");
  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final User SAVED_USER =
      User.restore(42L, EMAIL, UserStatus.ACTIVE, null, 0L, null, null);

  private final UserRepository users = mock(UserRepository.class);
  private final UserPasswordCredentialRepository credentials =
      mock(UserPasswordCredentialRepository.class);
  private final PasswordHashingPort passwordHashing = mock(PasswordHashingPort.class);
  private final PasswordPolicy passwordPolicy = new PasswordPolicy(Set.of("password123")::contains);
  private final RegisterUserHandler handler =
      new RegisterUserHandler(
          users,
          credentials,
          passwordHashing,
          passwordPolicy,
          TransactionOperations.withoutTransaction());

  @Test
  @DisplayName("注册成功：存用户、存凭据，返回用户 ID 和规范化之后的邮箱")
  void should_register_user_with_password_credential() {
    when(passwordHashing.hash(any())).thenReturn(HASH);
    when(users.save(any())).thenReturn(SAVED_USER);
    when(credentials.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    RegisterUserResult result =
        handler.handle(RegisterUserCommand.of(" Chen.Yu@Example.com ", "correct horse battery"));

    assertThat(result.userId()).isEqualTo(42L);
    assertThat(result.email()).isEqualTo("chen.yu@example.com");
    verify(users).existsByEmail(EMAIL);
    ArgumentCaptor<UserPasswordCredential> credential =
        ArgumentCaptor.forClass(UserPasswordCredential.class);
    verify(credentials).save(credential.capture());
    assertThat(credential.getValue().getUserId()).isEqualTo(42L);
    assertThat(credential.getValue().getPasswordHash()).isEqualTo(HASH);
  }

  @Test
  @DisplayName("两个字段的错误一次报全，不查库也不做哈希")
  void should_report_all_field_violations_at_once() {
    assertThatThrownBy(() -> handler.handle(RegisterUserCommand.of("", "short")))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly("email:REQUIRED", "password:TOO_SHORT"));
    verifyNoInteractions(users, credentials, passwordHashing);
  }

  @Test
  @DisplayName("字段为 null 时按 REQUIRED 处理，不出空指针")
  void should_treat_null_fields_as_required() {
    assertThatThrownBy(() -> handler.handle(RegisterUserCommand.of(null, null)))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly("email:REQUIRED", "password:REQUIRED"));
  }

  @Test
  @DisplayName("常见密码报 TOO_COMMON")
  void should_reject_common_password() {
    assertThatThrownBy(
            () -> handler.handle(RegisterUserCommand.of("chen.yu@example.com", "Password123")))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(v -> v.code())
                    .containsExactly("TOO_COMMON"));
  }

  @Test
  @DisplayName("1 MB 的邮箱和密码在长度校验处拒绝，不做哈希")
  void should_reject_huge_input_without_hashing() {
    String hugeEmail = "a".repeat(1_000_000) + "@example.com";
    String hugePassword = "b".repeat(1_000_000);

    assertThatThrownBy(() -> handler.handle(RegisterUserCommand.of(hugeEmail, hugePassword)))
        .isInstanceOf(InvalidUserFieldsException.class)
        .satisfies(
            e ->
                assertThat(((InvalidUserFieldsException) e).getFieldViolations())
                    .extracting(v -> v.field() + ":" + v.code())
                    .containsExactly("email:TOO_LONG", "password:TOO_LONG"));
    verifyNoInteractions(passwordHashing);
  }

  @Test
  @DisplayName("邮箱已注册时返回 409，不做哈希")
  void should_reject_registered_email_before_hashing() {
    when(users.existsByEmail(EMAIL)).thenReturn(true);

    assertThatThrownBy(
            () ->
                handler.handle(
                    RegisterUserCommand.of("CHEN.YU@example.com", "correct horse battery")))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
    verifyNoInteractions(passwordHashing);
    verify(users, never()).save(any());
  }

  @Test
  @DisplayName("哈希在事务外做，两次保存在同一个事务里")
  void should_hash_outside_transaction_and_save_inside() {
    AtomicBoolean inTransaction = new AtomicBoolean(false);
    TransactionOperations recording =
        new TransactionOperations() {
          /// 标记事务边界后执行回调。
          ///
          /// @param action 回调
          /// @param <T> 结果类型
          /// @return 回调结果
          @Override
          public <T> T execute(TransactionCallback<T> action) throws TransactionException {
            inTransaction.set(true);
            try {
              return action.doInTransaction(new SimpleTransactionStatus());
            } finally {
              inTransaction.set(false);
            }
          }
        };
    RegisterUserHandler transactional =
        new RegisterUserHandler(users, credentials, passwordHashing, passwordPolicy, recording);
    when(passwordHashing.hash(any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isFalse();
              return HASH;
            });
    when(users.save(any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isTrue();
              return SAVED_USER;
            });
    when(credentials.save(any()))
        .thenAnswer(
            invocation -> {
              assertThat(inTransaction).isTrue();
              return invocation.getArgument(0);
            });

    transactional.handle(RegisterUserCommand.of("chen.yu@example.com", "correct horse battery"));

    verify(credentials).save(any());
  }

  @Test
  @DisplayName("并发注册撞上唯一约束时，仓储抛出的 409 原样传出")
  void should_propagate_unique_violation() {
    when(passwordHashing.hash(any())).thenReturn(HASH);
    when(users.save(any())).thenThrow(new EmailAlreadyRegisteredException());

    assertThatThrownBy(
            () ->
                handler.handle(
                    RegisterUserCommand.of("chen.yu@example.com", "correct horse battery")))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
    verify(credentials, never()).save(any());
  }

  @Test
  @DisplayName("命令的 toString 不输出密码")
  void should_hide_password_in_command_to_string() {
    assertThat(RegisterUserCommand.of("chen.yu@example.com", "Secret-Value-1").toString())
        .doesNotContain("Secret-Value-1");
  }
}
