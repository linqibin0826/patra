package dev.patra.identity.app.account.usecase.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.LinkSessionMismatchException;
import dev.patra.identity.domain.account.exception.ProviderAccountTakenException;
import dev.patra.identity.domain.account.exception.ProviderAlreadyLinkedException;
import dev.patra.identity.domain.account.model.aggregate.ExternalIdentity;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.ProviderAccount;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.ExternalIdentityRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.test.transaction.RecordingTransactions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// LinkProviderAccountHandler 单元测试：发起人核对在碰库之前，三种占用各走各的错误。
@DisplayName("LinkProviderAccountHandler 单元测试")
class LinkProviderAccountHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");
  private static final CurrentUser CURRENT =
      CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB);
  private static final ProviderAccount ACCOUNT =
      ProviderAccount.builder()
          .provider(IdentityProvider.GITHUB)
          .subject("583231")
          .accountLabel("octocat")
          .build();

  private final UserRepository users = mock(UserRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
  private final ClientOriginResolver resolver = mock(ClientOriginResolver.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final LinkProviderAccountHandler handler =
      new LinkProviderAccountHandler(
          users,
          userReadPort,
          identities,
          resolver,
          emailDelivery,
          new RecordingTransactions(new ArrayList<>()),
          Clock.fixed(NOW, ZoneOffset.UTC));

  private static LinkProviderAccountCommand command(CurrentUser current) {
    return LinkProviderAccountCommand.builder()
        .account(ACCOUNT)
        .initiatorUserId(42L)
        .initiatorSessionId(7001L)
        .current(current)
        .build();
  }

  private static ExternalIdentity identityOf(long userId, String subject) {
    return ExternalIdentity.restore()
        .id(9L)
        .userId(userId)
        .provider(IdentityProvider.GITHUB)
        .subject(subject)
        .accountLabel("octocat")
        .linkedAt(NOW)
        .version(0L)
        .build();
  }

  @BeforeEach
  void stubHappyPath() {
    when(users.findByIdForUpdate(42L))
        .thenReturn(
            Optional.of(
                User.restore()
                    .id(42L)
                    .email(EmailAddress.of("chen@example.com"))
                    .username(Username.of("chen-yu"))
                    .status(UserStatus.ACTIVE)
                    .version(0L)
                    .build()));
    when(identities.findByProviderAndSubject(IdentityProvider.GITHUB, "583231"))
        .thenReturn(Optional.empty());
    when(identities.findByUserIdAndProvider(42L, IdentityProvider.GITHUB))
        .thenReturn(Optional.empty());
    when(identities.insertIfAbsent(any())).thenReturn(true);
    when(resolver.resolve(any()))
        .thenReturn(
            ClientOrigin.builder()
                .ip("203.0.113.9")
                .userAgent("UA")
                .deviceName("Chrome 141 · macOS")
                .deviceClass(DeviceClass.DESKTOP)
                .ipRegion("澳大利亚")
                .build());
    when(userReadPort.findAccount(42L))
        .thenReturn(
            Optional.of(
                UserAccountReadModel.builder()
                    .userId(42L)
                    .email("chen@example.com")
                    .username("chen-yu")
                    .displayName("陈宇")
                    .status(UserStatus.ACTIVE)
                    .build()));
  }

  @Test
  @DisplayName("发起人对得上：写外部身份，登记绑定邮件")
  void should_link_when_initiator_matches() {
    ProviderLinked linked = handler.handle(command(CURRENT));

    assertThat(linked.provider()).isEqualTo(IdentityProvider.GITHUB);
    verify(identities).insertIfAbsent(any());
    verify(emailDelivery).send(any());
  }

  @Test
  @DisplayName("回调没带会话：401，不碰库")
  void should_require_login() {
    assertThatThrownBy(() -> handler.handle(command(null)))
        .isInstanceOf(AuthenticationRequiredException.class);

    verifyNoInteractions(users, identities);
  }

  @Test
  @DisplayName("用户对不上：403，不碰库")
  void should_reject_another_user() {
    CurrentUser other = CurrentUser.of(43L, 7001L, AccountType.USER, ClientType.WEB);

    assertThatThrownBy(() -> handler.handle(command(other)))
        .isInstanceOf(LinkSessionMismatchException.class);

    verifyNoInteractions(users, identities);
  }

  @Test
  @DisplayName("会话对不上：403，不碰库")
  void should_reject_another_session() {
    CurrentUser other = CurrentUser.of(42L, 7002L, AccountType.USER, ClientType.WEB);

    assertThatThrownBy(() -> handler.handle(command(other)))
        .isInstanceOf(LinkSessionMismatchException.class);

    verifyNoInteractions(users, identities);
  }

  @Test
  @DisplayName("已绑给别人：409")
  void should_reject_account_of_someone_else() {
    when(identities.findByProviderAndSubject(IdentityProvider.GITHUB, "583231"))
        .thenReturn(Optional.of(identityOf(99L, "583231")));

    assertThatThrownBy(() -> handler.handle(command(CURRENT)))
        .isInstanceOf(ProviderAccountTakenException.class);
  }

  @Test
  @DisplayName("已绑给自己：幂等，不发邮件")
  void should_be_idempotent_for_own_account() {
    when(identities.findByProviderAndSubject(IdentityProvider.GITHUB, "583231"))
        .thenReturn(Optional.of(identityOf(42L, "583231")));

    assertThat(handler.handle(command(CURRENT)).accountLabel()).isEqualTo("octocat");

    verify(identities, never()).insertIfAbsent(any());
    verifyNoInteractions(emailDelivery);
  }

  @Test
  @DisplayName("这一家绑了另一个账号：409")
  void should_reject_second_account_of_same_provider() {
    when(identities.findByUserIdAndProvider(42L, IdentityProvider.GITHUB))
        .thenReturn(Optional.of(identityOf(42L, "other")));

    assertThatThrownBy(() -> handler.handle(command(CURRENT)))
        .isInstanceOf(ProviderAlreadyLinkedException.class);
  }

  @Test
  @DisplayName("并发抢同一个第三方账号：插入没写进去，409")
  void should_reject_when_insert_conflicts() {
    when(identities.insertIfAbsent(any())).thenReturn(false);

    assertThatThrownBy(() -> handler.handle(command(CURRENT)))
        .isInstanceOf(ProviderAccountTakenException.class);

    verifyNoInteractions(emailDelivery);
  }
}
