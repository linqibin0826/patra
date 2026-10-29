package dev.patra.identity.domain.account.model.vo.profile;

import dev.patra.identity.domain.account.model.enums.Occupation;
import dev.patra.identity.domain.account.model.vo.DisplayName;
import java.util.List;
import lombok.Builder;

/// `ProfilePolicy.parse` 的结果：规范化之后的值对象，机构与关注领域还没有名称快照（由处理器经 catalog 补上）。
///
/// @param displayName 昵称，`null` 表示跟随用户名
/// @param bio 简介
/// @param links 链接
/// @param occupation 身份大类
/// @param jobTitle 职称
/// @param organizationRorId 机构的 ROR ID；`null` 表示没有或用自由文本
/// @param organizationName 手填的机构名，只在 `organizationRorId` 为空时有值
/// @param department 科室
/// @param researchInterests 关注领域的主题词 UI，按用户排好的顺序
@Builder
public record ParsedProfile(
    DisplayName displayName,
    Bio bio,
    List<ProfileLink> links,
    Occupation occupation,
    JobTitle jobTitle,
    RorId organizationRorId,
    String organizationName,
    Department department,
    List<MeshUi> researchInterests) {

  /// 列表防御性拷贝，`null` 视为空。
  public ParsedProfile {
    links = links != null ? List.copyOf(links) : List.of();
    researchInterests = researchInterests != null ? List.copyOf(researchInterests) : List.of();
  }
}
