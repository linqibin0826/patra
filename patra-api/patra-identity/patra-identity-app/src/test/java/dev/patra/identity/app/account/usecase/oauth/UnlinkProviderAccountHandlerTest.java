package dev.patra.identity.app.account.usecase.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.patra.identity.domain.account.exception.LastLoginMethodException;
import dev.patra.identity.domain.account.model.aggregate.ExternalIdentity;
import dev.patra.identity.domain.account.model.aggregate.User;
import dev.patra.identity.domain.account.model.aggregate.UserPasswordCredential;
import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.PasswordHash;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.mail.LoginMethodUnlinkedMail;
import dev.patra.identity.domain.account.port.mail.EmailDeliveryPort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.ExternalIdentityRepository;
import dev.patra.identity.domain.account.port.repository.UserPasswordCredentialRepository;
import dev.patra.identity.domain.account.port.repository.UserRepository;
import dev.patra.identity.domain.account.service.ClientOriginResolver;
import dev.patra.identity.domain.session.exception.ReauthenticationRequiredException;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.service.ReauthenticationGuard;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.test.transaction.RecordingTransactions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// UnlinkProviderAccountHandler 单元测试：规则先于再认证，事务里重判。
@DisplayName("UnlinkProviderAccountHandler 单元测试")
class UnlinkProviderAccountHandlerTest {

  private static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");
  private static final CurrentUser CURRENT =
      CurrentUser.of(42L, 7001L, AccountType.USER, ClientType.WEB);
  private static final UserPasswordCredential CREDENTIAL =
      UserPasswordCredential.restore(
          7L, 42L, PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA"), 0L);
  private static final User USER =
      User.restore()
          .id(42L)
          .email(EmailAddress.of("chen@example.com"))
          .username(Username.of("chen-yu"))
          .status(UserStatus.ACTIVE)
          .version(0L)
          .build();
  private static final ExternalIdentity GITHUB =
      ExternalIdentity.restore()
          .id(9L)
          .userId(42L)
          .provider(IdentityProvider.GITHUB)
          .subject("583231")
          .accountLabel("octocat")
          .linkedAt(NOW)
          .version(0L)
          .build();

  private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
  private final UserRepository users = mock(UserRepository.class);
  private final UserReadPort userReadPort = mock(UserReadPort.class);
  private final UserPasswordCredentialRepository credentials =
      mock(UserPasswordCredentialRepository.class);
  private final ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
  private final ReauthenticationGuard guard = mock(ReauthenticationGuard.class);
  private final ClientOriginResolver resolver = mock(ClientOriginResolver.class);
  private final EmailDeliveryPort emailDelivery = mock(EmailDeliveryPort.class);
  private final UnlinkProviderAccountHandler handler =
      new UnlinkProviderAccountHandler(
          currentUserPort,
          users,
          userReadPort,
          credentials,
          identities,
          guard,
          resolver,
          emailDelivery,
          new RecordingTransactions(new ArrayList<>()),
          Clock.fixed(NOW, ZoneOffset.UTC));

  private static UnlinkProviderAccountCommand command() {
    return UnlinkProviderAccountCommand.builder().provider(IdentityProvider.GITHUB).build();
  }

  @BeforeEach
  void stubHappyPath() {
    when(currentUserPort.require()).thenReturn(CURRENT);
    when(users.findById(42L)).thenReturn(Optional.of(USER));
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(USER));
    when(identities.findByUserIdAndProvider(42L, IdentityProvider.GITHUB))
        .thenReturn(Optional.of(GITHUB));
    when(credentials.findByUserId(42L)).thenReturn(Optional.of(CREDENTIAL));
    when(identities.countByUserId(42L)).thenReturn(1L);
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
  @DisplayName("有密码：删行，登记解绑邮件，带平台名和账号标识")
  void should_delete_and_register_mail() {
    handler.handle(command());

    verify(identities).deleteByUserIdAndProvider(42L, IdentityProvider.GITHUB);
    ArgumentCaptor<LoginMethodUnlinkedMail> mail =
        ArgumentCaptor.forClass(LoginMethodUnlinkedMail.class);
    verify(emailDelivery).send(mail.capture());
    assertThat(mail.getValue().providerName()).isEqualTo("GitHub");
    assertThat(mail.getValue().accountLabel()).isEqualTo("octocat");
  }

  @Test
  @DisplayName("本来就没绑：什么都不做，也不要求再认证")
  void should_do_nothing_when_not_linked() {
    when(identities.findByUserIdAndProvider(42L, IdentityProvider.GITHUB))
        .thenReturn(Optional.empty());

    handler.handle(command());

    verifyNoInteractions(guard, emailDelivery);
    verify(identities, never()).deleteByUserIdAndProvider(42L, IdentityProvider.GITHUB);
  }

  @Test
  @DisplayName("没有密码且只剩一种：规则先于再认证，用户看到的是真实原因")
  void should_check_rule_before_reauth() {
    when(credentials.findByUserId(42L)).thenReturn(Optional.empty());
    doThrow(new ReauthenticationRequiredException()).when(guard).requireRecent(CURRENT);

    assertThatThrownBy(() -> handler.handle(command()))
        .isInstanceOf(LastLoginMethodException.class);
  }

  @Test
  @DisplayName("并发：事务外看到还有两种，拿到锁后只剩一种，拒绝并且不删")
  void should_recheck_rule_after_lock() {
    when(credentials.findByUserId(42L)).thenReturn(Optional.empty());
    when(identities.countByUserId(42L)).thenReturn(2L, 1L);

    assertThatThrownBy(() -> handler.handle(command()))
        .isInstanceOf(LastLoginMethodException.class);

    verify(identities, never()).deleteByUserIdAndProvider(42L, IdentityProvider.GITHUB);
    verifyNoInteractions(emailDelivery);
  }
}
