package dev.patra.interaction.app.post.usecase.query;

/// 用户主页「投稿经验」标签的一条：经验（不带作者，就是主页主人）与所在期刊。
///
/// @param journal 所在期刊
/// @param experience 经验
public record UserExperienceView(JournalRefView journal, ExperienceItemView experience) {

  /// 创建。
  ///
  /// @param journal 期刊
  /// @param experience 经验
  /// @return 条目
  public static UserExperienceView of(JournalRefView journal, ExperienceItemView experience) {
    return new UserExperienceView(journal, experience);
  }
}
