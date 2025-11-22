package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 作者数据库实体,映射到表 `cat_author`。
///
/// 表结构: 存储作者信息,支持复合去重策略(ORCID + 姓名 + 机构 + 邮箱)。
///
/// 关键字段说明:
///
/// - `last_name` 姓(Last Name/Family Name)
///   - `fore_name` 名(First Name/Given Name)
///   - `orcid` ORCID 标识符,唯一索引 uk_orcid
///   - `email` 邮箱地址,索引 idx_email
///   - `dedup_key` 复合去重键,应用层计算 MD5 哈希,索引 idx_dedup_key
///   - `author_metadata` JSON 扩展数据字段
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_author", autoResultMap = true)
public class AuthorDO extends BaseDO {

  /// 姓(Last Name/Family Name)
  @TableField("last_name")
  private String lastName;

  /// 名(First Name/Given Name)
  @TableField("fore_name")
  private String foreName;

  /// 姓名缩写(如 "J.K.")
  @TableField("initials")
  private String initials;

  /// 后缀(如 "Jr.", "III", "PhD")
  @TableField("suffix")
  private String suffix;

  /// 组织名称(机构/企业)
  @TableField("organization_name")
  private String organizationName;

  /// ORCID 标识符(格式: 0000-0001-2345-6789)
  @TableField("orcid")
  private String orcid;

  /// 研究者 ID(ResearcherID/Publons)
  @TableField("researcher_id")
  private String researcherId;

  /// Scopus 作者 ID
  @TableField("scopus_id")
  private String scopusId;

  /// 邮箱地址
  @TableField("email")
  private String email;

  /// 复合去重键(应用层计算,MD5 哈希)
  @TableField("dedup_key")
  private String dedupKey;

  /// 同等贡献标志(0=否, 1=是)
  @TableField("equal_contribution")
  private Boolean equalContribution;

  /// 信息是否有效(0=无效, 1=有效)
  @TableField("valid")
  private Boolean valid;

  /// 作者元数据(JSON 格式,灵活扩展)
  @TableField(value = "author_metadata", typeHandler = JacksonTypeHandler.class)
  private JsonNode authorMetadata;
}
