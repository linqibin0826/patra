package com.patra.catalog.adapter.scheduler.param;

/// MeSH 导入任务参数记录。
///
/// 通过 XXL-Job 调度器以 JSON 格式传递的任务参数。所有字段均为必填。
///
/// JSON 格式示例：
///
/// ```json
/// {
///   "url": "https://nlmpubs.nlm.nih.gov/projects/mesh/2025/desc2025.xml",
///   "meshVersion": "2025",
///   "mode": "INCREMENTAL"
/// }
/// ```
///
/// @param url XML 文件 URL（必填）- MeSH 描述符 XML 文件的 HTTP/HTTPS URL
/// @param meshVersion MeSH 版本（必填）- 如 "2025"
/// @param mode 导入模式（必填）- INCREMENTAL 或 TRUNCATE_REIMPORT
/// @author linqibin
/// @since 0.1.0
public record MeshImportJobParam(String url, String meshVersion, String mode) {}
