package dev.patra.identity.app.usecase.profile;

import dev.patra.identity.domain.model.aggregate.User;
import dev.patra.identity.domain.model.aggregate.UserProfile;
import dev.patra.identity.domain.model.vo.profile.PrivacySettings;
import dev.patra.identity.domain.policy.ProfilePolicy;
import dev.patra.identity.domain.port.repository.UserProfileRepository;
import dev.patra.identity.domain.port.repository.UserRepository;
import dev.patra.tool.core.security.AuthenticationRequiredException;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 当前用户改隐私设置：解析（错误一次报全）→ 加用户行锁 → 状态守卫 → 读资料、替换、保存。
/// 不撤销会话、不发邮件、不要求再认证。
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdatePrivacyHandler implements CommandHandler<UpdatePrivacyCommand, PrivacySettings> {

  private final CurrentUserPort currentUserPort;
  private final UserRepository users;
  private final UserProfileRepository profiles;
  private final ProfilePolicy profilePolicy;
  private final TransactionOperations transactions;

  /// 改隐私设置。
  ///
  /// @param command 命令
  /// @return 保存后的设置
  @Override
  public PrivacySettings handle(UpdatePrivacyCommand command) {
    long userId = currentUserPort.require().userId();
    PrivacySettings privacy =
        profilePolicy.parsePrivacy(
            command.profileVisibility(),
            command.professionalVisibility(),
            command.searchIndexing());
    PrivacySettings saved =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  User user =
                      users
                          .findByIdForUpdate(userId)
                          .orElseThrow(AuthenticationRequiredException::new);
                  user.requireActive();
                  UserProfile profile =
                      profiles
                          .findByUserId(userId)
                          .orElseThrow(() -> new IllegalStateException("用户 " + userId + " 没有资料行"));
                  profile.changePrivacy(privacy);
                  return profiles.save(profile).getPrivacy();
                }));
    log.info(
        "隐私设置已更新: userId={}, profile={}, professional={}, searchIndexing={}",
        userId,
        saved.profile(),
        saved.professional(),
        saved.searchIndexing());
    return saved;
  }
}
