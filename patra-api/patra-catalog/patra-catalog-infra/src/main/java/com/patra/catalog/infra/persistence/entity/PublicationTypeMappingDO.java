package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.EqualsAndHashCode;
import com.patra.starter.mybatis.entity.BaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/**
 * 文献-类型关联数据库实体,映射到表 {@code cat_publication_type_mapping}。
 *
 * <p>表结构: 存储文献的出版类型标注,关联文献和类型(一篇文献可有多个类型)。
 *
 * <p>关键字段说明:
 *
 * <ul>
 *   <li>{@code publication_id} 出版物 ID(外键:cat_publication.id)
 *   <li>{@code type_id} 类型 ID(外键:cat_publication_type.id)
 *   <li>{@code order_num} 顺序号(在同一文献内的排序)
 * </ul>
 *
 * <p>索引说明:
 *
 * <ul>
 *   <li>{@code idx_pub_type} 文献+类型复合索引,支持查询文献的类型(<20ms)
 *   <li>{@code idx_type_pub} 类型+文献复合索引,支持查询类型的文献(<50ms)
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cat_publication_type_mapping")
public class PublicationTypeMappingDO extends BaseDO {
  /** 出版物 ID(外键:cat_publication.id) */
  @TableField("publication_id")
  private Long publicationId;

  /** 类型 ID(外键:cat_publication_type.id) */
  @TableField("type_id")
  private Long typeId;

  /** 顺序号(在同一文献内的排序) */
  @TableField("order_num")
  private Integer orderNum;

}
