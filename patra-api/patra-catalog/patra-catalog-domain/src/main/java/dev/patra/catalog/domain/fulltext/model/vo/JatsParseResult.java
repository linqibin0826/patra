package dev.patra.catalog.domain.fulltext.model.vo;

import dev.patra.catalog.domain.fulltext.model.vo.body.FullTextBody;

/// JATS 解析结果。
///
/// @param body 正文，无正文时为 `null`
/// @param warningCount 解析警告数（不认识的元素、匹配不上的图片、降级的引用等），只写日志不入库
/// @param noBody XML 没有 `<body>` 或 `<body>` 为空
/// @author linqibin
/// @since 0.11.0
public record JatsParseResult(FullTextBody body, int warningCount, boolean noBody) {

  /// 有正文的结果。
  ///
  /// @param body 正文
  /// @param warningCount 警告数
  /// @return 结果
  public static JatsParseResult ofBody(FullTextBody body, int warningCount) {
    return new JatsParseResult(body, warningCount, false);
  }

  /// 无正文的结果。
  ///
  /// @return 结果
  public static JatsParseResult ofNoBody() {
    return new JatsParseResult(null, 0, true);
  }
}
