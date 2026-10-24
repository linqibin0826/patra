package dev.patra.interaction.domain.subject.model.vo;

/// 登记对象的本地 ID（Snowflake）。互动数据只引用它。
///
/// @param value ID
public record SubjectId(long value) {

  /// 创建。
  ///
  /// @param value ID
  /// @return 对象 ID
  public static SubjectId of(long value) {
    return new SubjectId(value);
  }
}
