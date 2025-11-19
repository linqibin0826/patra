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
 * 文献-研究者关联数据库实体,映射到表 {@code cat_publication_investigator}。
 *
 * <p>表结构: 管理文献与研究者的多对多关系,记录研究者角色和职责。
 *
 * <p>关键字段说明:
 *
 * <ul>
 *   <li>{@code publication_id} 出版物ID,外键 cat_publication.id
 *   <li>{@code investigator_id} 研究者ID,外键 cat_investigator.id
 *   <li>{@code role} 角色（如"principal","co-investigator","coordinator"）
 *   <li>{@code metadata} JSON 扩展数据字段
 * </ul>
 *
 * @author linqibin
 * @since 0.3.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_publication_investigator", autoResultMap = true)
public class PublicationInvestigatorDO extends BaseDO {
  /** 出版物ID（外键:cat_publication.id） */
  @TableField("publication_id")
  private Long publicationId;

  /** 研究者ID（外键:cat_investigator.id） */
  @TableField("investigator_id")
  private Long investigatorId;

  /** 角色（如"principal","co-investigator","coordinator"） */
  @TableField("role")
  private String role;

  /** 是否联系人（false=否,true=是） */
  @TableField("is_contact")
  private Boolean isContact;

  /** 顺序号（多个研究者时排序） */
  @TableField("order_num")
  private Integer orderNum;

  /** 职责描述（文本） */
  @TableField("responsibility")
  private String responsibility;

  /** 关联元数据（JSON 格式） */
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;
}
