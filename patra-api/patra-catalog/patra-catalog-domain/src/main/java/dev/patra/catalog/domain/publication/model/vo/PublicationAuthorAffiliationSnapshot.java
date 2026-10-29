package dev.patra.catalog.domain.publication.model.vo;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/// 作者-机构快照值对象——文献视角的机构原文，不做标准化。
///
/// 机构原文按文献侧给出的顺序原样保留，不解析、不匹配组织库：
/// 消歧与组织关联属于 `cat_organization` 侧职责，与导入解耦。
///
/// @param order 机构顺序（1 起连续）
/// @param affiliationString 机构原文（非空，超长按 2000 截断）
/// @author linqibin
/// @since 0.6.0
public record PublicationAuthorAffiliationSnapshot(int order, String affiliationString)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 机构原文列宽上限（与 DDL `affiliation_string VARCHAR(2000)` 对齐）。
  private static final int AFFILIATION_STRING_MAX_LENGTH = 2000;

  /// 紧凑构造器：校验顺序与机构原文，先去首尾空白再按列宽截断。
  ///
  /// 机构原文是唯一无上游长度约束的字段（PubMed `AffiliationInfo` 为自由文本），
  /// 超长时截断而非抛异常：一条超长机构不应导致整篇文献被跳过。
  ///
  /// 截断前先整串 `strip()`：否则前导空白超过列宽的病态输入会被截成纯空白，
  /// 再经截断后 strip 退化为空串，与"机构原文非空"的不变量矛盾。
  ///
  /// @throws IllegalArgumentException 如果顺序小于 1 或机构原文为空白
  public PublicationAuthorAffiliationSnapshot {
    Assert.isTrue(order >= 1, "机构顺序必须 >= 1");
    Assert.notBlank(affiliationString, "机构原文不能为空");
    affiliationString = truncate(affiliationString.strip());
  }

  /// 按机构原文列宽截断，并去除截断后可能残留的首尾空白。
  ///
  /// @param value 待截断字符串
  /// @return 长度不超过 2000 且无首尾空白的字符串
  private static String truncate(String value) {
    String limited =
        value.length() <= AFFILIATION_STRING_MAX_LENGTH
            ? value
            : value.substring(0, AFFILIATION_STRING_MAX_LENGTH);
    return limited.strip();
  }

  /// 创建机构快照。
  ///
  /// @param order 机构顺序（1 起连续）
  /// @param affiliationString 机构原文
  /// @return 机构快照值对象
  public static PublicationAuthorAffiliationSnapshot of(int order, String affiliationString) {
    return new PublicationAuthorAffiliationSnapshot(order, affiliationString);
  }
}
