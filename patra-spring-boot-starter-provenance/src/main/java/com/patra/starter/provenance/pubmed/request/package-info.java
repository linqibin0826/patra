/// PubMed 请求装配包。
/// 
/// 提供 PubMed API 请求参数的构建和装配工具。
/// 
/// ## 职责
/// 
/// - 将请求对象转换为 URL 查询参数
///   - 应用 PubMed API 参数约定
///   - 处理参数编码和格式化
/// 
/// ## 核心组件
/// 
/// - {@link PubMedParamKeys} - PubMed 参数键常量
///   - {@link PubMedESearchRequestAssembler} - ESearch 请求装配器
/// 
/// ## 使用示例
/// 
/// ```java
/// ESearchRequest request = new ESearchRequest();
/// request.setTerm("cancer");
/// request.setRetMax(100);
/// 
/// Map<String, String> params = PubMedESearchRequestAssembler.assemble(request);
/// // params: {term=cancer, retmax=100, db=pubmed, retmode=json
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.provenance.pubmed.request;
