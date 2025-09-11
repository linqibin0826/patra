package com.patra.starter.core.error.codec;

import com.patra.common.error.core.PlatformError;

import java.util.Map;

/**
 * 平台错误编解码器接口，负责 PlatformError 与各种数据格式之间的转换。
 * 
 * <p>该接口定义了平台错误对象与外部表示形式之间的双向转换能力：
 * <ul>
 *   <li>支持 Map 格式转换（便于框架集成）</li>
 *   <li>支持 JSON 字符串转换（便于 HTTP API 响应）</li>
 *   <li>支持字节数组转换（便于网络传输和存储）</li>
 * </ul>
 * 
 * <p>编码输出遵循 RFC7807 Problem Details 标准，同时支持平台扩展字段。
 * 解码时采用宽松策略，容忍字段缺失和类型不匹配。
 * 
 * <p>实现要求：
 * <ul>
 *   <li>所有方法必须线程安全</li>
 *   <li>编码方法不接受 null 参数</li>
 *   <li>解码方法对异常输入要有兜底处理</li>
 *   <li>生成的 JSON 必须符合 application/problem+json 媒体类型</li>
 * </ul>
 * 
 * <p>使用示例：
 * <pre>{@code
 * PlatformErrorCodec codec = new JacksonPlatformErrorCodec();
 * 
 * // 编码示例
 * PlatformError error = PlatformError.builder("REG-C0101")
 *     .title("Parameter missing")
 *     .status(422)
 *     .build();
 * 
 * String json = codec.toJson(error);
 * Map<String, Object> map = codec.toProblemMap(error);
 * byte[] bytes = codec.toBytes(error);
 * 
 * // 解码示例
 * PlatformError decoded1 = codec.fromJson(json);
 * PlatformError decoded2 = codec.fromProblemMap(map);
 * PlatformError decoded3 = codec.fromBytes(bytes);
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 * @see PlatformError
 * @see ProblemJsonConstant
 */
public interface PlatformErrorCodec {

    /* ==================== 编码方法 ==================== */

    /**
     * 将平台错误对象转换为 Problem JSON 格式的 Map。
     * 
     * <p>生成的 Map 键名遵循 RFC7807 标准加平台扩展字段约定：
     * <ul>
     *   <li>标准字段：type, title, status, detail, instance</li>
     *   <li>扩展字段：code, service, traceId, timestamp, extras</li>
     * </ul>
     * 
     * @param error 平台错误对象，不能为 null
     * @return Problem JSON 格式的 Map，never null
     * @throws NullPointerException 如果 error 为 null
     * @throws IllegalArgumentException 如果 error 包含无效数据
     */
    Map<String, Object> toProblemMap(PlatformError error);

    /**
     * 将平台错误对象转换为 JSON 字符串。
     * 
     * <p>生成的 JSON 符合 application/problem+json 媒体类型规范，
     * 包含 RFC7807 标准字段和平台扩展字段。
     * 
     * @param error 平台错误对象，不能为 null
     * @return JSON 字符串，never null
     * @throws NullPointerException 如果 error 为 null
     * @throws IllegalStateException 如果 JSON 序列化失败
     */
    String toJson(PlatformError error);

    /**
     * 将平台错误对象转换为 UTF-8 编码的字节数组。
     * 
     * <p>等价于 {@code toJson(error).getBytes(StandardCharsets.UTF_8)}，
     * 但实现可能进行性能优化。
     * 
     * @param error 平台错误对象，不能为 null
     * @return UTF-8 编码的 JSON 字节数组，never null
     * @throws NullPointerException 如果 error 为 null
     * @throws IllegalStateException 如果序列化失败
     */
    byte[] toBytes(PlatformError error);

    /* ==================== 解码方法 ==================== */

    /**
     * 从 Problem JSON 格式的 Map 构建平台错误对象。
     * 
     * <p>解码采用宽松策略：
     * <ul>
     *   <li>容忍字段缺失，使用默认值</li>
     *   <li>容忍类型不匹配，尝试类型转换</li>
     *   <li>忽略不认识的字段</li>
     *   <li>对于严重格式错误，返回兜底错误对象</li>
     * </ul>
     * 
     * @param problemMap Problem JSON 格式的 Map，可以为 null 或空
     * @return 平台错误对象，never null
     */
    PlatformError fromProblemMap(Map<String, ?> problemMap);

    /**
     * 从 JSON 字符串构建平台错误对象。
     * 
     * <p>解码采用宽松策略，对于无法解析的 JSON 会返回兜底错误对象。
     * 支持的 JSON 格式包括：
     * <ul>
     *   <li>标准的 Problem JSON 格式</li>
     *   <li>包含平台扩展字段的格式</li>
     *   <li>部分字段缺失的简化格式</li>
     * </ul>
     * 
     * @param json JSON 字符串，可以为 null 或空
     * @return 平台错误对象，never null
     */
    PlatformError fromJson(String json);

    /**
     * 从 UTF-8 编码的字节数组构建平台错误对象。
     * 
     * <p>等价于 {@code fromJson(new String(bytes, StandardCharsets.UTF_8))}，
     * 但实现可能进行性能优化。
     * 
     * @param bytes UTF-8 编码的 JSON 字节数组，可以为 null 或空
     * @return 平台错误对象，never null
     */
    PlatformError fromBytes(byte[] bytes);

    /* ==================== 默认方法 ==================== */

    /**
     * 检查编解码器是否支持指定的媒体类型。
     * 
     * @param mediaType 媒体类型字符串
     * @return 如果支持返回 true，否则返回 false
     */
    default boolean supports(String mediaType) {
        return ProblemJsonConstant.MEDIA_TYPE.equals(mediaType);
    }

    /**
     * 获取编解码器支持的媒体类型。
     * 
     * @return 支持的媒体类型字符串
     */
    default String getSupportedMediaType() {
        return ProblemJsonConstant.MEDIA_TYPE;
    }

    /**
     * 获取编解码器的 Content-Type 头部值。
     * 
     * @return Content-Type 头部值（包含字符集）
     */
    default String getContentType() {
        return ProblemJsonConstant.MEDIA_TYPE + "; charset=utf-8";
    }
}
