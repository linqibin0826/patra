package dev.patra.identity.domain.account.model.vo.profile;

import java.util.List;
import lombok.Builder;

/// `PUT /me/profile` 的原始输入，交给 `ProfilePolicy.parse` 校验。字段都可空；列表 `null` 视为空。
///
/// @param displayName 昵称
/// @param bio 简介
/// @param links 链接
/// @param occupation 身份大类
/// @param jobTitle 职称
/// @param organizationRorId 机构的 ROR ID
/// @param organizationName 机构名（只在没有 ROR ID 时作为自由文本）
/// @param department 科室
/// @param researchInterests 关注领域的 MeSH UI，按用户排好的顺序
@Builder
public record ProfileDraft(
    String displayName,
    String bio,
    List<LinkDraft> links,
    String occupation,
    String jobTitle,
    String organizationRorId,
    String organizationName,
    String department,
    List<String> researchInterests) {

  /// 列表防御性拷贝，`null` 视为空；元素可以是 `null`（由规则报 `REQUIRED`）。
  public ProfileDraft {
    links = links != null ? List.copyOf(nullSafe(links)) : List.of();
    researchInterests =
        researchInterests != null ? copyAllowingNulls(researchInterests) : List.of();
  }

  /// 链接列表里的 `null` 元素换成空草稿，由规则报 `REQUIRED`。
  ///
  /// @param links 链接
  /// @return 不含 `null` 的列表
  private static List<LinkDraft> nullSafe(List<LinkDraft> links) {
    return links.stream().map(link -> link != null ? link : LinkDraft.of(null, null)).toList();
  }

  /// 拷贝一份允许 `null` 元素的不可变列表（`List.copyOf` 不收 `null`）。
  ///
  /// @param values 原列表
  /// @return 不可变副本
  private static List<String> copyAllowingNulls(List<String> values) {
    return values.stream().toList();
  }
}
