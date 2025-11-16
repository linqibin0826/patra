/**
 * Provenance API 操作枚举常量
 *
 * <p>定义各数据源的 API 操作枚举，封装操作名称、端点路径和描述信息。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>封装 API 操作的元数据（名称、端点、描述）
 *   <li>提供统一的操作标识符用于日志、指标、异常处理
 *   <li>简化端点路径管理
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.common.provenance.api.constants.PubMedOperation} - PubMed 操作枚举（ESEARCH、EFETCH、EPOST）
 *   <li>{@link com.patra.common.provenance.api.constants.EpmcOperation} - EPMC 操作枚举（SEARCH）
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 获取操作信息
 * PubMedOperation op = PubMedOperation.ESEARCH;
 *
 * // 记录指标
 * metrics.recordApiCall(PROVENANCE, op.getOperationName(), duration);
 *
 * // 构建 URL
 * String url = baseUrl + op.getEndpoint();
 *
 * // 日志记录
 * log.info("调用 {} - {}", op.getOperationName(), op.getDescription());
 * }</pre>
 *
 * <h2>设计说明</h2>
 *
 * <p>相比于 {@code endpoints} 包的简单字符串常量，本包提供：
 *
 * <ul>
 *   <li><b>更丰富的元数据</b> - 操作名称、端点路径、中文描述
 *   <li><b>更好的可读性</b> - 枚举值自解释，无需查文档
 *   <li><b>更强的类型安全</b> - 枚举类型检查，避免传错操作
 *   <li><b>更便于监控</b> - 统一的操作标识符用于日志和指标
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.common.provenance.api.constants;
