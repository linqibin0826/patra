package dev.patra.identity.domain.account.model.read;

import dev.patra.identity.domain.account.model.enums.Occupation;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.model.vo.profile.Affiliation;
import dev.patra.identity.domain.account.model.vo.profile.ProfileLink;
import dev.patra.identity.domain.account.model.vo.profile.ResearchInterest;
import java.time.YearMonth;
import java.util.List;
import lombok.Builder;

/// 按请求者裁剪之后的公开主页：不含邮箱、状态、任何设置原值。用户 ID 从 PAP-108 起带上（门户凭它取该用户的
/// 公开收藏夹，v0.13 决策 E）。
///
/// @param userId 用户 ID
/// @param username 用户名
/// @param displayName 昵称（已回退）
/// @param avatar 头像，没上传时为 `null`
/// @param avatarPalette 默认头像色号（0–11），服务端按用户 ID 算好
/// @param audience 实际能被谁看到，门户在本人视角据此提示
/// @param joinedMonth 加入时间，只到月
/// @param bio 简介
/// @param links 链接；邮箱未验证时为空
/// @param professional 专业背景；不可见或四项都为空时为 `null`
/// @param researchInterests 关注领域
/// @param indexable 门户要不要允许搜索引擎收录（为假时输出 `noindex`）
@Builder
public record PublicUserView(
    long userId,
    String username,
    String displayName,
    Avatar avatar,
    int avatarPalette,
    PublicAudience audience,
    YearMonth joinedMonth,
    String bio,
    List<ProfileLink> links,
    Professional professional,
    List<ResearchInterest> researchInterests,
    boolean indexable) {

  /// 列表防御性拷贝，`null` 视为空。
  public PublicUserView {
    links = links != null ? List.copyOf(links) : List.of();
    researchInterests = researchInterests != null ? List.copyOf(researchInterests) : List.of();
  }

  /// 专业背景：自述，按原样展示，不带认证外观。
  ///
  /// @param occupation 身份大类
  /// @param jobTitle 职称
  /// @param affiliation 机构
  /// @param department 科室
  public record Professional(
      Occupation occupation, String jobTitle, Affiliation affiliation, String department) {

    /// 工厂方法。
    ///
    /// @param occupation 身份大类
    /// @param jobTitle 职称
    /// @param affiliation 机构
    /// @param department 科室
    /// @return 专业背景
    public static Professional of(
        Occupation occupation, String jobTitle, Affiliation affiliation, String department) {
      return new Professional(occupation, jobTitle, affiliation, department);
    }
  }
}
