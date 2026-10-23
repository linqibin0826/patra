package dev.patra.identity.infra.adapter.read;

import dev.patra.identity.domain.model.aggregate.UserProfile;
import dev.patra.identity.domain.model.enums.UserStatus;
import dev.patra.identity.domain.model.read.AvatarOwner;
import dev.patra.identity.domain.model.read.PublicUserReadModel;
import dev.patra.identity.domain.model.read.UserAccountReadModel;
import dev.patra.identity.domain.model.vo.Username;
import dev.patra.identity.domain.model.vo.avatar.Avatar;
import dev.patra.identity.domain.model.vo.profile.Bio;
import dev.patra.identity.domain.model.vo.profile.Department;
import dev.patra.identity.domain.model.vo.profile.JobTitle;
import dev.patra.identity.domain.policy.UsernameChangePolicy;
import dev.patra.identity.domain.port.read.UserReadPort;
import dev.patra.identity.infra.adapter.persistence.converter.mapper.UserProfileJpaMapper;
import dev.patra.identity.infra.adapter.persistence.dao.UserDao;
import dev.patra.identity.infra.adapter.persistence.dao.UserProfileDao;
import dev.patra.identity.infra.adapter.persistence.dao.UserProfileLinkDao;
import dev.patra.identity.infra.adapter.persistence.dao.UserResearchInterestDao;
import dev.patra.identity.infra.adapter.persistence.entity.UserEntity;
import dev.patra.identity.infra.adapter.persistence.entity.UserProfileEntity;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 前台用户读端口的 JPA 实现：账号信息是用户表加资料表；公开主页再加链接与关注领域两张子表。
///
/// 资料行缺失是数据不一致（注册时同事务建的），抛 `IllegalStateException` 而不是静默回退。
@Component
@RequiredArgsConstructor
public class UserReadAdapter implements UserReadPort {

  private final UserDao userDao;
  private final UserProfileDao profileDao;
  private final UserProfileLinkDao linkDao;
  private final UserResearchInterestDao interestDao;
  private final UserProfileJpaMapper profileMapper;
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

  /// 按当前用户名读头像归属：用户行加资料行里的头像键，不读子表。
  ///
  /// @param username 用户名
  /// @return 归属
  @Override
  public Optional<AvatarOwner> findAvatarOwner(Username username) {
    return userDao
        .findByUsername(username.value())
        .map(
            user ->
                AvatarOwner.of(
                    user.getId(),
                    UserStatus.valueOf(user.getStatus()),
                    avatarOf(user, profileOf(user))));
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
        .avatar(avatarOf(user, profileOf(user)))
        .status(UserStatus.valueOf(user.getStatus()))
        .deletionDueAt(user.getDeletionDueAt())
        .nextUsernameChangeAt(usernameChangePolicy.nextChangeAt(user.getUsernameChangedAt()))
        .build();
  }

  /// 把用户行、资料行和两张子表拼成公开主页：昵称为空回退成用户名，加入时间是注册时间。
  ///
  /// @param user 用户行
  /// @return 公开主页读模型
  private PublicUserReadModel toPublicIdentity(UserEntity user) {
    UserProfileEntity row = profileOf(user);
    UserProfile profile =
        profileMapper.toAggregate(
            row,
            linkDao.findByUserIdOrderByPositionAsc(user.getId()),
            interestDao.findByUserIdOrderByPositionAsc(user.getId()));
    return PublicUserReadModel.builder()
        .userId(user.getId())
        .username(user.getUsername())
        .displayName(row.getDisplayName() == null ? user.getUsername() : row.getDisplayName())
        .avatar(profile.getAvatar().orElse(null))
        .status(UserStatus.valueOf(user.getStatus()))
        .emailVerified(user.getEmailVerifiedAt() != null)
        .joinedAt(user.getCreatedAt())
        .bio(profile.getBio().map(Bio::value).orElse(null))
        .links(profile.getLinks())
        .occupation(profile.getOccupation().orElse(null))
        .jobTitle(profile.getJobTitle().map(JobTitle::value).orElse(null))
        .affiliation(profile.getAffiliation().orElse(null))
        .department(profile.getDepartment().map(Department::value).orElse(null))
        .researchInterests(profile.getResearchInterests())
        .privacy(profile.getPrivacy())
        .build();
  }

  /// 资料里的昵称，没设时用用户名。
  ///
  /// @param user 用户行
  /// @return 昵称
  private String displayNameOf(UserEntity user) {
    UserProfileEntity profile = profileOf(user);
    return profile.getDisplayName() == null ? user.getUsername() : profile.getDisplayName();
  }

  /// 资料行里的头像。
  ///
  /// @param user 用户行
  /// @param profile 资料行
  /// @return 头像；没上传时为 `null`
  private static Avatar avatarOf(UserEntity user, UserProfileEntity profile) {
    String key = profile.getAvatarObjectKey();
    return key == null ? null : Avatar.parse(user.getId(), key);
  }

  /// 用户的资料行；缺行是数据不一致（注册时同事务建的）。
  ///
  /// @param user 用户行
  /// @return 资料行
  private UserProfileEntity profileOf(UserEntity user) {
    return profileDao
        .findByUserId(user.getId())
        .orElseThrow(() -> new IllegalStateException("用户 " + user.getId() + " 没有资料行"));
  }
}
