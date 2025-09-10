package com.patra.error.core;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 平台错误码值对象：{MODULE}-{CATEGORY}{NNNN}
 * - MODULE: 2~4 位大写字母（如 REG/VAU/QRY/COM）
 * - CATEGORY: 单字符（C/B/S/N/U）
 * - NNNN: 0001~9999 四位数字
 * <p>
 * 设计说明：
 * - 使用 Java 21 record，不可变、值相等语义。
 * - 在压缩构造器中进行强校验，保证一旦构造即合法。
 */
public record ErrorCode(String module, Category category, int number) {

    /**
     * 完整字符串编码正则（快速初筛）
     */
    public static final Pattern FULL_CODE_PATTERN =
            Pattern.compile("^[A-Z]{2,4}-[CBSNU][0-9]{4}$");

    /**
     * number 的边界
     */
    public static final int MIN_NUMBER = 1;
    public static final int MAX_NUMBER = 9999;

    /**
     * 主构造器进行强校验与标准化
     */
    public ErrorCode {
        Objects.requireNonNull(module, "module must not be null");
        Objects.requireNonNull(category, "category must not be null");
        if (module.isBlank()) {
            throw new IllegalArgumentException("module must not be blank");
        }
        if (!module.chars().allMatch(ch -> ch >= 'A' && ch <= 'Z')) {
            throw new IllegalArgumentException("module must be uppercase A-Z");
        }
        if (module.length() < 2 || module.length() > 4) {
            throw new IllegalArgumentException("module length must be 2~4");
        }
        if (number < MIN_NUMBER || number > MAX_NUMBER) {
            throw new IllegalArgumentException("number must be in [0001, 9999]");
        }
    }

    /**
     * 从完整字符串编码解析（如 "REG-C0101"），非法则抛出 IllegalArgumentException
     */
    public static ErrorCode of(String code) {
        Objects.requireNonNull(code, "code must not be null");
        if (!FULL_CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("Invalid error code format: " + code);
        }
        // 形如 ABC-XNNNN
        String module = code.substring(0, code.indexOf('-'));
        char ch = code.charAt(code.indexOf('-') + 1);
        Category category = Category.fromSymbol(ch);
        int number = Integer.parseInt(code.substring(code.length() - 4));
        return new ErrorCode(module, category, number);
    }

    /**
     * 通过三段构造，内部会进行合法性校验
     */
    public static ErrorCode of(String module, Category category, int number) {
        return new ErrorCode(module, category, number);
    }

    /**
     * 返回平台统一字符串形态：{MODULE}-{CATEGORY}{NNNN}
     */
    @Override
    public String toString() {
        return "%s-%c%04d".formatted(module, category.symbol(), number);
    }

    /**
     * 便捷：是否属于某个类别
     */
    public boolean is(Category cat) {
        return this.category == cat;
    }
}
