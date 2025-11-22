/// Europe PMC 请求装配包。
/// 
/// 提供 Europe PMC API 请求参数的构建和装配工具。
/// 
/// ## 职责
/// 
/// - 将请求对象转换为 URL 查询参数
///   - 应用 EPMC API 参数约定
///   - 处理参数编码和格式化
/// 
/// ## 核心组件
/// 
/// - {@link EpmcParamKeys} - EPMC 参数键常量
///   - {@link EpmcSearchRequestAssembler} - 搜索请求装配器
/// 
/// ## 使用示例
/// 
/// ```java
/// SearchRequest request = new SearchRequest();
/// request.setQuery("cancer");
/// request.setPageSize(100);
/// 
/// Map<String, String> params = EpmcSearchRequestAssembler.assemble(request);
/// // params: {query=cancer, pageSize=100, format=json
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.provenance.epmc.request;
