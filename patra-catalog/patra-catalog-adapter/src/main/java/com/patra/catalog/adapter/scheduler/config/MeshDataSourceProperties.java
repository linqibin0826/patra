package com.patra.catalog.adapter.scheduler.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/// MeSH 数据源配置属性。
///
/// 配置 MeSH 主题词（Descriptor）和限定词（Qualifier）数据文件的下载 URL。
/// 版本号会从文件名自动推断（如 `desc2025.xml` → `2025`）。
///
/// **配置示例**：
///
/// ```yaml
/// patra:
///   catalog:
///     mesh:
///       descriptor-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml
///       qualifier-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml
/// ```
///
/// **数据源说明**：
///
/// - NLM 官方 MeSH XML 文件下载地址
/// - Descriptor 文件约 400MB，包含约 35,000 条主题词
/// - Qualifier 文件约 1MB，包含约 80 条限定词
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "patra.catalog.mesh")
public class MeshDataSourceProperties {

  /// MeSH Descriptor（主题词）XML 文件 URL。
  ///
  /// 文件名格式必须为 `desc{year}.xml`，如 `desc2025.xml`。
  @NotBlank(message = "patra.catalog.mesh.descriptor-url 不能为空")
  private String descriptorUrl;

  /// MeSH Qualifier（限定词）XML 文件 URL。
  ///
  /// 文件名格式必须为 `qual{year}.xml`，如 `qual2025.xml`。
  @NotBlank(message = "patra.catalog.mesh.qualifier-url 不能为空")
  private String qualifierUrl;
}
