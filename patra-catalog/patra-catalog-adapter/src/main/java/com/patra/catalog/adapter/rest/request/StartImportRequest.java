package com.patra.catalog.adapter.rest.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/// 开始 MeSH 导入任务 HTTP 请求参数。
/// 
/// 接收前端传入的原始 HTTP 请求参数，在 Controller 中转换为 {@link
/// com.patra.catalog.app.usecase.meshimport.command.StartImportCommand} 后传递给应用层。
/// 
/// **参数说明**：
/// 
/// - `sourceUrl` - 数据源 URL（可选，默认从配置读取）
///   - `taskName` - 任务名称（可选，默认生成：如 "2025年MeSH数据导入"）
/// 
/// **参数校验**：
/// 
/// - sourceUrl 必须是有效的 HTTP/HTTPS URL
///   - taskName 长度不超过 100 个字符
/// 
/// **使用示例**：
/// 
/// ```java
/// // 使用默认配置
/// POST /api/v1/mesh/import/start
/// Content-Type: application/json
/// {
/// 
/// // 指定自定义 URL
/// POST /api/v1/mesh/import/start
/// Content-Type: application/json
/// {
///   "sourceUrl": "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
///   "taskName": "2025年MeSH数据首次导入"
/// ```
/// 
/// @author linqibin
/// @since 0.2.0
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartImportRequest {

  /// 数据源 URL（可选）。
/// 
/// 如果为 null，则使用配置文件中的默认值：
/// 
/// ```
/// 
/// patra.catalog.mesh.import.source-url=https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml
/// 
/// ```
  @Pattern(
      regexp = "^https?://.*",
      message = "数据源 URL 必须是有效的 HTTP/HTTPS 地址")
  private String sourceUrl;

  /// 任务名称（可选）。
/// 
/// 如果为 null，则自动生成任务名称（格式："{year}年MeSH数据导入"）
  @Size(max = 100, message = "任务名称长度不能超过 100 个字符")
  private String taskName;
}
