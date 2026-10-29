package dev.patra.identity.domain.account.model.enums;

/// 专业背景（身份大类、职称、机构、科室）的可见性。
public enum ProfessionalVisibility {
  /// 和主页一样，能看到主页的人都能看到。
  PUBLIC,
  /// 只有本人能看。
  SELF
}
