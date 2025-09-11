package com.patra.common.error.core;

/**
 * 强类型错误定义接口，为错误枚举提供统一的结构约束。
 * 
 * <p>该接口用于定义强类型的错误定义，主要由错误枚举类实现。
 * 通过实现此接口，错误枚举可以提供结构化的错误信息，
 * 便于代码中的类型安全使用和IDE的智能提示。
 * 
 * <p><strong>设计目标：</strong>
 * <ul>
 *   <li><strong>类型安全</strong>：在编译期确保错误码的正确性</li>
 *   <li><strong>代码提示</strong>：IDE可以提供完整的错误定义列表</li>
 *   <li><strong>统一结构</strong>：所有错误定义遵循相同的接口约定</li>
 *   <li><strong>易于维护</strong>：错误定义集中管理，便于维护和查找</li>
 * </ul>
 * 
 * <p><strong>实现示例：</strong>
 * <pre>{@code
 * public enum UserErrorDefs implements ErrorDef {
 *     USERNAME_INVALID(ErrorCode.of("USR-C0101")),
 *     USER_NOT_FOUND(ErrorCode.of("USR-C0404")),
 *     PASSWORD_INCORRECT(ErrorCode.of("USR-C0401"));
 *     
 *     private final ErrorCode code;
 *     
 *     UserErrorDefs(ErrorCode code) {
 *         this.code = code;
 *     }
 *     
 *     @Override
 *     public ErrorCode code() {
 *         return code;
 *     }
 * }
 * }</pre>
 * 
 * <p><strong>使用示例：</strong>
 * <pre>{@code
 * // 类型安全的错误创建
 * PlatformError error = PlatformErrorFactory.of(UserErrorDefs.USERNAME_INVALID)
 *     .title("用户名格式错误")
 *     .detail("用户名必须为3-20个字符")
 *     .build();
 * 
 * // 获取错误码
 * ErrorCode code = UserErrorDefs.USER_NOT_FOUND.code();
 * }</pre>
 * 
 * <p><strong>约定规范：</strong>
 * <ul>
 *   <li>错误枚举名应该语义清晰，采用 UPPER_SNAKE_CASE 命名</li>
 *   <li>错误码应该遵循平台规范：MODULE-CATEGORY+NUMBER</li>
 *   <li>同一模块的错误定义应该集中在一个枚举中</li>
 *   <li>错误码编号应该连续分配，避免随意跳跃</li>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 * @see ErrorCode
 * @see PlatformError
 */
public interface ErrorDef {
    
    /**
     * 获取错误定义对应的平台错误码。
     * 
     * @return 平台错误码对象，不应该为 null
     */
    ErrorCode code();
}
