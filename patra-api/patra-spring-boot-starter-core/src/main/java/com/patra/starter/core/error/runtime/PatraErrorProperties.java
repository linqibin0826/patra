package com.patra.starter.core.error.runtime;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Patra 平台错误处理框架配置属性类。
 * 
 * <p>该配置类定义了错误处理框架的所有可配置选项，包括：
 * <ul>
 *   <li>框架启用/禁用控制</li>
 *   <li>错误码册加载和验证策略</li>
 *   <li>冲突检测和处理策略</li>
 *   <li>日志记录和调试选项</li>
 *   <li>敏感信息脱敏配置</li>
 * </ul>
 * 
 * <p>配置示例：
 * <pre>{@code
 * # application.yml
 * patra:
 *   error:
 *     enabled: true
 *     fail-fast: false
 *     log-summary: true
 *     validate-module-prefix: true
 *     detect-conflict: true
 *     fail-on-conflict: false
 *     redacted-keys:
 *       - token
 *       - password
 *       - secret
 *       - apiKey
 * }</pre>
 * 
 * <p>配置说明：
 * <ul>
 *   <li>enabled: 控制整个错误处理框架是否启用</li>
 *   <li>failFast: 遇到配置错误时是否快速失败</li>
 *   <li>logSummary: 是否记录错误码册加载摘要日志</li>
 *   <li>validateModulePrefix: 是否验证错误码的模块前缀</li>
 *   <li>detectConflict: 是否检测错误码定义冲突</li>
 *   <li>failOnConflict: 检测到冲突时是否抛出异常</li>
 *   <li>redactedKeys: 需要脱敏的字段名列表</li>
 * </ul>
 * 
 * <p>最佳实践：
 * <ul>
 *   <li>开发环境建议设置 failFast=true，及时发现配置问题</li>
 *   <li>生产环境建议设置 failFast=false，增强容错性</li>
 *   <li>测试环境建议启用所有验证选项，确保配置正确性</li>
 *   <li>根据业务需要调整 redactedKeys 列表</li>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 * @see PatraErrorAutoConfiguration
 */
@Data
@ConfigurationProperties(prefix = "patra.error")
public class PatraErrorProperties {

    /**
     * 是否启用平台错误处理框架。
     * 
     * <p>设置为 false 时，自动配置将不会生效，错误处理框架将被完全禁用。
     * 默认值：true
     */
    private boolean enabled = true;

    /**
     * 遇到配置错误时是否快速失败。
     * 
     * <p>设置为 true 时，遇到以下情况会抛出异常：
     * <ul>
     *   <li>找不到任何错误码册文件</li>
     *   <li>错误码格式不符合规范</li>
     *   <li>模块前缀未注册</li>
     *   <li>错误码定义冲突（当 failOnConflict=true 时）</li>
     * </ul>
     * 
     * <p>设置为 false 时，上述问题只会记录警告日志，不会中断应用启动。
     * 默认值：true（推荐开发环境使用）
     */
    private boolean failFast = true;

    /**
     * 是否记录错误码册加载摘要日志。
     * 
     * <p>启用时会记录以下信息：
     * <ul>
     *   <li>已加载的模块列表</li>
     *   <li>错误码册文件数量和条目数量</li>
     *   <li>加载过程中的详细信息</li>
     * </ul>
     * 
     * 默认值：true
     */
    private boolean logSummary = true;

    /**
     * 需要脱敏的字段名列表。
     * 
     * <p>在错误信息的扩展字段中，如果字段名包含在此列表中，
     * 字段值将被替换为 "***" 以保护敏感信息。
     * 
     * <p>匹配方式：不区分大小写的包含匹配。
     * 
     * 默认值：["token", "password", "secret"]
     */
    private List<String> redactedKeys = List.of("token", "password", "secret");

    /**
     * 是否验证错误码的模块前缀。
     * 
     * <p>启用时，系统会检查错误码中的模块前缀是否在模块注册表中注册。
     * 未注册的模块前缀会根据 failFast 设置决定是否抛出异常。
     * 
     * 默认值：true
     */
    private boolean validateModulePrefix = true;

    /**
     * 是否检测错误码定义冲突。
     * 
     * <p>启用时，系统会检测相同错误码在不同文件中是否有不同的定义
     * （如不同的 title 或 httpStatus）。
     * 
     * 默认值：true
     */
    private boolean detectConflict = true;

    /**
     * 检测到错误码冲突时是否抛出异常。
     * 
     * <p>仅在 detectConflict=true 时生效。
     * 设置为 false 时，冲突只会记录警告日志。
     * 
     * 默认值：true
     */
    private boolean failOnConflict = true;
}
