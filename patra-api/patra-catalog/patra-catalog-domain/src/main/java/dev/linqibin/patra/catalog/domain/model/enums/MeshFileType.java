package dev.linqibin.patra.catalog.domain.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/// MeSH 源文件类型枚举。
///
/// 用于区分 MeSH 数据源的不同文件类型（Descriptor 和 Qualifier）。
///
/// **数据源**：
///
/// - Descriptor（主题词）：`https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc{year}.xml`
/// - Qualifier（限定词）：`https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual{year}.xml`
///
/// **与 MeshDataType 的区别**：
///
/// - `MeshFileType`：表示 NLM 提供的源文件类型（仅 2 种）
/// - `MeshDataType`：表示 MeSH 数据库的数据类型（5 种，含派生数据）
///
/// @author linqibin
/// @since 0.1.0
@Getter
@RequiredArgsConstructor
public enum MeshFileType {

  /// MeSH 主题词文件（Descriptor）
  DESCRIPTOR("主题词", "desc"),

  /// MeSH 限定词文件（Qualifier）
  QUALIFIER("限定词", "qual");

  /// 中文描述（用于日志和用户展示）
  private final String description;

  /// URL 路径前缀（如 desc2025.xml 中的 "desc"）
  private final String urlPrefix;
}
