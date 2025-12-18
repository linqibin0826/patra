package com.patra.catalog.infra.adapter.persistence.entity.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/// 作者姓名嵌入式值对象（JPA）。
///
/// **设计说明**：
///
/// - 使用 `@Embeddable` 将值对象字段展开到父实体表中
/// - 与领域层的 `AuthorName` record 对应
/// - 不创建独立表，字段直接嵌入 `cat_author` 表
///
/// **字段映射**：
///
/// | 嵌入式字段 | 数据库列 |
/// |-----------|---------|
/// | lastName  | last_name |
/// | foreName  | fore_name |
/// | initials  | initials |
/// | suffix    | suffix |
///
/// @author linqibin
/// @since 0.1.0
@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class AuthorNameEmbeddable {

  /// 姓氏（Last Name / Family Name）
  @Column(name = "last_name", length = 100)
  private String lastName;

  /// 名字（First Name / Given Name）
  @Column(name = "fore_name", length = 100)
  private String foreName;

  /// 姓名缩写（如 "J.K."）
  @Column(name = "initials", length = 20)
  private String initials;

  /// 后缀（如 "Jr.", "III", "PhD"）
  @Column(name = "suffix", length = 20)
  private String suffix;
}
