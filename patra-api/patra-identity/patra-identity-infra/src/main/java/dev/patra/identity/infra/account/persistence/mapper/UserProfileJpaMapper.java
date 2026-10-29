package dev.patra.identity.infra.account.persistence.mapper;

import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.enums.Occupation;
import dev.patra.identity.domain.account.model.enums.ProfessionalVisibility;
import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import dev.patra.identity.domain.account.model.vo.DisplayName;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarSize;
import dev.patra.identity.domain.account.model.vo.profile.Affiliation;
import dev.patra.identity.domain.account.model.vo.profile.Bio;
import dev.patra.identity.domain.account.model.vo.profile.Department;
import dev.patra.identity.domain.account.model.vo.profile.JobTitle;
import dev.patra.identity.domain.account.model.vo.profile.MeshUi;
import dev.patra.identity.domain.account.model.vo.profile.PrivacySettings;
import dev.patra.identity.domain.account.model.vo.profile.ProfileLink;
import dev.patra.identity.domain.account.model.vo.profile.ResearchInterest;
import dev.patra.identity.domain.account.model.vo.profile.RorId;
import dev.patra.identity.infra.account.persistence.entity.UserProfileEntity;
import dev.patra.identity.infra.account.persistence.entity.UserProfileLinkEntity;
import dev.patra.identity.infra.account.persistence.entity.UserResearchInterestEntity;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.Mapper;

/// UserProfile 与资料表、链接表、关注领域表三种实体的转换。
///
/// 聚合的取值几乎都是 `Optional` 与值对象，逐字段手写比让 MapStruct 生成更直白；`id`、`version`、
/// `createdAt`、`updatedAt` 原样带上，更新时才能做乐观锁检查。操作人字段由 JPA 审计管理。机构拆成两列：
/// `Registered` → `(rorId, 快照)`，`FreeText` → `(null, 名称)`；读回时 `rorId` 非空即 `Registered`。
/// 子表行的 `position` 就是列表下标。
@Mapper(componentModel = "spring")
public interface UserProfileJpaMapper {

  /// 聚合转资料表实体（不含子表）。
  ///
  /// @param profile 资料
  /// @return 实体
  default UserProfileEntity toEntity(UserProfile profile) {
    PrivacySettings privacy = profile.getPrivacy();
    return UserProfileEntity.builder()
        .id(profile.getId())
        .version(profile.getVersion())
        .createdAt(profile.getCreatedAt())
        .updatedAt(profile.getUpdatedAt())
        .userId(profile.getUserId())
        .displayName(profile.getDisplayName().map(DisplayName::value).orElse(null))
        .bio(profile.getBio().map(Bio::value).orElse(null))
        .occupation(profile.getOccupation().map(Occupation::name).orElse(null))
        .jobTitle(profile.getJobTitle().map(JobTitle::value).orElse(null))
        .organizationRorId(
            profile
                .getAffiliation()
                .filter(Affiliation.Registered.class::isInstance)
                .map(affiliation -> ((Affiliation.Registered) affiliation).rorId().value())
                .orElse(null))
        .organizationName(profile.getAffiliation().map(Affiliation::name).orElse(null))
        .department(profile.getDepartment().map(Department::value).orElse(null))
        .profileVisibility(privacy.profile().name())
        .professionalVisibility(privacy.professional().name())
        .searchIndexing(privacy.searchIndexing())
        .avatarObjectKey(
            profile.getAvatar().map(avatar -> avatar.objectKey(AvatarSize.LARGE)).orElse(null))
        .build();
  }

  /// 聚合的链接转子表实体，`position` 是下标。ID 由仓储分配。
  ///
  /// @param profile 资料
  /// @return 实体
  default List<UserProfileLinkEntity> toLinkEntities(UserProfile profile) {
    List<ProfileLink> links = profile.getLinks();
    List<UserProfileLinkEntity> entities = new ArrayList<>(links.size());
    for (int i = 0; i < links.size(); i++) {
      entities.add(
          UserProfileLinkEntity.builder()
              .userId(profile.getUserId())
              .position((short) i)
              .title(links.get(i).title())
              .url(links.get(i).url())
              .build());
    }
    return entities;
  }

  /// 聚合的关注领域转子表实体，`position` 是下标。ID 由仓储分配。
  ///
  /// @param profile 资料
  /// @return 实体
  default List<UserResearchInterestEntity> toInterestEntities(UserProfile profile) {
    List<ResearchInterest> interests = profile.getResearchInterests();
    List<UserResearchInterestEntity> entities = new ArrayList<>(interests.size());
    for (int i = 0; i < interests.size(); i++) {
      entities.add(
          UserResearchInterestEntity.builder()
              .userId(profile.getUserId())
              .position((short) i)
              .meshUi(interests.get(i).ui().value())
              .meshName(interests.get(i).name())
              .build());
    }
    return entities;
  }

  /// 三种实体拼回聚合。
  ///
  /// @param entity 资料表实体
  /// @param links 链接行，已按 `position` 排好
  /// @param interests 关注领域行，已按 `position` 排好
  /// @return 资料；资料表实体为 `null` 时返回 `null`
  default UserProfile toAggregate(
      UserProfileEntity entity,
      List<UserProfileLinkEntity> links,
      List<UserResearchInterestEntity> interests) {
    if (entity == null) {
      return null;
    }
    return UserProfile.restore()
        .id(entity.getId())
        .userId(entity.getUserId())
        .displayName(
            entity.getDisplayName() == null ? null : new DisplayName(entity.getDisplayName()))
        .bio(entity.getBio() == null ? null : new Bio(entity.getBio()))
        .links(toLinks(links))
        .occupation(
            entity.getOccupation() == null ? null : Occupation.valueOf(entity.getOccupation()))
        .jobTitle(entity.getJobTitle() == null ? null : JobTitle.of(entity.getJobTitle()))
        .affiliation(toAffiliation(entity.getOrganizationRorId(), entity.getOrganizationName()))
        .department(entity.getDepartment() == null ? null : Department.of(entity.getDepartment()))
        .researchInterests(toInterests(interests))
        .privacy(
            PrivacySettings.of(
                ProfileVisibility.valueOf(entity.getProfileVisibility()),
                ProfessionalVisibility.valueOf(entity.getProfessionalVisibility()),
                entity.isSearchIndexing()))
        .avatar(
            entity.getAvatarObjectKey() == null
                ? null
                : Avatar.parse(entity.getUserId(), entity.getAvatarObjectKey()))
        .version(entity.getVersion())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /// 链接行转值对象。
  ///
  /// @param rows 已排序的行
  /// @return 链接
  default List<ProfileLink> toLinks(List<UserProfileLinkEntity> rows) {
    return rows.stream().map(row -> ProfileLink.of(row.getTitle(), row.getUrl())).toList();
  }

  /// 关注领域行转值对象。
  ///
  /// @param rows 已排序的行
  /// @return 关注领域
  default List<ResearchInterest> toInterests(List<UserResearchInterestEntity> rows) {
    return rows.stream()
        .map(row -> ResearchInterest.of(new MeshUi(row.getMeshUi()), row.getMeshName()))
        .toList();
  }

  /// 机构两列转值对象。
  ///
  /// @param rorId 裸 ROR ID
  /// @param name 名称
  /// @return 机构；两列都为空时为 `null`
  default Affiliation toAffiliation(String rorId, String name) {
    if (name == null) {
      return null;
    }
    return rorId == null
        ? Affiliation.FreeText.of(name)
        : Affiliation.Registered.of(new RorId(rorId), name);
  }
}
