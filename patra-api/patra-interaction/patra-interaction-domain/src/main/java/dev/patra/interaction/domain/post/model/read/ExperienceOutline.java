package dev.patra.interaction.domain.post.model.read;

import dev.patra.interaction.domain.post.model.enums.FinalOutcome;
import dev.patra.interaction.domain.post.model.enums.FirstDecision;
import dev.patra.interaction.domain.post.model.vo.ExperienceFields;
import java.util.Objects;

/// 一条投稿经验的结果：经验列表的结果行（`首轮 42 天 · 大修 → 录用`）要的字段，给别的子域带进事件（PAP-141）。
///
/// 别的子域只把它整个传下去，不读里面的枚举（`post.model.enums` 不是公开包）。
///
/// @param firstDecision 首轮结果
/// @param finalOutcome 最终结果
/// @param firstRoundDays 首轮用时（天）
/// @param totalDays 投稿到最终决定的天数，可空（见 {@link ExperienceFields#totalDays()}）
public record ExperienceOutline(
    FirstDecision firstDecision, FinalOutcome finalOutcome, int firstRoundDays, Integer totalDays) {

  /// 两个结果必填：进外送事件时要转常量名。
  public ExperienceOutline {
    Objects.requireNonNull(firstDecision, "firstDecision");
    Objects.requireNonNull(finalOutcome, "finalOutcome");
  }

  /// 创建。
  ///
  /// @param firstDecision 首轮结果
  /// @param finalOutcome 最终结果
  /// @param firstRoundDays 首轮用时
  /// @param totalDays 总用时，可空
  /// @return 结果
  public static ExperienceOutline of(
      FirstDecision firstDecision,
      FinalOutcome finalOutcome,
      int firstRoundDays,
      Integer totalDays) {
    return new ExperienceOutline(firstDecision, finalOutcome, firstRoundDays, totalDays);
  }

  /// 从结构化字段取结果。
  ///
  /// @param fields 字段
  /// @return 结果
  public static ExperienceOutline of(ExperienceFields fields) {
    return of(
        fields.firstDecision(), fields.finalOutcome(), fields.firstRoundDays(), fields.totalDays());
  }
}
