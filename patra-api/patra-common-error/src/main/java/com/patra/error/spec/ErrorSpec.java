package com.patra.error.spec;

import com.patra.error.core.Category;
import com.patra.error.core.ErrorCode;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 规范常量与推荐映射：
 * - 错误码正则
 * - 类别到 HTTP 状态的推荐映射
 * - 常用判断与工具
 */
public final class ErrorSpec {

    /**
     * 完整错误码格式：{MODULE}-{CATEGORY}{NNNN}
     */
    public static final Pattern FULL_CODE_PATTERN = ErrorCode.FULL_CODE_PATTERN;

    /**
     * MODULE: 2~4 位大写字母
     */
    public static final Pattern MODULE_PATTERN = Pattern.compile("^[A-Z]{2,4}$");

    /**
     * 数字段边界
     */
    public static final int MIN_NUMBER = ErrorCode.MIN_NUMBER;
    public static final int MAX_NUMBER = ErrorCode.MAX_NUMBER;

    /**
     * 类别到“推荐状态码”的缺省映射（可按场景在上层覆写）
     */
    private static final Map<Category, Integer> DEFAULT_HTTP_BY_CATEGORY = new EnumMap<>(Category.class);

    static {
        DEFAULT_HTTP_BY_CATEGORY.put(Category.CLIENT, 400); // 参数/认证/权限/不存在，实际可能 401/403/404/422
        DEFAULT_HTTP_BY_CATEGORY.put(Category.BUSINESS, 409); // 冲突/状态机/幂等冲突
        DEFAULT_HTTP_BY_CATEGORY.put(Category.SERVER, 500); // 进程内异常/资源/依赖组件
        DEFAULT_HTTP_BY_CATEGORY.put(Category.NETWORK, 502); // 默认按“远端/网关错误”，也可能 429/503/504
        DEFAULT_HTTP_BY_CATEGORY.put(Category.UNKNOWN, 500); // 未归类
    }

    private ErrorSpec() {
    }

    /**
     * 获取类别的推荐 HTTP 状态码（仅建议；具体由上层决定）
     */
    public static int recommendedHttpStatus(Category category) {
        return DEFAULT_HTTP_BY_CATEGORY.getOrDefault(category, 500);
    }

    /**
     * 判断是否属于“调用方问题”（C 类）
     */
    public static boolean isClientSide(Category category) {
        return category == Category.CLIENT;
    }

    /**
     * 判断是否属于“业务冲突/规则”（B 类）
     */
    public static boolean isBusiness(Category category) {
        return category == Category.BUSINESS;
    }

    /**
     * 判断是否为“进程内服务端错误”（S 类）
     */
    public static boolean isServer(Category category) {
        return category == Category.SERVER;
    }

    /**
     * 判断是否为“网络/远端/网关”（N 类）
     */
    public static boolean isNetwork(Category category) {
        return category == Category.NETWORK;
    }

    /**
     * 错误码快速合法性判断（仅正则层面）
     */
    public static boolean isValidCodeLiteral(String literal) {
        return literal != null && FULL_CODE_PATTERN.matcher(literal).matches();
    }
}
