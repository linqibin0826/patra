package dev.patra.identity.domain.model.aggregate;

import dev.patra.identity.domain.model.enums.Occupation;
import dev.patra.identity.domain.model.vo.DisplayName;
import dev.patra.identity.domain.model.vo.profile.Affiliation;
import dev.patra.identity.domain.model.vo.profile.Bio;
import dev.patra.identity.domain.model.vo.profile.Department;
import dev.patra.identity.domain.model.vo.profile.JobTitle;
import dev.patra.identity.domain.model.vo.profile.MeshUi;
import dev.patra.identity.domain.model.vo.profile.PrivacySettings;
import dev.patra.identity.domain.model.vo.profile.ProfileContent;
import dev.patra.identity.domain.model.vo.profile.ProfileLink;
import dev.patra.identity.domain.model.vo.profile.ResearchInterest;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/// 资料聚合根，与用户一对一，注册时随用户一起建。
///
/// 昵称为空表示「跟随用户名」，读模型把它回退成当前用户名。资料整体替换（`edit`），隐私设置单独改
/// （`changePrivacy`）。不变式：链接最多 5 条，关注领域最多 10 个且主题词不重复。
@Getter
public final class UserProfile {

  /// 链接最多几条。
  public static final int MAX_LINKS = 5;

  /// 关注领域最多几个。
  public static final int MAX_RESEARCH_INTERESTS = 10;

  private final Long id;
  private final Long userId;

  @Getter(AccessLevel.NONE)
  private DisplayName displayName;

  @Getter(AccessLevel.NONE)
  private Bio bio;

  @Getter(AccessLevel.NONE)
  private List<ProfileLink> links;

  @Getter(AccessLevel.NONE)
  private Occupation occupation;

  @Getter(AccessLevel.NONE)
  private JobTitle jobTitle;

  @Getter(AccessLevel.NONE)
  private Affiliation affiliation;

  @Getter(AccessLevel.NONE)
  private Department department;

  @Getter(AccessLevel.NONE)
  private List<ResearchInterest> researchInterests;

  private PrivacySettings privacy;
  private final Long version;
  private final Instant createdAt;
  private final Instant updatedAt;

  /// 全参构造，守不变式。从库里恢复走 `restore()` 构建器；`privacy` 为空取默认，列表为空取空列表。
  ///
  /// @param id 资料 ID，新资料为 `null`
  /// @param userId 用户 ID
  /// @param displayName 昵称
  /// @param bio 简介
  /// @param links 链接
  /// @param occupation 身份大类
  /// @param jobTitle 职称
  /// @param affiliation 机构
  /// @param department 科室
  /// @param researchInterests 关注领域
  /// @param privacy 隐私设置
  /// @param version 乐观锁版本
  /// @param createdAt 创建时间
  /// @param updatedAt 更新时间
  @Builder(builderMethodName = "restore")
  private UserProfile(
      Long id,
      Long userId,
      DisplayName displayName,
      Bio bio,
      List<ProfileLink> links,
      Occupation occupation,
      JobTitle jobTitle,
      Affiliation affiliation,
      Department department,
      List<ResearchInterest> researchInterests,
      PrivacySettings privacy,
      Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.userId = Objects.requireNonNull(userId, "userId 不能为 null");
    this.privacy = privacy != null ? privacy : PrivacySettings.defaults();
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    apply(
        ProfileContent.builder()
            .displayName(displayName)
            .bio(bio)
            .links(links)
            .occupation(occupation)
            .jobTitle(jobTitle)
            .affiliation(affiliation)
            .department(department)
            .researchInterests(researchInterests)
            .build());
  }

  /// 给新用户建一份空资料：没有昵称，隐私取默认。ID 由仓储在保存时分配。
  ///
  /// @param userId 用户 ID
  /// @return 资料
  public static UserProfile create(long userId) {
    return UserProfile.restore().userId(userId).build();
  }

  /// 整体替换资料（昵称、简介、链接、专业背景、关注领域）。
  ///
  /// @param content 校验并解析完名称快照的资料
  /// @return 昵称是否变了
  /// @throws IllegalArgumentException 链接超过 5 条、关注领域超过 10 个或主题词重复
  public boolean edit(ProfileContent content) {
    Objects.requireNonNull(content, "content 不能为 null");
    boolean displayNameChanged = !Objects.equals(displayName, content.displayName());
    apply(content);
    return displayNameChanged;
  }

  /// 注销到期时清空资料：内容全部清掉（昵称、简介、链接、专业背景、关注领域），隐私回默认；行本身保留，
  /// 读模型要求资料行与用户一对一存在。PAP-97 的头像键在这里一起清。
  public void erase() {
    apply(ProfileContent.builder().build());
    privacy = PrivacySettings.defaults();
  }

  /// 替换隐私设置。
  ///
  /// @param privacy 新设置
  public void changePrivacy(PrivacySettings privacy) {
    this.privacy = Objects.requireNonNull(privacy, "privacy 不能为 null");
  }

  /// 昵称。
  ///
  /// @return 昵称；没有设过时为空，展示时用用户名
  public Optional<DisplayName> getDisplayName() {
    return Optional.ofNullable(displayName);
  }

  /// 简介。
  ///
  /// @return 简介
  public Optional<Bio> getBio() {
    return Optional.ofNullable(bio);
  }

  /// 链接，按用户排好的顺序。
  ///
  /// @return 不可变列表
  public List<ProfileLink> getLinks() {
    return links;
  }

  /// 身份大类。
  ///
  /// @return 身份大类
  public Optional<Occupation> getOccupation() {
    return Optional.ofNullable(occupation);
  }

  /// 职称。
  ///
  /// @return 职称
  public Optional<JobTitle> getJobTitle() {
    return Optional.ofNullable(jobTitle);
  }

  /// 机构。
  ///
  /// @return 机构
  public Optional<Affiliation> getAffiliation() {
    return Optional.ofNullable(affiliation);
  }

  /// 科室。
  ///
  /// @return 科室
  public Optional<Department> getDepartment() {
    return Optional.ofNullable(department);
  }

  /// 关注领域，按用户排好的顺序。
  ///
  /// @return 不可变列表
  public List<ResearchInterest> getResearchInterests() {
    return researchInterests;
  }

  /// 只输出 ID 和用户 ID。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "UserProfile[id=" + id + ", userId=" + userId + "]";
  }

  /// 校验不变式后写入字段。
  ///
  /// @param content 资料
  private void apply(ProfileContent content) {
    if (content.links().size() > MAX_LINKS) {
      throw new IllegalArgumentException("链接最多 " + MAX_LINKS + " 条");
    }
    List<ResearchInterest> interests = content.researchInterests();
    if (interests.size() > MAX_RESEARCH_INTERESTS) {
      throw new IllegalArgumentException("关注领域最多 " + MAX_RESEARCH_INTERESTS + " 个");
    }
    Set<MeshUi> seen = new HashSet<>();
    for (ResearchInterest interest : interests) {
      if (!seen.add(interest.ui())) {
        throw new IllegalArgumentException("关注领域重复: " + interest.ui().value());
      }
    }
    this.displayName = content.displayName();
    this.bio = content.bio();
    this.links = content.links();
    this.occupation = content.occupation();
    this.jobTitle = content.jobTitle();
    this.affiliation = content.affiliation();
    this.department = content.department();
    this.researchInterests = interests;
  }
}
