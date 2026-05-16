package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 文献版本类型枚举。
///
/// 字段映射：cat_publication_oa_location.version_type
///
/// 学术文献版本类型说明（按学术出版流程排序）：
///
/// - **SUBMITTED** - 投稿版（Submitted Version / Preprint）
///   - 作者原始投稿，未经同行评审
///   - 又称"作者原稿"（Author's Original Manuscript）
///
/// - **ACCEPTED** - 接受版（Accepted Version / Postprint）
///   - 通过同行评审，内容已定稿
///   - 又称"作者最终版"（Author's Accepted Manuscript, AAM）
///   - 不含出版商排版格式
///
/// - **PUBLISHED** - 出版版（Published Version / Version of Record）
///   - 正式出版版本，含出版商排版
///   - 又称"记录版本"（Version of Record, VoR）
///
/// 优先级排序：PUBLISHED > ACCEPTED > SUBMITTED
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum VersionType {

  /// 投稿版（预印本）
  SUBMITTED("submittedversion", "Submitted Version", 1),

  /// 接受版（同行评审后）
  ACCEPTED("acceptedversion", "Accepted Version", 2),

  /// 出版版（最终发表版）
  PUBLISHED("publishedversion", "Published Version", 3);

  /// 数据库存储的代码值（小写）
  private final String code;

  /// 描述文本
  private final String description;

  /// 优先级（数值越大版本越终极）
  private final int priority;

  VersionType(String code, String description, int priority) {
    this.code = code;
    this.description = description;
    this.priority = priority;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// 支持多种格式：
  /// - 完整格式："publishedversion", "acceptedversion", "submittedversion"
  /// - 简写格式："published", "accepted", "submitted"
  ///
  /// @param value 代码值
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static VersionType fromCode(String value) {
    Assert.notBlank(value, "版本类型代码不能为空");
    String normalized = value.trim().toLowerCase().replace("_", "").replace("-", "");
    for (VersionType type : values()) {
      if (type.code.equals(normalized) || type.name().toLowerCase().equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的版本类型：" + value);
  }

  /// 判断是否为最终出版版。
  ///
  /// @return true 如果为 PUBLISHED
  public boolean isFinalVersion() {
    return this == PUBLISHED;
  }

  /// 判断是否已通过同行评审。
  ///
  /// @return true 如果为 ACCEPTED 或 PUBLISHED
  public boolean isPeerReviewed() {
    return this == ACCEPTED || this == PUBLISHED;
  }

  /// 判断是否比指定版本更终极。
  ///
  /// @param other 比较的版本类型
  /// @return true 如果当前版本更终极
  public boolean isBetterThan(VersionType other) {
    return this.priority > other.priority;
  }
}
