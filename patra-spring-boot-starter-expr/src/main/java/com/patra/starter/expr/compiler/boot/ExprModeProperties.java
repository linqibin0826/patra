package com.patra.starter.expr.compiler.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 表达式编译器安全模式和行为开关的配置属性。
 *
 * <p>配置前缀：{@code expr}
 *
 * <p>关键配置项：
 *
 * <ul>
 *   <li>{@code strict} - 启用 STRICT 模式以实现快速失败行为（默认：false）
 *   <li>{@code multi.repeatEnabled} - 允许 MULTI 基准键发出重复参数（默认：false）
 * </ul>
 *
 * <p>STRICT 模式行为：
 *
 * <ul>
 *   <li>当为 {@code true}：缺少函数/变换或不支持 NOT → 编译错误
 *   <li>当为 {@code false}：缺少函数/变换或不支持 NOT → 警告并优雅降级
 * </ul>
 *
 * <p>MULTI 重复行为：
 *
 * <ul>
 *   <li>当为 {@code false}（默认）：MULTI 基准键必须使用拼接变换（LIST_JOIN, FILTER_JOIN）
 *   <li>当为 {@code true}：MULTI 基准键可发出重复的提供方参数（需要适配器序列化支持）
 * </ul>
 *
 * <p>参考：docs/expr/02-architecture.md §2.8, docs/expr/03-compiler-bridge-internals.md §3.4.2, §3.6,
 * §3.8
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "expr")
public class ExprModeProperties {

  private boolean strict = false;
  private final Multi multi = new Multi();

  /**
   * 启用 STRICT 模式以实现确定性的快速失败行为。
   *
   * <p>建议：开发/预发环境使用 {@code false}，生产环境使用 {@code true}。
   *
   * @return 如果启用了 STRICT 模式则返回 true
   */
  public boolean isStrict() {
    return strict;
  }

  public void setStrict(boolean strict) {
    this.strict = strict;
  }

  public Multi getMulti() {
    return multi;
  }

  /**
   * MULTI 基准键行为配置。
   *
   * <p>控制 MULTI 基准键是否可以发出重复的提供方参数，或必须使用拼接变换。
   */
  public static class Multi {
    private boolean repeatEnabled = false;

    /**
     * 为 MULTI 基准键启用重复参数发出。
     *
     * <p>在适配器的重复参数序列化正式文档化之前，保持禁用状态（false）。
     *
     * @return 如果启用了重复策略则返回 true
     */
    public boolean isRepeatEnabled() {
      return repeatEnabled;
    }

    public void setRepeatEnabled(boolean repeatEnabled) {
      this.repeatEnabled = repeatEnabled;
    }
  }
}
