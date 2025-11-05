/**
 * Provenance 异常定义包。
 *
 * <p>定义 Provenance Starter 使用的异常类型。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>封装数据源调用过程中的异常
 *   <li>提供统一的错误处理和上下文传递
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link ProvenanceClientException} - Provenance 客户端异常基类
 * </ul>
 *
 * <h2>异常层次</h2>
 *
 * <pre>
 * RuntimeException
 * └── ProvenanceClientException
 *     ├── HTTP 调用失败
 *     ├── 响应解析失败
 *     └── 配置验证失败
 * </pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * try {
 *     ESearchResponse response = client.esearch(request);
 * } catch (ProvenanceClientException ex) {
 *     log.error("PubMed 调用失败: {}", ex.getMessage());
 *     // 处理异常...
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.common.exception;
