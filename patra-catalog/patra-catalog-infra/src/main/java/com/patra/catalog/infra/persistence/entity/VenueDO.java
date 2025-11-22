package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 出版载体数据库实体,映射到表 `cat_venue`。
///
/// 表结构: 管理期刊、书籍、会议等出版载体的基本信息(不包含具体卷期)。
///
/// 关键字段说明:
///
/// - `venue_type` 载体类型,支持 JOURNAL/BOOK/CONFERENCE/OTHER
///   - `issn` 期刊专用,唯一索引 idx_issn
///   - `isbn` 书籍专用,唯一索引 idx_isbn
///   - `issn_type` ISSN 类型,print/electronic
///   - `venue_specific_data` JSON 扩展数据字段
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_venue", autoResultMap = true)
public class VenueDO extends BaseDO {

  /// 载体类型: JOURNAL/BOOK/CONFERENCE/OTHER
  @TableField("venue_type")
  private String venueType;

  /// 载体名称(期刊名/书名/会议名)
  @TableField("title")
  private String title;

  /// ISO 标准缩写(期刊专用)
  @TableField("iso_abbreviation")
  private String isoAbbreviation;

  /// Medline 缩写(期刊专用)
  @TableField("medline_abbreviation")
  private String medlineAbbreviation;

  /// ISSN 号(期刊专用,格式: 1234-5678)
  @TableField("issn")
  private String issn;

  /// ISBN 号(书籍专用,格式: 978-3-16-148410-0)
  @TableField("isbn")
  private String isbn;

  /// ISSN 类型: print/electronic
  @TableField("issn_type")
  private String issnType;

  /// Linking ISSN(关联纸质版和电子版)
  @TableField("issn_linking")
  private String issnLinking;

  /// NLM 唯一标识符
  @TableField("nlm_unique_id")
  private String nlmUniqueId;

  /// 出版国家(ISO 3166-1 alpha-3,如 USA/CHN)
  @TableField("country")
  private String country;

  /// 出版商名称
  @TableField("publisher")
  private String publisher;

  /// 类型特定数据(JSON 格式,灵活扩展)
  @TableField(value = "venue_specific_data", typeHandler = JacksonTypeHandler.class)
  private JsonNode venueSpecificData;
}
