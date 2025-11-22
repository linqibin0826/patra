/// Europe PMC 响应模型包。
/// 
/// 定义 Europe PMC API 的响应对象，映射 JSON 响应结构。
/// 
/// ## 响应类型
/// 
/// - {@link SearchResponse} - 搜索响应
/// 
/// ## 使用示例
/// 
/// ```java
/// SearchResponse response = client.search(request);
/// int totalResults = response.getHitCount();
/// List<Result> results = response.getResultList();
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.provenance.epmc.model.response;
