package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.EqualsAndHashCode;
import com.patra.starter.mybatis.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/**
 * 文献-物质关联数据库实体,映射到表 {@code cat_publication_substance}。
 *
 * <p>表结构: 存储文献涉及的化学物质标注,关联文献和物质,支持主/副物质标记和角色。
 *
 * <p>关键字段说明:
 *
 * <ul>
 *   <li>{@code publication_id} 出版物 ID(外键:cat_publication.id)
 *   <li>{@code substance_id} 物质 ID(外键:cat_substance.id)
 *   <li>{@code is_major} 是否主要物质(0=副物质,1=主要物质)
 *   <li>{@code role} 物质角色(枚举:therapeutic/diagnostic/research_tool/adverse_effect/target/metabolite)
 * </ul>
 *
 * <p>索引说明:
 *
 * <ul>
 *   <li>{@code idx_pub_substance} 文献+物质复合索引,支持查询文献的物质(<20ms)
 *   <li>{@code idx_substance_pub} 物质+文献复合索引,支持查询物质的文献(<50ms)
 *   <li>{@code idx_major_role} 物质+主/副标记+角色复合索引,支持多条件筛选
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cat_publication_substance")
public class PublicationSubstanceDO extends BaseDO {
  /** 出版物 ID(外键:cat_publication.id) */
  @TableField("publication_id")
  private Long publicationId;

  /** 物质 ID(外键:cat_substance.id) */
  @TableField("substance_id")
  private Long substanceId;

  /** 是否主要物质(0=副物质,1=主要物质) */
  @TableField("is_major")
  private Boolean isMajor;

  /** 物质角色(枚举:therapeutic/diagnostic/research_tool/adverse_effect/target/metabolite) */
  @TableField("role")
  private String role;

}
