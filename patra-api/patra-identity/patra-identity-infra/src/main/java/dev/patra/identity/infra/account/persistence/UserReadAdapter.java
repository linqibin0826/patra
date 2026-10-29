package dev.patra.identity.infra.account.persistence;

import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.AvatarOwner;
import dev.patra.identity.domain.account.model.read.PublicUserReadModel;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.identity.domain.account.model.read.UserSummaryReadModel;
import dev.patra.identity.domain.account.model.vo.DisplayNamePrefix;
import dev.patra.identity.domain.account.model.vo.Username;
import dev.patra.identity.domain.account.model.vo.UsernamePrefix;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.model.vo.profile.Bio;
import dev.patra.identity.domain.account.model.vo.profile.Department;
import dev.patra.identity.domain.account.model.vo.profile.JobTitle;
import dev.patra.identity.domain.account.policy.UsernameChangePolicy;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import dev.patra.identity.infra.account.persistence.dao.UserDao;
import dev.patra.identity.infra.account.persistence.dao.UserProfileDao;
import dev.patra.identity.infra.account.persistence.dao.UserProfileLinkDao;
import dev.patra.identity.infra.account.persistence.dao.UserResearchInterestDao;
import dev.patra.identity.infra.account.persistence.entity.UserEntity;
import dev.patra.identity.infra.account.persistence.entity.UserProfileEntity;
import dev.patra.identity.infra.account.persistence.mapper.UserProfileJpaMapper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  /// 按用户 ID 批量读摘要原始数据：用户表、资料表各一条 `IN` 查询，内存里按用户 ID 拼。
  ///
  /// @param userIds 用户 ID
  /// @return 存在的那些，不保证顺序
  @Override
  public List<UserSummaryReadModel> findSummaries(Collection<Long> userIds) {
    if (userIds.isEmpty()) {
      return List.of();
    }
    return summariesOf(userDao.findAllById(userIds));
  }

  /// 按用户名前缀读正常账号：`[前缀, 前缀末字符加一)` 的范围查询，资料行一次 `IN` 取齐，保持查询的字节序。
  ///
  /// @param prefix 前缀
  /// @param limit 最多几个
  /// @return 摘要原始数据，按用户名字节序
  @Override
  public List<UserSummaryReadModel> findActiveSummariesByUsernamePrefix(
      UsernamePrefix prefix, int limit) {
    String from = prefix.value();
    return summariesOf(userDao.findActiveByUsernameRange(from, upperBound(from), limit));
  }

  /// 按昵称前缀读正常账号：`[前缀, 前缀末码点加一)` 的范围查询，资料行一次 `IN` 取齐，保持查询的顺序。
  ///
  /// @param prefix 昵称前缀，已转小写
  /// @param limit 最多几个
  /// @return 摘要原始数据，按小写昵称的码点序
  @Override
  public List<UserSummaryReadModel> findActiveSummariesByDisplayNamePrefix(
      DisplayNamePrefix prefix, int limit) {
    String from = prefix.value();
    return summariesOf(userDao.findActiveByDisplayNameRange(from, upperBound(from), limit));
  }

  /// 按当前用户名批量读摘要原始数据：用户表、资料表各一条 `IN` 查询。
  ///
  /// @param usernames 用户名
  /// @return 存在的那些，不保证顺序
  @Override
  public List<UserSummaryReadModel> findSummariesByUsernames(Collection<Username> usernames) {
    return summariesOf(userDao.findByUsernameIn(usernames.stream().map(Username::value).toList()));
  }

  /// 按用户 ID 读头像归属：用户行加资料行里的头像键，不读子表。
  ///
  /// @param userId 用户 ID
  /// @return 归属
  @Override
  public Optional<AvatarOwner> findAvatarOwner(long userId) {
    return userDao
        .findById(userId)
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

  /// 用户行配上资料行（一次 `IN` 取齐）拼成摘要原始数据，按传入的顺序。
  ///
  /// @param users 用户行
  /// @return 摘要原始数据
  private List<UserSummaryReadModel> summariesOf(List<UserEntity> users) {
    if (users.isEmpty()) {
      return List.of();
    }
    Map<Long, UserProfileEntity> profiles =
        profileDao.findByUserIdIn(users.stream().map(UserEntity::getId).toList()).stream()
            .collect(Collectors.toMap(UserProfileEntity::getUserId, Function.identity()));
    return users.stream().map(user -> toSummary(user, profiles.get(user.getId()))).toList();
  }

  /// 码点序（`COLLATE "C"` 下即 UTF-8 字节序）里排在所有以前缀开头的字符串之后的最小字符串：末码点加一，
  /// 代理对按码点算。用户名只有 ASCII，昵称前缀已排除代理项与未分配码点，加一不会越界。
  ///
  /// @param prefix 前缀
  /// @return 上界（不含）
  private static String upperBound(String prefix) {
    int last = prefix.codePointBefore(prefix.length());
    return prefix.substring(0, prefix.length() - Character.charCount(last))
        + Character.toString(last + 1);
  }

  /// 把用户行和资料行拼成摘要原始数据：昵称取原值，不回退（回退与已注销的占位在读模型里做）。
  ///
  /// @param user 用户行
  /// @param profile 资料行，缺失时为 `null`
  /// @return 摘要原始数据
  private static UserSummaryReadModel toSummary(UserEntity user, UserProfileEntity profile) {
    if (profile == null) {
      throw new IllegalStateException("用户 " + user.getId() + " 没有资料行");
    }
    return UserSummaryReadModel.builder()
        .userId(user.getId())
        .username(user.getUsername())
        .displayName(profile.getDisplayName())
        .avatar(avatarOf(user, profile))
        .status(UserStatus.valueOf(user.getStatus()))
        .emailVerified(user.getEmailVerifiedAt() != null)
        .profileVisibility(ProfileVisibility.valueOf(profile.getProfileVisibility()))
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
