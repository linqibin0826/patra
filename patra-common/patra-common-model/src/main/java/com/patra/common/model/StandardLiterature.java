package com.patra.common.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Papertrace 微服务间共享的规范化文献表示。
 *
 * <p>此不可变结构作为采集、目录和溯源适配器之间的共享内核模型。 特意不包含任何业务行为,以保持共享模块无框架依赖且可移植。
 *
 * @since 0.2.0
 */
@Value
@Builder
@Jacksonized
public class StandardLiterature {

  /** 文献项目的人类可读标题。 */
  String title;

  /** 摘要或概要文本。 */
  String abstractText;

  /** 按展示顺序排列的作者列表。 */
  List<StandardAuthor> authors;

  /** 期刊元数据(如果可用)。 */
  StandardJournal journal;

  /** 标识符映射,如 PMID、DOI、PMC。 */
  Map<String, String> identifiers;

  /** 精确到日的发布日期;未提供时为 null。 */
  LocalDate publicationDate;

  /** 领域级关键词。 */
  List<String> keywords;

  /** 与目录契约需求对齐的作者快照。 */
  @Value
  @Builder
  @Jacksonized
  public static class StandardAuthor {

    /** 姓氏 */
    String lastName;

    /** 名字 */
    String foreName;

    /** 所属机构 */
    String affiliation;
  }

  /** 与目录契约需求对齐的期刊快照。 */
  @Value
  @Builder
  @Jacksonized
  public static class StandardJournal {

    /** 期刊标题 */
    String title;

    /** 国际标准期刊号 */
    String issn;

    /** 出版商 */
    String publisher;
  }
}
