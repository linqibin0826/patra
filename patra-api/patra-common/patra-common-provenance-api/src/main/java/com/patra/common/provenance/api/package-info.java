/// Provenance API 常量和枚举模块
///
/// 本模块提供 Provenance 数据源（PubMed、EPMC、Crossref 等）的 API 常量和类型安全枚举，
/// 作为跨模块共享的单一事实来源（SSOT）。
///
/// ## 模块结构
///
/// - **endpoints** - API 端点路径常量（如 `/esearch.fcgi`）
///   - **params** - API 参数键名常量（如 `"retmode"`）
///   - **values** - API 参数值枚举（如 `RetMode.JSON`）
///
/// ## 使用示例
///
/// ```java
/// // 使用端点常量
/// String url = baseUrl + PubMedEndpoints.ESEARCH;
///
/// // 使用参数键
/// params.put(PubMedParamKeys.RETMODE, RetMode.JSON.value());
///
/// // 类型安全的枚举
/// if (request.retmode() == RetMode.XML) {
///     // 处理 XML 格式
/// ```
///
/// @author linqibin
/// @since 0.1.0
package com.patra.common.provenance.api;
