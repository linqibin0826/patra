package com.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import java.io.Serial;
import java.io.Serializable;

/// 文献关键词值对象。
///
/// 封装文献的关键词信息，包含来源、关键词文本和主题标记。
///
/// **业务含义**：
///
/// 关键词用于描述文献的核心主题，来源包括：
/// - **author**：作者提供的关键词
/// - **publisher**：出版商添加的关键词
/// - **indexer**：索引员分配的关键词
///
/// **与 MeSH 的区别**：
///
/// - MeSH 是受控词表，有严格的层级结构和标准化
/// - 关键词是自由文本，由作者/出版商自行定义
///
/// **MajorTopic 标记**：
///
/// 当 `majorTopic=true` 时，表示该关键词是文献的主要主题。
///
/// @param source 关键词来源（author, publisher, indexer 等）
/// @param term 关键词文本
/// @param majorTopic 是否为主要主题
/// @param keywordOrder 关键词顺序
/// @author linqibin
/// @since 0.1.0
public record PublicationKeyword(
    String source, String term, boolean majorTopic, Integer keywordOrder) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证关键词文本不为空。
  public PublicationKeyword {
    Assert.isTrue(StrUtil.isNotBlank(term), "关键词文本不能为空");
  }

  /// 创建关键词值对象。
  ///
  /// @param source 关键词来源
  /// @param term 关键词文本
  /// @param majorTopic 是否为主要主题
  /// @param order 关键词顺序
  /// @return 关键词值对象
  public static PublicationKeyword of(
      String source, String term, boolean majorTopic, Integer order) {
    return new PublicationKeyword(source, term, majorTopic, order);
  }

  /// 创建作者关键词。
  ///
  /// @param term 关键词文本
  /// @param order 关键词顺序
  /// @return 关键词值对象
  public static PublicationKeyword ofAuthor(String term, Integer order) {
    return new PublicationKeyword("author", term, false, order);
  }

  /// 判断是否为作者关键词。
  ///
  /// @return true 如果来源为 author
  public boolean isAuthorKeyword() {
    return "author".equalsIgnoreCase(source);
  }

  /// 判断是否有来源信息。
  ///
  /// @return true 如果来源不为空
  public boolean hasSource() {
    return StrUtil.isNotBlank(source);
  }
}
