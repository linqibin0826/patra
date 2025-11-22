/// Provenance API 参数键名常量
///
/// 定义各数据源的 HTTP 查询参数键名，作为单一事实来源（SSOT）。
///
/// ## 核心组件
///
/// - {@link com.patra.common.provenance.api.params.PubMedParamKeys} - PubMed E-utilities
// 参数键（retstart/retmax、WebEnv/query_key）
///   - {@link com.patra.common.provenance.api.params.EpmcParamKeys} - Europe PMC
// 参数键（pageSize/cursorMark）
///   - {@link com.patra.common.provenance.api.params.CrossrefParamKeys} - Crossref Works API
// 参数键（offset/rows、cursor）
///   - {@link com.patra.common.provenance.api.params.DoajParamKeys} - DOAJ 参数键（page/pageSize）
///
/// ## 使用示例
///
/// ### PubMed 参数
///
/// ```java
/// // 构建 PubMed 查询参数
/// Map<String, String> params = new HashMap<>();
/// params.put(PubMedParamKeys.TERM, "cancer");
/// params.put(PubMedParamKeys.RETMODE, RetMode.JSON.value());
/// params.put(PubMedParamKeys.RETMAX, "100");
/// params.put(PubMedParamKeys.USEHISTORY, UseHistory.YES.value());
/// ```
///
/// ### EPMC 参数
///
/// ```java
/// // 构建 EPMC 查询参数
/// Map<String, String> params = new HashMap<>();
/// params.put(EpmcParamKeys.QUERY, "cancer");
/// params.put(EpmcParamKeys.FORMAT, Format.JSON.value());
/// params.put(EpmcParamKeys.PAGE_SIZE, "100");
/// params.put(EpmcParamKeys.CURSOR_MARK, "*");
/// ```
///
/// ### DOAJ 参数
///
/// ```java
/// // 构建 DOAJ 查询参数
/// Map<String, String> params = new HashMap<>();
/// params.put(DoajParamKeys.QUERY, "cancer");
/// params.put(DoajParamKeys.PAGE, "1");
/// params.put(DoajParamKeys.PAGE_SIZE, "100");
/// params.put(DoajParamKeys.SORT, "title");
/// ```
///
/// ## 架构定位
///
/// **设计原则**：单一事实来源（SSOT）
///
/// - **集中管理**: 所有数据源的参数键名集中定义
///   - **类型安全**: 使用常量替代魔法字符串
///   - **跨模块共享**: 供 starter-provenance、ingest、测试模块使用
///
/// @author linqibin
/// @since 0.1.0
package com.patra.common.provenance.api.params;
