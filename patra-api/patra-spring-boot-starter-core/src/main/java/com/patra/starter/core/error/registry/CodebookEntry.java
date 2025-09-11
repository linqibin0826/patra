package com.patra.starter.core.error.registry;

import cn.hutool.core.util.StrUtil;
import com.patra.common.error.core.ErrorCode;
import lombok.Builder;

import java.util.Map;
import java.util.Objects;

/**
 * 错误码条目记录，用于存储单个错误码的完整元数据信息。
 * 
 * <p>主要用途：
 * <ul>
 *   <li>错误码文档生成和维护</li>
 *   <li>错误码格式和内容校验</li>
 *   <li>错误信息的统一展示和管理</li>
 *   <li>团队责任划分和联系方式记录</li>
 * </ul>
 * 
 * <p>字段说明：
 * <ul>
 *   <li>code: 错误码对象，必须符合平台错误码规范</li>
 *   <li>title: 人类可读的错误标题，用于用户界面显示</li>
 *   <li>httpStatus: 建议的 HTTP 状态码，可以为 null</li>
 *   <li>doc: 错误码详细文档链接，可以为 null</li>
 *   <li>owner: 负责人或团队标识，可以为 null</li>
 *   <li>extras: 扩展信息映射，支持自定义元数据，可以为 null</li>
 * </ul>
 * 
 * <p>使用示例：
 * <pre>{@code
 * ErrorCode errorCode = ErrorCode.of("REG-C0101");
 * CodebookEntry entry = new CodebookEntry(
 *     errorCode,
 *     "用户名参数缺失",
 *     422,
 *     "https://docs.example.com/errors/REG-C0101",
 *     "registration-team",
 *     Map.of("severity", "high", "category", "validation")
 * );
 * 
 * // 使用 Builder 模式
 * CodebookEntry entry2 = CodebookEntry.builder()
 *     .code(errorCode)
 *     .title("用户名参数缺失")
 *     .httpStatus(422)
 *     .owner("registration-team")
 *     .build();
 * }</pre>
 * 
 * <p>验证规则：
 * <ul>
 *   <li>code 字段不能为 null</li>
 *   <li>title 建议非空，但允许为 null（用于占位或临时条目）</li>
 *   <li>httpStatus 如果提供，应该是有效的 HTTP 状态码</li>
 *   <li>其他字段均为可选</li>
 * </ul>
 *
 * @param code 错误码对象，不能为 null
 * @param title 错误标题，可以为 null
 * @param httpStatus 建议的 HTTP 状态码，可以为 null
 * @param doc 文档链接，可以为 null
 * @param owner 负责人或团队，可以为 null
 * @param extras 扩展信息，可以为 null
 * 
 * @author linqibin
 * @since 0.1.0
 * @see ErrorCode
 * @see Codebook
 */
@Builder
public record CodebookEntry(
        ErrorCode code,
        String title,
        Integer httpStatus,
        String doc,
        String owner,
        Map<String, Object> extras
) {
    
    /**
     * 紧凑构造器，执行参数验证和规范化。
     */
    public CodebookEntry {
        Objects.requireNonNull(code, "ErrorCode must not be null");
        
        // 验证 HTTP 状态码的合理性
        if (httpStatus != null && (httpStatus < 100 || httpStatus > 599)) {
            throw new IllegalArgumentException("Invalid HTTP status code: " + httpStatus);
        }
        
        // 规范化字符串字段：空字符串转为 null
        title = StrUtil.emptyToNull(title);
        doc = StrUtil.emptyToNull(doc);
        owner = StrUtil.emptyToNull(owner);
    }
    
    /**
     * 检查当前条目是否包含有效的标题信息。
     * 
     * @return 如果 title 非空且非空白字符串返回 true，否则返回 false
     */
    public boolean hasTitle() {
        return StrUtil.isNotBlank(title);
    }
    
    /**
     * 检查当前条目是否指定了 HTTP 状态码。
     * 
     * @return 如果 httpStatus 非 null 返回 true，否则返回 false
     */
    public boolean hasHttpStatus() {
        return httpStatus != null;
    }
    
    /**
     * 检查当前条目是否包含文档链接。
     * 
     * @return 如果 doc 非空且非空白字符串返回 true，否则返回 false
     */
    public boolean hasDocumentation() {
        return StrUtil.isNotBlank(doc);
    }
    
    /**
     * 检查当前条目是否指定了负责人信息。
     * 
     * @return 如果 owner 非空且非空白字符串返回 true，否则返回 false
     */
    public boolean hasOwner() {
        return StrUtil.isNotBlank(owner);
    }
    
    /**
     * 检查当前条目是否包含扩展信息。
     * 
     * @return 如果 extras 非 null 且非空返回 true，否则返回 false
     */
    public boolean hasExtras() {
        return extras != null && !extras.isEmpty();
    }
    
    /**
     * 获取错误码的字符串表示形式。
     * 
     * @return 错误码字符串
     */
    public String getCodeString() {
        return code.toString();
    }
}
