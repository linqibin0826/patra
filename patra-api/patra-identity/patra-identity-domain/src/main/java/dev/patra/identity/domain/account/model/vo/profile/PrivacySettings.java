package dev.patra.identity.domain.account.model.vo.profile;

import dev.patra.identity.domain.account.model.enums.ProfessionalVisibility;
import dev.patra.identity.domain.account.model.enums.ProfileVisibility;
import java.util.Objects;

/// 隐私设置。邮箱永不展示，没有开关。
///
/// @param profile 主页可见性
/// @param professional 专业背景可见性
/// @param searchIndexing 是否允许搜索引擎收录主页
public record PrivacySettings(
    ProfileVisibility profile, ProfessionalVisibility professional, boolean searchIndexing) {

  /// 校验非空。
  public PrivacySettings {
    Objects.requireNonNull(profile, "profile 不能为 null");
    Objects.requireNonNull(professional, "professional 不能为 null");
  }

  /// 工厂方法。
  ///
  /// @param profile 主页可见性
  /// @param professional 专业背景可见性
  /// @param searchIndexing 是否允许收录
  /// @return 设置
  public static PrivacySettings of(
      ProfileVisibility profile, ProfessionalVisibility professional, boolean searchIndexing) {
    return new PrivacySettings(profile, professional, searchIndexing);
  }

  /// 默认值：主页公开、专业背景公开、不允许收录。
  ///
  /// @return 默认设置
  public static PrivacySettings defaults() {
    return new PrivacySettings(ProfileVisibility.PUBLIC, ProfessionalVisibility.PUBLIC, false);
  }
}
