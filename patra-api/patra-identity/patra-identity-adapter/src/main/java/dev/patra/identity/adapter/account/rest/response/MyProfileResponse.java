package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.enums.Occupation;
import dev.patra.identity.domain.account.model.vo.DisplayName;
import dev.patra.identity.domain.account.model.vo.profile.Bio;
import dev.patra.identity.domain.account.model.vo.profile.Department;
import dev.patra.identity.domain.account.model.vo.profile.JobTitle;
import java.util.List;
import lombok.Builder;

/// `GET /me/profile` 与 `PUT /me/profile` 的响应：本人资料的原值。空字段输出 `null`，空列表输出 `[]`。
///
/// @param displayName 昵称原值，`null` 表示跟随用户名（回退后的昵称看 `/auth/me`）
/// @param bio 简介
/// @param links 链接
/// @param occupation 身份大类
/// @param jobTitle 职称
/// @param organization 机构
/// @param department 科室
/// @param researchInterests 关注领域
@Builder
public record MyProfileResponse(
    String displayName,
    String bio,
    List<LinkResponse> links,
    Occupation occupation,
    String jobTitle,
    OrganizationResponse organization,
    String department,
    List<ResearchInterestResponse> researchInterests) {

  /// 列表防御性拷贝。
  public MyProfileResponse {
    links = links != null ? List.copyOf(links) : List.of();
    researchInterests = researchInterests != null ? List.copyOf(researchInterests) : List.of();
  }

  /// 从聚合映射。
  ///
  /// @param profile 资料
  /// @return 响应
  public static MyProfileResponse from(UserProfile profile) {
    return MyProfileResponse.builder()
        .displayName(profile.getDisplayName().map(DisplayName::value).orElse(null))
        .bio(profile.getBio().map(Bio::value).orElse(null))
        .links(profile.getLinks().stream().map(LinkResponse::from).toList())
        .occupation(profile.getOccupation().orElse(null))
        .jobTitle(profile.getJobTitle().map(JobTitle::value).orElse(null))
        .organization(OrganizationResponse.from(profile.getAffiliation().orElse(null)))
        .department(profile.getDepartment().map(Department::value).orElse(null))
        .researchInterests(
            profile.getResearchInterests().stream().map(ResearchInterestResponse::from).toList())
        .build();
  }
}
