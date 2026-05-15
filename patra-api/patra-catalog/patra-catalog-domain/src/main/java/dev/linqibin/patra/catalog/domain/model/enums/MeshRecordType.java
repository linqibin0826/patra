package dev.linqibin.patra.catalog.domain.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/// MeSH 记录类型枚举。
///
/// 定义 MeSH 数据库中的两种主要记录类型：
///
/// - **DESCRIPTOR**：主题词（Descriptor），MeSH 的核心概念，用于 PubMed 索引
/// - **SCR**：补充概念（Supplementary Concept Record），用于扩展主题词覆盖范围
///
/// 业务规则：
///
/// - 两种记录类型的 Concept 和 Term 结构相同，可共用领域模型
/// - 通过 UI 前缀区分：D（Descriptor）、C（SCR）
/// - 每种类型有独立的聚合根和持久化表
///
/// @author linqibin
/// @since 0.1.0
@Getter
@RequiredArgsConstructor
public enum MeshRecordType {

  /// 主题词 - MeSH 的核心概念，用于 PubMed 文献索引。
  DESCRIPTOR("D", "主题词", 'D'),

  /// 补充概念 - 扩展主题词的覆盖范围，包括化学物质、药物、罕见病等。
  SCR("C", "补充概念", 'C');

  /// 记录类型代码（用于数据库存储、配置文件）。
  private final String code;

  /// 记录类型中文名（用于日志和用户展示）。
  private final String displayName;

  /// UI 前缀字符（用于关联 MeshUI）。
  private final char uiPrefix;

  /// 根据 code 查找枚举。
  ///
  /// @param code 记录类型代码（D 或 C，不区分大小写）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果 code 不存在
  public static MeshRecordType fromCode(String code) {
    if (code == null) {
      throw new IllegalArgumentException("未知的 MeSH 记录类型: null");
    }
    String upperCode = code.toUpperCase();
    for (MeshRecordType type : values()) {
      if (type.code.equals(upperCode)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的 MeSH 记录类型: " + code);
  }

  /// 根据 UI 前缀查找枚举。
  ///
  /// @param prefix UI 前缀字符（D 或 C，不区分大小写）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果前缀无效
  public static MeshRecordType fromUiPrefix(char prefix) {
    char upperPrefix = Character.toUpperCase(prefix);
    for (MeshRecordType type : values()) {
      if (type.uiPrefix == upperPrefix) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的 UI 前缀: " + prefix);
  }
}
