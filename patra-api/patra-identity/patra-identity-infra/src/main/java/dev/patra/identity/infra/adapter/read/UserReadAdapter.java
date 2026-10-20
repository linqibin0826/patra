package dev.patra.identity.infra.adapter.read;

import dev.patra.identity.domain.model.enums.UserStatus;
import dev.patra.identity.domain.model.read.PublicUserReadModel;
import dev.patra.identity.domain.model.read.UserAccountReadModel;
import dev.patra.identity.domain.model.vo.Username;
import dev.patra.identity.domain.policy.UsernameChangePolicy;
import dev.patra.identity.domain.port.read.UserReadPort;
import dev.patra.identity.infra.adapter.persistence.dao.UserDao;
import dev.patra.identity.infra.adapter.persistence.dao.UserProfileDao;
import dev.patra.identity.infra.adapter.persistence.entity.UserEntity;
import dev.patra.identity.infra.adapter.persistence.entity.UserProfileEntity;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 前台用户读端口的 JPA 实现：用户表加资料表，两次查询拼起来。
///
/// 资料行缺失是数据不一致（注册时同事务建的），抛 `IllegalStateException` 而不是静默回退。
@Component
@RequiredArgsConstructor
public class UserReadAdapter implements UserReadPort {

  private final UserDao userDao;
  private final UserProfileDao profileDao;
  private final UsernameChangePolicy usernameChangePolicy;

  /// 按 ID 读账号信息。
  ///
  /// @param userId 用户 ID
  /// @return 账号信息
  @Override
  public Optional<UserAccountReadModel> findAccount(long userId) {
    return userDao.findById(userId).map(this::toAccount);
  }

  /// 按用户名读公开身份卡。
  ///
  /// @param username 用户名
  /// @return 身份卡
  @Override
  public Optional<PublicUserReadModel> findPublicIdentity(Username username) {
    return userDao.findByUsername(username.value()).map(this::toPublicIdentity);
  }

  /// 按用户 ID 读公开身份卡。
  ///
  /// @param userId 用户 ID
  /// @return 身份卡
  @Override
  public Optional<PublicUserReadModel> findPublicIdentityByUserId(long userId) {
    return userDao.findById(userId).map(this::toPublicIdentity);
  }

  /// 把用户行和资料行拼成账号读模型：昵称为空回退成用户名，下次改名时间按策略算。
  ///
  /// @param user 用户行
  /// @return 读模型
  private UserAccountReadModel toAccount(UserEntity user) {
    return UserAccountReadModel.builder()
        .userId(user.getId())
        .email(user.getEmail())
        .emailVerified(user.getEmailVerifiedAt() != null)
        .username(user.getUsername())
        .displayName(displayNameOf(user))
        .status(UserStatus.valueOf(user.getStatus()))
        .deletionDueAt(user.getDeletionDueAt())
        .nextUsernameChangeAt(usernameChangePolicy.nextChangeAt(user.getUsernameChangedAt()))
        .build();
  }

  /// 把用户行和资料行拼成公开身份卡。
  ///
  /// @param user 用户行
  /// @return 身份卡
  private PublicUserReadModel toPublicIdentity(UserEntity user) {
    return PublicUserReadModel.builder()
        .userId(user.getId())
        .username(user.getUsername())
        .displayName(displayNameOf(user))
        .status(UserStatus.valueOf(user.getStatus()))
        .emailVerified(user.getEmailVerifiedAt() != null)
        .build();
  }

  /// 资料里的昵称，没设时用用户名。
  ///
  /// @param user 用户行
  /// @return 昵称
  private String displayNameOf(UserEntity user) {
    UserProfileEntity profile =
        profileDao
            .findByUserId(user.getId())
            .orElseThrow(() -> new IllegalStateException("用户 " + user.getId() + " 没有资料行"));
    return profile.getDisplayName() == null ? user.getUsername() : profile.getDisplayName();
  }
}
