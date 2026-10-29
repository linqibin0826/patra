package dev.patra.identity.adapter.account.rest.request;

import dev.patra.identity.domain.account.model.vo.profile.LinkDraft;
import dev.patra.identity.domain.account.model.vo.profile.ProfileDraft;
import java.util.List;

/// `PUT /me/profile` 的请求体：整份替换，省略的字段等于传 `null`（清空）。校验在应用层，这里只搬运原始输入。
///
/// @param displayName 昵称，`null` 或空白表示跟随用户名
/// @param bio 简介
/// @param links 链接
/// @param occupation 身份大类
/// @param jobTitle 职称
/// @param organization 机构：`{ rorId }`（名称由 catalog 给）或 `{ name }`（自由文本）
/// @param department 科室
/// @param researchInterests 关注领域的 MeSH UI，按用户排好的顺序
public record UpdateProfileRequest(
    String displayName,
    String bio,
    List<LinkRequest> links,
    String occupation,
    String jobTitle,
    OrganizationRequest organization,
    String department,
    List<String> researchInterests) {

  /// 列表防御性拷贝。`null` 元素换成空值，由规则报 `REQUIRED`（`List.copyOf` 不收 `null`）。
  public UpdateProfileRequest {
    links =
        links == null
            ? null
            : List.copyOf(
                links.stream()
                    .map(link -> link != null ? link : new LinkRequest(null, null))
                    .toList());
    researchInterests =
        researchInterests == null
            ? null
            : List.copyOf(researchInterests.stream().map(ui -> ui != null ? ui : "").toList());
  }

  /// 转成领域的资料草稿。
  ///
  /// @return 草稿
  public ProfileDraft toDraft() {
    return ProfileDraft.builder()
        .displayName(displayName)
        .bio(bio)
        .links(
            links == null
                ? null
                : links.stream().map(link -> LinkDraft.of(link.title(), link.url())).toList())
        .occupation(occupation)
        .jobTitle(jobTitle)
        .organizationRorId(organization == null ? null : organization.rorId())
        .organizationName(organization == null ? null : organization.name())
        .department(department)
        .researchInterests(researchInterests)
        .build();
  }

  /// 一条链接。
  ///
  /// @param title 标题，可空
  /// @param url URL
  public record LinkRequest(String title, String url) {}

  /// 机构。
  ///
  /// @param rorId 裸 ROR ID
  /// @param name 名称（只在没有 ROR ID 时作为自由文本）
  public record OrganizationRequest(String rorId, String name) {}
}
