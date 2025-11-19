package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.EqualsAndHashCode;
import com.patra.starter.mybatis.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/**
 * 文献-关键词关联数据库实体,映射到表 {@code cat_publication_keyword}。
 *
 * <p>表结构: 存储文献的关键词标注,关联文献和关键词,支持主/副关键词标记。
 *
 * <p>关键字段说明:
 *
 * <ul>
 *   <li>{@code publication_id} 出版物 ID(外键:cat_publication.id)
 *   <li>{@code keyword_id} 关键词 ID(外键:cat_keyword.id)
 *   <li>{@code is_major} 是否主要关键词(0=副关键词,1=主要关键词)
 *   <li>{@code order_num} 顺序号(在同一文献内的排序)
 *   <li>{@code keyword_set} 关键词集(如 Author/Editor,区分不同来源)
 * </ul>
 *
 * <p>索引说明:
 *
 * <ul>
 *   <li>{@code idx_pub_keyword} 文献+关键词复合索引,支持查询文献的关键词(<20ms)
 *   <li>{@code idx_keyword_pub} 关键词+文献复合索引,支持查询关键词的文献(<50ms)
 *   <li>{@code idx_major} 关键词+主/副标记复合索引,筛选主要关键词文献
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cat_publication_keyword")
public class PublicationKeywordDO extends BaseDO {
  /** 出版物 ID(外键:cat_publication.id) */
  @TableField("publication_id")
  private Long publicationId;

  /** 关键词 ID(外键:cat_keyword.id) */
  @TableField("keyword_id")
  private Long keywordId;

  /** 是否主要关键词(0=副关键词,1=主要关键词) */
  @TableField("is_major")
  private Boolean isMajor;

  /** 顺序号(在同一文献内的排序) */
  @TableField("order_num")
  private Integer orderNum;

  /** 关键词集(如 Author/Editor,区分不同来源) */
  @TableField("keyword_set")
  private String keywordSet;

}
