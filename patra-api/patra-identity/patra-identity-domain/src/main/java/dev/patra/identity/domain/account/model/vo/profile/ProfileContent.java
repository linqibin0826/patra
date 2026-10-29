package dev.patra.identity.domain.account.model.vo.profile;

import dev.patra.identity.domain.account.model.enums.Occupation;
import dev.patra.identity.domain.account.model.vo.DisplayName;
import java.util.List;
import lombok.Builder;

/// 校验并解析完名称快照之后的一整份资料，`UserProfile.edit` 用它整体替换。除两个列表外都可空。
///
/// @param displayName 昵称，`null` 表示跟随用户名
/// @param bio 简介
/// @param links 链接
/// @param occupation 身份大类
/// @param jobTitle 职称
/// @param affiliation 机构
/// @param department 科室
/// @param researchInterests 关注领域，按用户排好的顺序
@Builder
public record ProfileContent(
    DisplayName displayName,
    Bio bio,
    List<ProfileLink> links,
    Occupation occupation,
    JobTitle jobTitle,
    Affiliation affiliation,
    Department department,
    List<ResearchInterest> researchInterests) {

  /// 列表防御性拷贝，`null` 视为空。
  public ProfileContent {
    links = links != null ? List.copyOf(links) : List.of();
    researchInterests = researchInterests != null ? List.copyOf(researchInterests) : List.of();
  }
}
