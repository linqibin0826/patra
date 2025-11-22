/// Europe PMC 请求模型包。
/// 
/// 定义 Europe PMC API 的请求对象。
/// 
/// ## 请求类型
/// 
/// - {@link SearchRequest} - 搜索请求
/// 
/// ## 使用示例
/// 
/// ```java
/// SearchRequest request = new SearchRequest();
/// request.setQuery("cancer AND open access:y");
/// request.setPageSize(100);
/// request.setFormat("json");
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.provenance.epmc.model.request;
