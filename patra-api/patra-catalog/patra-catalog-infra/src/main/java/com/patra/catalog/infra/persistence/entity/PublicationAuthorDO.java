package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.EqualsAndHashCode;
import com.patra.starter.mybatis.entity.BaseDO;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/**
 * 文献-作者关联数据库实体,映射到表 {@code cat_publication_author}。
 *
 * <p>表结构: 管理文献与作者的多对多关系,记录作者顺序、角色和贡献类型。
 *
 * <p>关键字段说明:
 *
 * <ul>
 *   <li>{@code publication_id} 出版物ID,外键 cat_publication.id
 *   <li>{@code author_id} 作者ID,外键 cat_author.id
 *   <li>{@code author_order} 作者顺序,唯一约束 uk_author_order
 *   <li>{@code is_first_author} 是否第一作者,CHECK 约束 chk_first_author_consistency
 *   <li>{@code author_metadata} JSON 扩展数据字段
 * </ul>
 *
 *
 * @author linqibin
 * @since 0.3.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_publication_author", autoResultMap = true)
public class PublicationAuthorDO extends BaseDO {
  /** 出版物ID（外键:cat_publication.id） */
  @TableField("publication_id")
  private Long publicationId;

  /** 作者ID（外键:cat_author.id） */
  @TableField("author_id")
  private Long authorId;

  /** 作者顺序（1=第一作者,2=第二作者...） */
  @TableField("author_order")
  private Integer authorOrder;

  /** 是否第一作者（false=否,true=是） */
  @TableField("is_first_author")
  private Boolean isFirstAuthor;

  /** 是否通讯作者（false=否,true=是） */
  @TableField("is_corresponding_author")
  private Boolean isCorrespondingAuthor;

  /** 是否同等贡献作者（false=否,true=是） */
  @TableField("is_equal_contribution")
  private Boolean isEqualContribution;

  /** 贡献类型（CRediT分类,如"Conceptualization"） */
  @TableField("contribution_type")
  private String contributionType;

  /** 原始机构字符串（外部采集,未标准化） */
  @TableField("affiliation_string")
  private String affiliationString;

  /** 作者元数据（JSON 格式） */
  @TableField(value = "author_metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode authorMetadata;

}
