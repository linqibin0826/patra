package com.patra.common.error.core;

import cn.hutool.core.util.StrUtil;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 错误规范配置类，定义错误码格式规范、类别映射和推荐配置。
 * 
 * <p>该类提供了以下核心功能：
 * <ul>
 *   <li>错误码格式正则表达式定义</li>
 *   <li>错误类别到 HTTP 状态码的推荐映射</li>
 *   <li>错误码验证和类别判断工具方法</li>
 * </ul>
 * 
 * <p>错误码格式规范：
 * <ul>
 *   <li>完整格式：{MODULE}-{CATEGORY}{NNNN}，例如：REG-C0101</li>
 *   <li>模块：2-4 位大写字母，例如：REG, VAU, DOC</li>
 *   <li>类别：单个大写字母，表示错误分类（C/B/S/N/U）</li>
 *   <li>序号：4 位数字，范围 0001-9999</li>
 * </ul>
 * 
 * <p>HTTP 状态码推荐：
 * <ul>
 *   <li>CLIENT (C类)：400 - 客户端参数、认证、权限等问题</li>
 *   <li>BUSINESS (B类)：409 - 业务规则冲突、状态机错误</li>
 *   <li>SERVER (S类)：500 - 服务端内部异常、资源不可用</li>
 *   <li>NETWORK (N类)：502 - 网络异常、远端服务不可用</li>
 *   <li>UNKNOWN (U类)：500 - 未归类的异常</li>
 * </ul>
 * 
 * <p>使用示例：
 * <pre>{@code
 * // 验证错误码格式
 * boolean valid = ErrorSpec.isValidCodeLiteral("REG-C0101");
 * 
 * // 获取推荐 HTTP 状态码
 * int status = ErrorSpec.recommendedHttpStatus(Category.CLIENT);
 * 
 * // 判断错误类别
 * boolean isClientError = ErrorSpec.isClientSide(Category.CLIENT);
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 * @see ErrorCode
 * @see Category
 */
public final class ErrorSpec {

    /**
     * 私有构造器，防止实例化。
     */
    private ErrorSpec() {}

    /* ==================== 错误码格式常量 ==================== */

    /**
     * 完整错误码格式正则表达式。
     * 
     * <p>格式：{MODULE}-{CATEGORY}{NNNN}
     * <ul>
     *   <li>MODULE: 2-4 位大写字母</li>
     *   <li>CATEGORY: 单个大写字母（C/B/S/N/U）</li>
     *   <li>NNNN: 4 位数字（0001-9999）</li>
     * </ul>
     */
    public static final Pattern FULL_CODE_PATTERN = ErrorCode.FULL_CODE_PATTERN;

    /**
     * 模块名称格式正则表达式：2-4 位大写字母。
     */
    public static final Pattern MODULE_PATTERN = Pattern.compile("^[A-Z]{2,4}$");

    /**
     * 错误序号最小值。
     */
    public static final int MIN_NUMBER = ErrorCode.MIN_NUMBER;

    /**
     * 错误序号最大值。
     */
    public static final int MAX_NUMBER = ErrorCode.MAX_NUMBER;

    /* ==================== HTTP 状态码推荐映射 ==================== */

    /**
     * 错误类别到推荐 HTTP 状态码的映射表。
     * 
     * <p>这些状态码仅作为推荐，具体使用时可以根据业务场景进行调整。
     */
    private static final Map<Category, Integer> DEFAULT_HTTP_BY_CATEGORY = new EnumMap<>(Category.class);

    static {
        // 客户端错误：参数错误、认证失败、权限不足、资源不存在等
        DEFAULT_HTTP_BY_CATEGORY.put(Category.CLIENT, 400);
        // 业务规则错误：状态冲突、业务逻辑不满足、幂等性冲突等
        DEFAULT_HTTP_BY_CATEGORY.put(Category.BUSINESS, 409);
        // 服务端错误：内部异常、资源不可用、依赖组件故障等
        DEFAULT_HTTP_BY_CATEGORY.put(Category.SERVER, 500);
        // 网络错误：远端服务不可用、网关错误、超时等
        DEFAULT_HTTP_BY_CATEGORY.put(Category.NETWORK, 502);
        // 未知错误：未归类的异常情况
        DEFAULT_HTTP_BY_CATEGORY.put(Category.UNKNOWN, 500);
    }

    /* ==================== 工具方法 ==================== */

    /**
     * 获取指定错误类别的推荐 HTTP 状态码。
     * 
     * <p>返回的状态码仅作为建议，具体使用时应根据实际业务场景确定：
     * <ul>
     *   <li>CLIENT 类错误可能使用 400, 401, 403, 404, 422 等</li>
     *   <li>NETWORK 类错误可能使用 429, 502, 503, 504 等</li>
     * </ul>
     * 
     * @param category 错误类别
     * @return 推荐的 HTTP 状态码，如果类别未知则返回 500
     */
    public static int recommendedHttpStatus(Category category) {
        return DEFAULT_HTTP_BY_CATEGORY.getOrDefault(category, 500);
    }

    /**
     * 判断是否为客户端错误类别。
     * 
     * <p>客户端错误通常包括：
     * <ul>
     *   <li>请求参数格式错误或缺失</li>
     *   <li>身份认证失败</li>
     *   <li>权限不足</li>
     *   <li>请求的资源不存在</li>
     * </ul>
     * 
     * @param category 错误类别
     * @return 如果是 CLIENT 类别则返回 true，否则返回 false
     */
    public static boolean isClientSide(Category category) {
        return category == Category.CLIENT;
    }

    /**
     * 判断是否为业务规则错误类别。
     * 
     * <p>业务规则错误通常包括：
     * <ul>
     *   <li>业务状态冲突</li>
     *   <li>业务规则不满足</li>
     *   <li>重复操作导致的冲突</li>
     *   <li>业务流程限制</li>
     * </ul>
     * 
     * @param category 错误类别
     * @return 如果是 BUSINESS 类别则返回 true，否则返回 false
     */
    public static boolean isBusiness(Category category) {
        return category == Category.BUSINESS;
    }

    /**
     * 判断是否为服务端内部错误类别。
     * 
     * <p>服务端错误通常包括：
     * <ul>
     *   <li>程序内部异常</li>
     *   <li>系统资源不足</li>
     *   <li>配置错误</li>
     *   <li>依赖组件故障</li>
     * </ul>
     * 
     * @param category 错误类别
     * @return 如果是 SERVER 类别则返回 true，否则返回 false
     */
    public static boolean isServer(Category category) {
        return category == Category.SERVER;
    }

    /**
     * 判断是否为网络/远端错误类别。
     * 
     * <p>网络错误通常包括：
     * <ul>
     *   <li>网络连接超时</li>
     *   <li>远端服务不可用</li>
     *   <li>网关错误</li>
     *   <li>限流或熔断</li>
     * </ul>
     * 
     * @param category 错误类别
     * @return 如果是 NETWORK 类别则返回 true，否则返回 false
     */
    public static boolean isNetwork(Category category) {
        return category == Category.NETWORK;
    }

    /**
     * 验证错误码字面量的格式合法性。
     * 
     * <p>该方法仅进行正则表达式层面的格式验证，不检查模块是否已注册、
     * 错误码是否已定义等业务层面的有效性。
     * 
     * @param literal 错误码字面量
     * @return 如果格式合法则返回 true，否则返回 false
     */
    public static boolean isValidCodeLiteral(String literal) {
        return StrUtil.isNotBlank(literal) && FULL_CODE_PATTERN.matcher(literal).matches();
    }
}
