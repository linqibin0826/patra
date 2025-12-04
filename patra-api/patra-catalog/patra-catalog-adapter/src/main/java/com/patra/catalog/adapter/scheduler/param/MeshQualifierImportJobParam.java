package com.patra.catalog.adapter.scheduler.param;

/// MeSH 限定词导入任务参数记录。
///
/// 通过 XXL-Job 调度器以 JSON 格式传递的任务参数。所有字段均为必填。
///
/// **导入策略**：
///
/// 纯 INSERT 策略，用于一次性数据初始化。如果表中已有数据，导入会失败。
///
/// JSON 格式示例：
///
/// ```json
/// {
///   "url": "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml",
///   "meshVersion": "2025"
/// }
/// ```
///
/// @param url XML 文件 URL（必填）- MeSH 限定词 XML 文件的 HTTP/HTTPS URL
/// @param meshVersion MeSH 版本（必填）- 如 "2025"
/// @author linqibin
/// @since 0.1.0
public record MeshQualifierImportJobParam(String url, String meshVersion) {}
