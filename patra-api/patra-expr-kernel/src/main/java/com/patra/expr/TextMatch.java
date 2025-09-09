package com.patra.expr;

/**
 * 文本匹配策略（对外公开）。
 * <ul>
 *   <li>PHRASE：短语匹配（按原样整体匹配，通常需要加引号）。</li>
 *   <li>EXACT：精确等值（字段语义上等于）。</li>
 *   <li>ANY：任意词匹配（通常是分词后 OR）。</li>
 * </ul>
 *
 * 线程安全性：枚举常量不可变，线程安全。
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum TextMatch {
    PHRASE, EXACT, ANY
}
