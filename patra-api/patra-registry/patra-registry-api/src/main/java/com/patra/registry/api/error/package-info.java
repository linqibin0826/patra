/**
 * Registry 服务错误处理 API 包。
 *
 * <p>本包定义错误码目录和相关契约。错误码遵循结构化格式,确保所有 Registry 操作暴露一致的、机器可读的标识符。
 *
 * <h2>错误码格式</h2>
 *
 * 所有错误码遵循 {@code REG-NNNN}:
 *
 * <ul>
 *   <li>{@code REG} - Registry 服务前缀
 *   <li>{@code NNNN} - 四位数字代码
 * </ul>
 *
 * <h2>系列定义</h2>
 *
 * <ul>
 *   <li>{@code 0xxx} - 通过 {@code HttpStdErrors} 生成的通用 HTTP 对齐错误
 *   <li>{@code 1xxx} - 目录中维护的领域或业务特定错误
 * </ul>
 *
 * <h2>追加式维护</h2>
 *
 * <ul>
 *   <li>可根据需要添加新代码
 *   <li>现有代码不得删除或修改
 *   <li>这保证了 API 稳定性和向后兼容性
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.registry.api.error;
