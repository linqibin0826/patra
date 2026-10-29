package dev.patra.catalog.domain.publication.model.vo;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Builder;

/// 文献作者快照值对象——「行内存名 + 可空 ORCID 软关联」的领域表达。
///
/// 快照挂在 `PublicationAggregate` 上（唯一事实来源）；`authorId` 仅当 ORCID
/// 命中 `cat_author` 已消歧作者时由 Repository 填充，绝不因导入新建作者。
///
/// @param order 作者顺序（1 起）
/// @param lastName 姓（可空）
/// @param foreName 名（可空）
/// @param initials 缩写（可空）
/// @param suffix 后缀（可空，不拼入 displayName）
/// @param collectiveName 集体作者名（个人作者为 null，超长按 500 截断）
/// @param displayName 展示名（恒非空，由 [#deriveDisplayName] 派生）
/// @param orcid 归一化 ORCID（可空）
/// @param authorId 软关联作者 ID（可空）
/// @param firstAuthor 是否第一作者
/// @param equalContribution 是否同等贡献
/// @param affiliations 机构快照列表（有序，恒非 null）
/// @author linqibin
/// @since 0.6.0
@Builder
public record PublicationAuthorSnapshot(
    int order,
    String lastName,
    String foreName,
    String initials,
    String suffix,
    String collectiveName,
    String displayName,
    String orcid,
    Long authorId,
    boolean firstAuthor,
    boolean equalContribution,
    List<PublicationAuthorAffiliationSnapshot> affiliations)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 展示名列宽上限（与 DDL `display_name VARCHAR(200)` 对齐）。
  private static final int DISPLAY_NAME_MAX_LENGTH = 200;

  /// 集体作者名列宽上限（与 DDL `collective_name VARCHAR(500)` 对齐）。
  private static final int COLLECTIVE_NAME_MAX_LENGTH = 500;

  /// 紧凑构造器：校验 + 空白归一化 + 集体名截断 + 机构列表防御性拷贝。
  ///
  /// 展示名列宽校验在此兜底：绕过 [#deriveDisplayName] 直接 build 的超长展示名
  /// 在领域层即失败，而非拖到数据库写入才炸。
  ///
  /// 集体作者名同样是 PubMed 侧无长度约束的自由文本，超长时截断而非抛异常：
  /// 一个超长集体名不应导致整篇文献被跳过（与机构原文一致的处置）。
  ///
  /// @throws IllegalArgumentException 如果作者顺序小于 1、展示名为空白或超过 200 字符
  public PublicationAuthorSnapshot {
    Assert.isTrue(order >= 1, "作者顺序必须 >= 1");
    Assert.notBlank(displayName, "作者展示名不能为空");
    Assert.isTrue(
        displayName.length() <= DISPLAY_NAME_MAX_LENGTH, "作者展示名长度不能超过 " + DISPLAY_NAME_MAX_LENGTH);
    collectiveName = StrUtil.trimToNull(collectiveName);
    if (collectiveName != null) {
      collectiveName = truncate(collectiveName, COLLECTIVE_NAME_MAX_LENGTH);
    }
    affiliations = affiliations != null ? List.copyOf(affiliations) : List.of();
  }

  /// 派生展示名：集体名优先；否则 `"LastName ForeName"`，ForeName 缺则用 Initials，
  /// 仅 LastName 则单独成名；全空返回 null（调用方据此跳过该作者）；超长按 200 截断。
  ///
  /// @param lastName 姓
  /// @param foreName 名
  /// @param initials 缩写
  /// @param collectiveName 集体作者名
  /// @return 展示名，或 null（无可用姓名）
  public static String deriveDisplayName(
      String lastName, String foreName, String initials, String collectiveName) {
    String collective = StrUtil.trimToNull(collectiveName);
    if (collective != null) {
      return truncate(collective, DISPLAY_NAME_MAX_LENGTH);
    }
    String last = StrUtil.trimToNull(lastName);
    if (last == null) {
      return null;
    }
    String fore = StrUtil.trimToNull(foreName);
    if (fore != null) {
      return truncate(last + " " + fore, DISPLAY_NAME_MAX_LENGTH);
    }
    String init = StrUtil.trimToNull(initials);
    if (init != null) {
      return truncate(last + " " + init, DISPLAY_NAME_MAX_LENGTH);
    }
    return truncate(last, DISPLAY_NAME_MAX_LENGTH);
  }

  /// 按给定列宽截断，并去除截断后可能残留的首尾空白。
  ///
  /// @param value 待截断字符串
  /// @param maxLength 列宽上限
  /// @return 长度不超过 maxLength 且无首尾空白的字符串
  private static String truncate(String value, int maxLength) {
    String limited = value.length() <= maxLength ? value : value.substring(0, maxLength);
    return limited.strip();
  }

  /// 是否集体作者。
  ///
  /// @return true 如果存在集体作者名
  public boolean isCollective() {
    return collectiveName != null;
  }
}
