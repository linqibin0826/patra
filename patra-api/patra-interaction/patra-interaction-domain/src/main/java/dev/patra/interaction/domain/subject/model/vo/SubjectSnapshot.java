package dev.patra.interaction.domain.subject.model.vo;

import java.util.regex.Pattern;

/// 对象的展示快照：兜底与「已失效」展示，也是 collection 按出版日期 / 期刊排序的数据来源。
///
/// `publishedDate` 是 ISO 8601 降精度字符串（`2019`、`2019-03`、`2019-03-15`），按字典序倒排即
/// 「年、月、日依次降序，缺的排后」。只校验写法，不做历法校验，以来源为准。
///
/// @param title 标题原文（含内联标记），可空
/// @param journal 期刊全称，可空
/// @param publishedDate 出版日期，可空
public record SubjectSnapshot(String title, String journal, String publishedDate) {

  private static final Pattern PUBLISHED_DATE =
      Pattern.compile("[0-9]{4}(-(0[1-9]|1[0-2])(-(0[1-9]|[12][0-9]|3[01]))?)?");

  /// 校验出版日期的写法。
  ///
  /// @param title 标题
  /// @param journal 期刊
  /// @param publishedDate 出版日期
  public SubjectSnapshot {
    if (publishedDate != null && !isValidPublishedDate(publishedDate)) {
      throw new IllegalArgumentException("出版日期不是 ISO 8601 降精度写法：" + publishedDate);
    }
  }

  /// 创建。
  ///
  /// @param title 标题
  /// @param journal 期刊
  /// @param publishedDate 出版日期
  /// @return 快照
  public static SubjectSnapshot of(String title, String journal, String publishedDate) {
    return new SubjectSnapshot(title, journal, publishedDate);
  }

  /// 出版日期的写法是否合法。
  ///
  /// @param publishedDate 出版日期
  /// @return 合法时为 `true`
  public static boolean isValidPublishedDate(String publishedDate) {
    return PUBLISHED_DATE.matcher(publishedDate).matches();
  }
}
