package com.patra.common.error.core;

/**
 * 错误类别枚举，定义平台错误的分类体系。
 * 
 * <p>错误类别用于对错误进行分类，便于统一处理和监控。每个类别对应一个
 * 单字符符号，用于错误码的构成。类别设计基于错误的根本原因和责任归属：
 * 
 * <ul>
 *   <li><strong>CLIENT (C)</strong>：客户端错误，通常由调用方的问题导致</li>
 *   <li><strong>BUSINESS (B)</strong>：业务逻辑错误，由业务规则或状态不满足导致</li>
 *   <li><strong>SERVER (S)</strong>：服务端内部错误，由系统内部问题导致</li>
 *   <li><strong>NETWORK (N)</strong>：网络或远端错误，由外部依赖问题导致</li>
 *   <li><strong>UNKNOWN (U)</strong>：未知错误，暂时无法分类的异常情况</li>
 * </ul>
 * 
 * <p><strong>使用场景：</strong>
 * <ul>
 *   <li>错误码构成：MODULE-{CATEGORY}{NNNN}，如 REG-C0101</li>
 *   <li>HTTP 状态码映射：不同类别建议不同的 HTTP 状态码</li>
 *   <li>监控告警：根据类别设置不同的告警策略</li>
 *   <li>错误处理：根据类别采用不同的处理策略</li>
 * </ul>
 * 
 * <p><strong>类别详细说明：</strong>
 * <table border="1">
 *   <tr><th>类别</th><th>符号</th><th>典型场景</th><th>建议HTTP状态</th></tr>
 *   <tr><td>CLIENT</td><td>C</td><td>参数错误、认证失败、权限不足、资源不存在</td><td>400, 401, 403, 404</td></tr>
 *   <tr><td>BUSINESS</td><td>B</td><td>业务规则不满足、状态冲突、重复操作</td><td>409, 422</td></tr>
 *   <tr><td>SERVER</td><td>S</td><td>内部异常、资源不足、配置错误</td><td>500</td></tr>
 *   <tr><td>NETWORK</td><td>N</td><td>网络超时、远端不可用、限流熔断</td><td>502, 503, 504</td></tr>
 *   <tr><td>UNKNOWN</td><td>U</td><td>未归类的异常情况</td><td>500</td></tr>
 * </table>
 * 
 * <p><strong>使用示例：</strong>
 * <pre>{@code
 * // 获取类别符号
 * char symbol = Category.CLIENT.symbol(); // 返回 'C'
 * 
 * // 从符号解析类别
 * Category category = Category.fromSymbol('B'); // 返回 BUSINESS
 * 
 * // 错误码中使用
 * String errorCode = "REG-" + Category.CLIENT.symbol() + "0101"; // "REG-C0101"
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 * @see ErrorCode
 * @see ErrorSpec
 */
public enum Category {
    
    /**
     * 客户端错误类别。
     * 
     * <p>表示错误由调用方（客户端）的问题导致，包括：
     * <ul>
     *   <li>请求参数格式错误或缺失</li>
     *   <li>身份认证失败</li>
     *   <li>权限不足，无法访问资源</li>
     *   <li>请求的资源不存在</li>
     *   <li>请求格式不正确</li>
     * </ul>
     */
    CLIENT('C'),

    /**
     * 业务逻辑错误类别。
     * 
     * <p>表示错误由业务规则或状态不满足导致，包括：
     * <ul>
     *   <li>业务规则验证失败</li>
     *   <li>业务状态冲突</li>
     *   <li>重复操作或幂等性检查失败</li>
     *   <li>业务流程限制</li>
     *   <li>数据一致性冲突</li>
     * </ul>
     */
    BUSINESS('B'),

    /**
     * 服务端内部错误类别。
     * 
     * <p>表示错误由服务端内部问题导致，包括：
     * <ul>
     *   <li>程序代码异常</li>
     *   <li>系统资源不足（内存、磁盘等）</li>
     *   <li>配置错误</li>
     *   <li>内部组件故障</li>
     *   <li>数据库连接失败</li>
     * </ul>
     */
    SERVER('S'),

    /**
     * 网络或远端错误类别。
     * 
     * <p>表示错误由网络问题或外部依赖导致，包括：
     * <ul>
     *   <li>网络连接超时</li>
     *   <li>远端服务不可用</li>
     *   <li>网关错误</li>
     *   <li>限流或熔断保护</li>
     *   <li>第三方服务异常</li>
     * </ul>
     */
    NETWORK('N'),

    /**
     * 未知错误类别。
     * 
     * <p>表示暂时无法准确分类的异常情况，包括：
     * <ul>
     *   <li>新发现的异常，尚未分类</li>
     *   <li>系统异常兜底处理</li>
     *   <li>临时使用的错误分类</li>
     * </ul>
     * 
     * <p><strong>注意：</strong>该类别应该尽量避免使用，
     * 建议将未知错误归类到具体的类别中。
     */
    UNKNOWN('U');

    /* ==================== 字段和构造器 ==================== */

    /**
     * 类别在错误码中的单字符表示。
     */
    private final char symbol;

    /**
     * 构造器。
     * 
     * @param symbol 类别符号
     */
    Category(char symbol) {
        this.symbol = symbol;
    }

    /* ==================== 公共方法 ==================== */

    /**
     * 获取类别在错误码中的单字符表示。
     * 
     * @return 类别符号（C/B/S/N/U）
     */
    public char symbol() {
        return symbol;
    }

    /**
     * 通过单字符符号解析错误类别。
     * 
     * @param symbol 类别符号字符
     * @return 对应的错误类别
     * @throws IllegalArgumentException 如果符号不是有效的类别符号
     */
    public static Category fromSymbol(char symbol) {
        return switch (symbol) {
            case 'C' -> CLIENT;
            case 'B' -> BUSINESS;
            case 'S' -> SERVER;
            case 'N' -> NETWORK;
            case 'U' -> UNKNOWN;
            default -> throw new IllegalArgumentException(
                    "Unknown category symbol: '" + symbol + "'. Valid symbols are: C, B, S, N, U");
        };
    }
}
