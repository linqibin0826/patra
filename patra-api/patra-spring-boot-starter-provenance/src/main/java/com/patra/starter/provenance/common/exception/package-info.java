/// Provenance 异常定义包。
///
/// 定义 Provenance Starter 使用的异常类型。
///
/// ## 职责
///
/// - 封装数据源调用过程中的异常
///   - 提供统一的错误处理和上下文传递
///
/// ## 核心组件
///
/// - {@link ProvenanceClientException} - Provenance 客户端异常基类
///
/// ## 异常层次
///
/// ```
///
/// RuntimeException
/// └── ProvenanceClientException
///     ├── HTTP 调用失败
///     ├── 响应解析失败
///     └── 配置验证失败
///
/// ```
///
/// ## 使用示例
///
/// ```java
/// try {
///     ESearchResponse response = client.esearch(request); catch (ProvenanceClientException ex) {
///     log.error("PubMed 调用失败: {", ex.getMessage());
///     // 处理异常...
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.provenance.common.exception;
