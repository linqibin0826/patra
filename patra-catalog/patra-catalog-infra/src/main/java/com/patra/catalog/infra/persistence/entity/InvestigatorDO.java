package com.patra.catalog.infra.persistence.entity;

import java.time.Instant;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 研究者数据库实体,映射到表 {@code cat_investigator}。
 *
 * <p>表结构: 存储研究者信息（非作者的研究人员,如临床试验 PI）,支持去重。
 *
 * <p>关键字段说明:
 *
 * <ul>
 *   <li>{@code orcid} ORCID 标识符,普通索引 idx_orcid
 *   <li>{@code dedup_key} 复合去重键,应用层计算 MD5 哈希
 *   <li>{@code metadata} JSON 扩展数据字段
 * </ul>
 *
 * @author linqibin
 * @since 0.3.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_investigator", autoResultMap = true)
public class InvestigatorDO extends BaseDO {

  /** 姓（Last Name/Family Name） */
  @TableField("last_name")
  private String lastName;

  /** 名（First Name/Given Name） */
  @TableField("fore_name")
  private String foreName;

  /** 姓名缩写（如"J.K."） */
  @TableField("initials")
  private String initials;

  /** 后缀（如"Jr.","III","MD","PhD"） */
  @TableField("suffix")
  private String suffix;

  /** ORCID 标识符（格式:0000-0001-2345-6789） */
  @TableField("orcid")
  private String orcid;

  /** 研究者ID（ResearcherID/Publons） */
  @TableField("researcher_id")
  private String researcherId;

  /** 研究者类型（如"PI","CoI","Collaborator"） */
  @TableField("investigator_type")
  private String investigatorType;

  /** 机构名称（文本,不关联 affiliation 表） */
  @TableField("affiliation_name")
  private String affiliationName;

  /** 邮箱地址 */
  @TableField("email")
  private String email;

  /** 复合去重键（应用层计算,MD5哈希） */
  @TableField("dedup_key")
  private String dedupKey;

  /** 研究者元数据（JSON 格式） */
  @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode metadata;

}
