package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import java.util.Locale;
import lombok.Getter;

/// 文献日期类型枚举。
///
/// 字段映射：cat_publication_date.date_type
///
/// 类型说明：
///
/// - **RECEIVED** - 投稿日期（文献首次提交）
/// - **ACCEPTED** - 接收日期（正式接收发表）
/// - **REVISED** - 修订日期（作者修改后重新提交）
/// - **PUBLISHED** - 发表日期（正式发表，通用）
/// - **EPUBLISH** - 电子版发表日期
/// - **PPUBLISH** - 纸质版发表日期
/// - **RETRACTED** - 撤稿日期
/// - **ENTREZ_DATE** - 进入 PubMed 数据库日期
/// - **OTHER** - 其他日期类型
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum PublicationDateType {

  /// 投稿日期
  RECEIVED("received", "Received Date"),

  /// 接收日期
  ACCEPTED("accepted", "Accepted Date"),

  /// 修订日期
  REVISED("revised", "Revised Date"),

  /// 发表日期（通用）
  PUBLISHED("published", "Published Date"),

  /// 电子版发表日期
  EPUBLISH("epublish", "Electronic Publication Date"),

  /// 纸质版发表日期
  PPUBLISH("ppublish", "Print Publication Date"),

  /// 撤稿日期
  RETRACTED("retracted", "Retracted Date"),

  /// 进入 PubMed 数据库日期
  ENTREZ_DATE("entrezdate", "Entrez Date"),

  /// 其他日期类型
  OTHER("other", "Other Date");

  /// 数据库存储的代码值（小写）
  private final String code;

  /// 描述文本
  private final String description;

  PublicationDateType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "received", "ACCEPTED", "Published"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static PublicationDateType fromCode(String value) {
    Assert.notBlank(value, "日期类型代码不能为空");
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (PublicationDateType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的日期类型：" + value);
  }

  /// 判断是否为发表相关日期。
  ///
  /// @return true 如果为 PUBLISHED、EPUBLISH 或 PPUBLISH
  public boolean isPublicationDate() {
    return this == PUBLISHED || this == EPUBLISH || this == PPUBLISH;
  }
}
