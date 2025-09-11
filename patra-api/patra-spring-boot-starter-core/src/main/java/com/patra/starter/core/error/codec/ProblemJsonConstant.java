package com.patra.starter.core.error.codec;

/**
 * RFC7807 Problem Details 标准字段常量和平台扩展字段定义。
 *
 * <p>该类定义了在 Problem JSON 格式中使用的所有字段常量：
 * <ul>
 *   <li>RFC7807 标准字段：type, title, status, detail, instance</li>
 *   <li>平台扩展字段：code, service, traceId, timestamp, extras</li>
 *   <li>媒体类型常量：application/problem+json</li>
 * </ul>
 *
 * <p>使用这些常量可以确保字段名的一致性，避免硬编码字符串的维护问题。
 *
 * <p>参考标准：
 * <ul>
 *   <li>RFC7807: Problem Details for HTTP APIs</li>
 *   <li>HTTP状态码: RFC7231, RFC6585等</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * Map<String, Object> problemMap = new HashMap<>();
 * problemMap.put(ProblemJson.TYPE, "https://example.com/probs/validation-error");
 * problemMap.put(ProblemJson.TITLE, "Validation Error");
 * problemMap.put(ProblemJson.STATUS, 422);
 * problemMap.put(ProblemJson.CODE, "REG-C0101");
 * }</pre>
 *
 * @author linqibin
 * @see <a href="https://tools.ietf.org/html/rfc7807">RFC7807 Problem Details for HTTP APIs</a>
 * @since 0.1.0
 */
public final class ProblemJsonConstant {

    /**
     * 私有构造器，防止实例化。
     */
    private ProblemJsonConstant() {
    }

    /* ==================== 媒体类型常量 ==================== */

    /**
     * Problem Details 标准媒体类型。
     *
     * <p>根据 RFC7807 定义，Problem Details 响应应该使用此媒体类型。
     */
    public static final String MEDIA_TYPE = "application/problem+json";

    /**
     * 包含字符集的完整 Content-Type 头部值。
     *
     * <p>推荐在 HTTP 响应中使用此值作为 Content-Type 头部。
     */
    public static final String CONTENT_TYPE = MEDIA_TYPE + "; charset=utf-8";

    /* ==================== RFC7807 标准字段 ==================== */

    /**
     * 问题类型 URI 字段。
     *
     * <p>该字段应该包含一个 URI 引用，用于标识问题类型。
     * 通常指向一个可读的文档，描述该类型的问题。
     */
    public static final String TYPE = "type";

    /**
     * 问题标题字段。
     *
     * <p>问题类型的简短、人类可读的摘要。
     * 应该保持不变，除非是为了本地化。
     */
    public static final String TITLE = "title";

    /**
     * HTTP 状态码字段。
     *
     * <p>与此问题实例相关的 HTTP 状态码。
     */
    public static final String STATUS = "status";

    /**
     * 问题详情字段。
     *
     * <p>针对此问题实例的人类可读的解释。
     */
    public static final String DETAIL = "detail";

    /**
     * 问题实例 URI 字段。
     *
     * <p>标识此特定问题实例的 URI 引用。
     */
    public static final String INSTANCE = "instance";

    /* ==================== 平台扩展字段 ==================== */

    /**
     * 平台错误码字段。
     *
     * <p>平台内部的错误码标识，格式通常为 "模块-类别错误序号"。
     * 例如：REG-C0101, VAU-B0202 等。
     */
    public static final String CODE = "code";

    /**
     * 服务名称字段。
     *
     * <p>产生该错误的服务名称或标识。
     */
    public static final String SERVICE = "service";

    /**
     * 链路追踪 ID 字段。
     *
     * <p>用于分布式链路追踪的唯一标识符。
     */
    public static final String TRACE_ID = "traceId";

    /**
     * 时间戳字段。
     *
     * <p>错误发生的时间戳，通常使用 ISO-8601 格式。
     */
    public static final String TIMESTAMP = "timestamp";

    /**
     * 扩展信息字段。
     *
     * <p>包含额外的错误相关信息，如参数值、上下文数据等。
     * 该字段的值通常是一个 Map 对象。
     */
    public static final String EXTRAS = "extras";
}
