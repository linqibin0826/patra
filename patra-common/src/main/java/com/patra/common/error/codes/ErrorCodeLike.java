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
     * <p>建议遵循统一格式（如 "REG-0404"、"ING-1201"），便于人读与程序处理。</p>
     */
    String code();

    /**
     * 返回与该错误码语义绑定的 HTTP 状态码（100~599）。
     *
     * <p>用于 HTTP 出形时的状态码确定；其他协议可忽略或自定义映射。</p>
     */
    int httpStatus();
}
