package dev.patra.interaction.app.post.usecase.experience;

import dev.patra.interaction.app.post.usecase.query.ExperienceDetailView;
import dev.patra.interaction.domain.post.model.vo.ExperienceInput;
import dev.patra.tool.cqrs.Command;

/// 在期刊下写一条投稿经验。
///
/// @param journalKey 期刊的对外 key（catalog 期刊 ID）
/// @param input 填写内容
/// @param anonymous 是否匿名发布（之后不能改）
public record PublishExperienceCommand(String journalKey, ExperienceInput input, boolean anonymous)
    implements Command<ExperienceDetailView> {

  /// 创建命令。
  ///
  /// @param journalKey 期刊 key
  /// @param input 填写内容
  /// @param anonymous 是否匿名
  /// @return 命令
  public static PublishExperienceCommand of(
      String journalKey, ExperienceInput input, boolean anonymous) {
    return new PublishExperienceCommand(journalKey, input, anonymous);
  }
}
