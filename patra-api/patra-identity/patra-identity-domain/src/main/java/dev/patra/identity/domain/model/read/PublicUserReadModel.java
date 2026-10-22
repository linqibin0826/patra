package dev.patra.identity.domain.model.read;

import dev.patra.identity.domain.model.enums.Occupation;
import dev.patra.identity.domain.model.enums.ProfessionalVisibility;
import dev.patra.identity.domain.model.enums.UserStatus;
import dev.patra.identity.domain.model.vo.profile.Affiliation;
import dev.patra.identity.domain.model.vo.profile.PrivacySettings;
import dev.patra.identity.domain.model.vo.profile.ProfileLink;
import dev.patra.identity.domain.model.vo.profile.ResearchInterest;
import dev.patra.identity.domain.policy.ProfileDisplayPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;

/// 公开主页要的全部信息。`userId`、`status`、`emailVerified`、`privacy` 只用来判可见性与裁剪，不进响应；
/// 对外输出的是 `viewFor` 按请求者裁剪后的 `PublicUserView`。
///
/// @param userId 用户 ID
/// @param username 用户名
/// @param displayName 昵称；资料里没设时已回退成用户名
/// @param status 状态
/// @param emailVerified 邮箱是否已验证
/// @param joinedAt 注册时间
/// @param bio 简介
/// @param links 链接
/// @param occupation 身份大类
/// @param jobTitle 职称
/// @param affiliation 机构
/// @param department 科室
/// @param researchInterests 关注领域
/// @param privacy 隐私设置
@Builder(toBuilder = true)
public record PublicUserReadModel(
    long userId,
    String username,
    String displayName,
    UserStatus status,
    boolean emailVerified,
    Instant joinedAt,
    String bio,
    List<ProfileLink> links,
    Occupation occupation,
    String jobTitle,
    Affiliation affiliation,
    String department,
    List<ResearchInterest> researchInterests,
    PrivacySettings privacy) {

  /// 校验非空；列表 `null` 视为空，隐私 `null` 取默认。
  public PublicUserReadModel {
    Objects.requireNonNull(username, "username 不能为 null");
    Objects.requireNonNull(displayName, "displayName 不能为 null");
    Objects.requireNonNull(status, "status 不能为 null");
    links = links != null ? List.copyOf(links) : List.of();
    researchInterests = researchInterests != null ? List.copyOf(researchInterests) : List.of();
    privacy = privacy != null ? privacy : PrivacySettings.defaults();
  }

  /// 实际能被谁看到：账号不是 `ACTIVE` 或邮箱未验证时只有本人；否则按用户设的主页可见性。
  ///
  /// @return 可见范围
  public PublicAudience audience() {
    if (status != UserStatus.ACTIVE || !emailVerified) {
      return PublicAudience.SELF;
    }
    return switch (privacy.profile()) {
      case PUBLIC -> PublicAudience.PUBLIC;
      case SIGNED_IN -> PublicAudience.SIGNED_IN;
      case SELF -> PublicAudience.SELF;
    };
  }

  /// 请求者能不能看到：本人永远能看；`PUBLIC` 谁都能看；`SIGNED_IN` 要有请求者。
  ///
  /// @param requesterId 请求者的用户 ID，匿名时为空
  /// @return 能看到时为 `true`
  public boolean isVisibleTo(Optional<Long> requesterId) {
    if (isOwner(requesterId)) {
      return true;
    }
    return switch (audience()) {
      case PUBLIC -> true;
      case SIGNED_IN -> requesterId.isPresent();
      case SELF -> false;
    };
  }

  /// 按请求者裁剪：专业背景在本人或可见性为公开时给出；链接只在邮箱已验证时给出（对本人也一样）；
  /// 只有谁都能看且用户允许收录时 `indexable` 为真。
  ///
  /// @param requesterId 请求者的用户 ID，匿名时为空
  /// @param display 展示规则（加入时间的时区）
  /// @return 裁剪后的视图
  /// @throws IllegalStateException 请求者看不到这个主页
  public PublicUserView viewFor(Optional<Long> requesterId, ProfileDisplayPolicy display) {
    if (!isVisibleTo(requesterId)) {
      throw new IllegalStateException("请求者看不到这个主页: userId=" + userId);
    }
    boolean showProfessional =
        isOwner(requesterId) || privacy.professional() == ProfessionalVisibility.PUBLIC;
    return PublicUserView.builder()
        .username(username)
        .displayName(displayName)
        .audience(audience())
        .joinedMonth(joinedAt == null ? null : display.joinedMonth(joinedAt))
        .bio(bio)
        .links(emailVerified ? links : List.of())
        .professional(showProfessional ? professional() : null)
        .researchInterests(researchInterests)
        .indexable(audience() == PublicAudience.PUBLIC && privacy.searchIndexing())
        .build();
  }

  /// 专业背景四项；四项都为空时为 `null`。
  ///
  /// @return 专业背景
  private PublicUserView.Professional professional() {
    if (occupation == null && jobTitle == null && affiliation == null && department == null) {
      return null;
    }
    return PublicUserView.Professional.of(occupation, jobTitle, affiliation, department);
  }

  /// 请求者是不是本人。
  ///
  /// @param requesterId 请求者
  /// @return 是本人时为 `true`
  private boolean isOwner(Optional<Long> requesterId) {
    return requesterId.filter(id -> id == userId).isPresent();
  }
}
