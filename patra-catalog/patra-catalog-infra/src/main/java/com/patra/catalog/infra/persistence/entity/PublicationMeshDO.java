package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 文献-MeSH 关联表数据库实体，映射到表 `cat_publication_mesh`。
///
/// 表结构：存储文献的 MeSH 标引，关联文献、主题词、限定词，支持主/副主题标记。
///
/// 关键字段说明：
///
/// - `publication_id` - 出版物 ID（外键：cat_publication.id）
///   - `descriptor_id` - 主题词 ID（外键：cat_mesh_descriptor.id）
///   - `qualifier_id` - 限定词 ID（外键：cat_mesh_qualifier.id，可选）
///   - `is_major_topic` - 是否主要主题（0=副主题，1=主要主题，对应 MeSH 星号 *）
///   - `order_num` - 顺序号（在同一文献内的排序）
///   - `indexing_method` - 标引方法（如 Manual/Automatic）
///
/// 索引说明：
///
/// - idx_pub_desc - 文献+主题词复合索引，支持查询文献的 MeSH（&lt;20ms）
///   - idx_desc_pub - 主题词+文献复合索引，支持查询 MeSH 的文献（&lt;50ms）
///   - idx_major_topic - 主题词+主/副主题复合索引，筛选主要主题文献
///   - idx_qualifier - 限定词索引，支持按限定词筛选文献
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_publication_mesh", autoResultMap = true)
public class PublicationMeshDO extends BaseDO {
  /// 出版物 ID（外键：cat_publication.id）
  @TableField("publication_id")
  private Long publicationId;

  /// 主题词 ID（外键：cat_mesh_descriptor.id）
  @TableField("descriptor_id")
  private Long descriptorId;

  /// 限定词 ID（外键：cat_mesh_qualifier.id，可选）
  @TableField("qualifier_id")
  private Long qualifierId;

  /// 是否主要主题（0=副主题，1=主要主题，对应 MeSH 星号 *）
  @TableField("is_major_topic")
  private Boolean isMajorTopic;

  /// 顺序号（在同一文献内的排序）
  @TableField("order_num")
  private Integer orderNum;

  /// 标引方法（如 Manual/Automatic）
  @TableField("indexing_method")
  private String indexingMethod;
}
