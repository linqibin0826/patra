package com.patra.error.core;

/**
 * 错误类别（与 HTTP 语义建议映射对应）：
 * C=CLIENT, B=BUSINESS, S=SERVER, N=NETWORK, U=UNKNOWN
 */
public enum Category {
    CLIENT('C'),     // 请求方错误（参数、认证、权限、资源不存在等）
    BUSINESS('B'),   // 业务规则或状态冲突
    SERVER('S'),     // 本服务进程内错误（代码、资源、组件）
    NETWORK('N'),    // 网络/远端/网关/熔断/限流
    UNKNOWN('U');    // 未归类（临时使用）

    private final char symbol;

    Category(char symbol) {
        this.symbol = symbol;
    }

    /**
     * 类别在编码中的单字符表示（C/B/S/N/U）
     */
    public char symbol() {
        return symbol;
    }

    /**
     * 通过单字符解析类别，不合法则抛出 IllegalArgumentException
     */
    public static Category fromSymbol(char ch) {
        return switch (ch) {
            case 'C' -> CLIENT;
            case 'B' -> BUSINESS;
            case 'S' -> SERVER;
            case 'N' -> NETWORK;
            case 'U' -> UNKNOWN;
            default -> throw new IllegalArgumentException("Unknown category symbol: " + ch);
        };
    }
}
