package dev.patra.identity.app.account.usecase.query;

import dev.patra.identity.domain.account.exception.UserNotFoundException;
import dev.patra.identity.domain.account.model.aggregate.FormerUsername;
import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.PublicUserLookup;
import dev.patra.identity.domain.account.model.read.PublicUserReadModel;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.profile.PrivacySettings;
import dev.patra.identity.domain.account.policy.ProfileDisplayPolicy;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.domain.account.port.repository.EmailTokenRepository;
import dev.patra.identity.domain.account.port.repository.FormerUsernameRepository;
import dev.patra.identity.domain.account.port.repository.UserProfileRepository;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 「当前用户」、本人的资料与隐私设置、待确认的改邮箱和公开主页的查询。只读，不经 CommandBus，没有事务。
///
/// 当前用户：没有当前用户、会话指向的用户不存在、用户已封禁或已注销，这些情况都抛
/// {@link AuthenticationRequiredException}（401）：对客户端来说都是「这个会话不再有效」。
/// 冷静期内的用户照常返回，响应里带状态。
///
/// 公开身份卡：别人只能看到正常且邮箱已验证的用户；本人永远能看到自己；保留期内的旧名跳到当前用户名；
/// 其余一律 {@link UserNotFoundException}（404），不区分不存在和不可见。
@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueryService {

  private final CurrentUserPort currentUserPort;
  private final UserReadPort userReadPort;
  private final FormerUsernameRepository formerUsernames;
  private final EmailTokenRepository tokens;
  private final UserProfileRepository profiles;
  private final ProfileDisplayPolicy display;
  private final Clock clock;

  /// 当前登录用户的账号信息。
  ///
  /// @return 账号信息
  /// @throws AuthenticationRequiredException 没有当前用户、用户不存在、已封禁或已注销
  public UserAccountReadModel currentAccount() {
    return requireUsableAccount(currentUserPort.require());
  }

  /// 当前用户自己的资料（`GET /me/profile`）。状态判断同 {@link #currentAccount()}。
  ///
  /// @return 资料
  /// @throws AuthenticationRequiredException 没有当前用户、用户不存在、已封禁或已注销
  public UserProfile myProfile() {
    long userId = requireUsableAccount(currentUserPort.require()).userId();
    return profiles
        .findByUserId(userId)
        .orElseThrow(() -> new IllegalStateException("用户 " + userId + " 没有资料行"));
  }

  /// 当前用户自己的隐私设置（`GET /me/privacy`）。状态判断同 {@link #currentAccount()}。
  ///
  /// @return 隐私设置
  /// @throws AuthenticationRequiredException 没有当前用户、用户不存在、已封禁或已注销
  public PrivacySettings myPrivacy() {
    return myProfile().getPrivacy();
  }

  /// 会话指向的账号还能用：存在且不是已封禁、已注销。
  ///
  /// @param current 当前用户
  /// @return 账号信息
  /// @throws AuthenticationRequiredException 用户不存在、已封禁或已注销
  private UserAccountReadModel requireUsableAccount(CurrentUser current) {
    UserAccountReadModel account =
        userReadPort
            .findAccount(current.userId())
            .orElseThrow(
                () -> {
                  log.warn(
                      "会话指向的用户不存在: userId={}, sessionId={}", current.userId(), current.sessionId());
                  return new AuthenticationRequiredException();
                });
    if (account.status() == UserStatus.BANNED || account.status() == UserStatus.DELETED) {
      log.warn(
          "已封禁或已注销用户的断言仍在有效期内: userId={}, sessionId={}, status={}",
          current.userId(),
          current.sessionId(),
          account.status());
      throw new AuthenticationRequiredException();
    }
    return account;
  }

  /// 当前用户待确认的改邮箱：一条未过期的 `EMAIL_CHANGE_CONFIRM` 令牌。
  ///
  /// @return 新邮箱与到期时间；没有待确认时为空
  /// @throws AuthenticationRequiredException 没有当前用户
  public Optional<PendingEmailChange> pendingEmailChange() {
    CurrentUser current = currentUserPort.require();
    return tokens
        .findActive(current.userId(), EmailTokenPurpose.EMAIL_CHANGE_CONFIRM, clock.instant())
        .map(token -> PendingEmailChange.of(token.getEmail().value(), token.getExpiresAt()));
  }

  /// 按路径里的用户名查公开身份卡。请求者是当前用户（公开路由上网关照样翻译令牌），匿名时为空。
  ///
  /// 1. 语法不合法直接 404，不查库。
  /// 2. 当前用户名查到：对请求者可见返回 `Found`，否则 404。
  /// 3. 查不到：是保留期内的旧名，且原主对请求者可见，返回 `Moved`；否则 404。
  ///
  /// @param rawUsername 路径里的用户名，先规范化
  /// @return 找到或跳转
  /// @throws UserNotFoundException 不存在、不可见、旧名已过期
  public PublicUserLookup publicIdentity(String rawUsername) {
    if (Username.violation(rawUsername).isPresent()) {
      throw new UserNotFoundException();
    }
    Username username = Username.of(rawUsername);
    Optional<Long> requester = currentUserPort.current().map(CurrentUser::userId);
    Optional<PublicUserReadModel> direct = userReadPort.findPublicIdentity(username);
    if (direct.isPresent()) {
      PublicUserReadModel user = direct.get();
      if (!user.isVisibleTo(requester)) {
        throw new UserNotFoundException();
      }
      return PublicUserLookup.Found.of(user.viewFor(requester, display));
    }
    FormerUsername former =
        formerUsernames
            .findReserved(username, clock.instant())
            .orElseThrow(UserNotFoundException::new);
    PublicUserReadModel owner =
        userReadPort
            .findPublicIdentityByUserId(former.getUserId())
            .filter(user -> user.isVisibleTo(requester))
            .orElseThrow(UserNotFoundException::new);
    return PublicUserLookup.Moved.of(Username.of(owner.username()));
  }
}
