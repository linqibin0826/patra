package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.adapter.account.rest.converter.AvatarUrls;
import dev.patra.identity.domain.account.model.enums.Occupation;
import dev.patra.identity.domain.account.model.read.PublicAudience;
import dev.patra.identity.domain.account.model.read.PublicUserView;
import java.util.List;
import lombok.Builder;

/// 公开主页的响应体，已按请求者裁剪。不含邮箱、状态、任何设置原值；`indexable` 是给门户输出
/// `noindex` 的渲染指令。用户 ID 从 PAP-108 起带上（v0.13 决策 E，修订 PAP-95 的「不含 ID」）。
///
/// @param userId 用户 ID（JSON 里是字符串）
/// @param username 用户名
/// @param displayName 昵称，没设时等于用户名
/// @param avatarUrl 头像地址（identity 相对路径，带版本），没上传时为 `null`
/// @param avatarPalette 默认头像色号（0–11），服务端按用户 ID 算好
/// @param audience 实际能被谁看到：`PUBLIC` / `SIGNED_IN` / `SELF`（门户在本人视角据此提示）
/// @param joinedAt 加入时间，`YYYY-MM`
/// @param bio 简介
/// @param links 链接；邮箱未验证时为空
/// @param professional 专业背景；不可见或都没填时为 `null`
/// @param researchInterests 关注领域
/// @param indexable 是否允许搜索引擎收录
@Builder
public record PublicUserResponse(
    Long userId,
    String username,
    String displayName,
    String avatarUrl,
    int avatarPalette,
    PublicAudience audience,
    String joinedAt,
    String bio,
    List<LinkResponse> links,
    ProfessionalResponse professional,
    List<ResearchInterestResponse> researchInterests,
    boolean indexable) {

  /// 列表防御性拷贝。
  public PublicUserResponse {
    links = links != null ? List.copyOf(links) : List.of();
    researchInterests = researchInterests != null ? List.copyOf(researchInterests) : List.of();
  }

  /// 从裁剪后的视图映射。
  ///
  /// @param view 公开主页视图
  /// @return 响应
  public static PublicUserResponse from(PublicUserView view) {
    return PublicUserResponse.builder()
        .userId(view.userId())
        .username(view.username())
        .displayName(view.displayName())
        .avatarUrl(AvatarUrls.of(view.avatar()))
        .avatarPalette(view.avatarPalette())
        .audience(view.audience())
        .joinedAt(view.joinedMonth() == null ? null : view.joinedMonth().toString())
        .bio(view.bio())
        .links(view.links().stream().map(LinkResponse::from).toList())
        .professional(ProfessionalResponse.from(view.professional()))
        .researchInterests(
            view.researchInterests().stream().map(ResearchInterestResponse::from).toList())
        .indexable(view.indexable())
        .build();
  }

  /// 专业背景：自述，按原样展示。
  ///
  /// @param occupation 身份大类
  /// @param jobTitle 职称
  /// @param organization 机构
  /// @param department 科室
  public record ProfessionalResponse(
      Occupation occupation,
      String jobTitle,
      OrganizationResponse organization,
      String department) {

    /// 从视图映射。
    ///
    /// @param professional 专业背景，可为 `null`
    /// @return 响应；不可见或都没填时为 `null`
    public static ProfessionalResponse from(PublicUserView.Professional professional) {
      if (professional == null) {
        return null;
      }
      return new ProfessionalResponse(
          professional.occupation(),
          professional.jobTitle(),
          OrganizationResponse.from(professional.affiliation()),
          professional.department());
    }
  }
}
