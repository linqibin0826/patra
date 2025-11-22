package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 文献-类型关联数据库实体,映射到表 `cat_publication_type_mapping`。
///
/// 表结构: 存储文献的出版类型标注,关联文献和类型(一篇文献可有多个类型)。
///
/// 关键字段说明:
///
/// - `publication_id` 出版物 ID(外键:cat_publication.id)
///   - `type_id` 类型 ID(外键:cat_publication_type.id)
///   - `order_num` 顺序号(在同一文献内的排序)
///
/// 索引说明:
///
/// - `idx_pub_type` 文献+类型复合索引,支持查询文献的类型(<20ms)
///   - `idx_type_pub` 类型+文献复合索引,支持查询类型的文献(<50ms)
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cat_publication_type_mapping")
public class PublicationTypeMappingDO extends BaseDO {
  /// 出版物 ID(外键:cat_publication.id)
  @TableField("publication_id")
  private Long publicationId;

  /// 类型 ID(外键:cat_publication_type.id)
  @TableField("type_id")
  private Long typeId;

  /// 顺序号(在同一文献内的排序)
  @TableField("order_num")
  private Integer orderNum;
}
