package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.EqualsAndHashCode;
import com.patra.starter.mybatis.entity.BaseDO;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/// 文献-资助关联数据库实体,映射到表 `cat_publication_funding`。
/// 
/// 表结构: 管理文献与资助的多对多关系,支持主要资助标记和顺序。
/// 
/// 关键字段说明:
/// 
/// - `publication_id` 出版物 ID(外键:cat_publication.id)
///   - `funding_id` 资助 ID(外键:cat_funding.id)
///   - `is_primary` 是否主要资助(0=否,1=是)
///   - `order_num` 顺序号(用于排序显示)
///   - `metadata` JSON 扩展数据字段
/// 
/// 唯一约束: uk_pub_funding(publication_id, funding_id),防止重复关联。
/// 
/// @author linqibin
/// @since 0.4.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_publication_funding", autoResultMap = true)
public class PublicationFundingDO extends BaseDO {
  /// 出版物 ID(外键:cat_publication.id)
  @TableField("publication_id")
  private Long publicationId;

  /// 资助 ID(外键:cat_funding.id)
  @TableField("funding_id")
  private Long fundingId;

  /// 致谢文本(原始致谢内容)
  @TableField("acknowledgment_text")
  private String acknowledgmentText;

  /// 是否主要资助(0=否,1=是)
  @TableField("is_primary")
  private Boolean isPrimary;

  /// 顺序号(用于排序显示)
  @TableField("order_num")
  private Integer orderNum;

  /// 接收人/主要研究者(PI)姓名
  @TableField("recipient_name")
  private String recipientName;

  /// 接收人 ORCID 标识符
  @TableField("recipient_orcid")
  private String recipientOrcid;

  /// 关联元数据(JSON 格式)
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;
}
