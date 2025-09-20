package com.patra.common.error.codes;

/**
 * 业务错误码契约，供统一错误处理体系使用。
 *
 * <p>实现需提供全局唯一的错误码标识，用于错误解析、映射与客户端处理。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ErrorCodeLike {
    
    /**
     * 返回唯一的错误码字符串。
     *
     * <p>建议遵循统一格式（如 "REG-0404"、"ORD-1001"），便于人读与程序处理。</p>
     */
    String code();
}
