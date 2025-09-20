/**
 * Registry 服务错误处理 API。
 *
 * <p>本包包含错误码目录及相关契约。错误码采用结构化格式，旨在为所有 Registry 操作
 * 提供一致且可机器识别的错误标识。</p>
 *
 * <h2>错误码格式</h2>
 * 全部错误码遵循 {@code REG-NNNN}：
 * <ul>
 *   <li>{@code REG} —— Registry 服务上下文前缀</li>
 *   <li>{@code NNNN} —— 四位数字编码</li>
 * </ul>
 *
 * <h2>分类</h2>
 * <ul>
 *   <li>{@code 0xxx} —— 对齐 HTTP 的通用错误（REG-0400 ~ REG-0504）</li>
 *   <li>{@code 1xxx} —— 领域/业务特定错误</li>
 * </ul>
 *
 * <h2>追加式维护</h2>
 * <ul>
 *   <li>按需新增错误码</li>
 *   <li>既有错误码不删除、不修改</li>
 *   <li>以保障 API 稳定性与兼容性</li>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.registry.api.error;
