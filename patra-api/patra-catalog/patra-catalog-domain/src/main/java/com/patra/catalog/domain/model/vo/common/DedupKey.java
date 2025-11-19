package com.patra.catalog.domain.model.vo.common;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;

/**
 * 作者去重键值对象。封装预计算的 MD5 哈希值,用于作者去重。
 *
 * <p><b>去重策略</b>(优先级从高到低)：
 *
 * <ol>
 *   <li><b>ORCID</b>：准确率 99%+,覆盖率 30%
 *   <li><b>姓名+邮箱</b>：准确率 95%,覆盖率 50%
 *   <li><b>姓名+机构+国家</b>：准确率 85%,覆盖率 80%
 *   <li><b>姓名+缩写</b>：准确率 70%,覆盖率 100%
 * </ol>
 *
 * <p><b>设计原则</b>：
 *
 * <ul>
 *   <li>不可变性：Record 自动提供
 *   <li>值验证：验证 MD5 哈希格式(32位十六进制)
 *   <li>可追溯：保留原始输入用于调试
 *   <li><b>纯领域对象</b>：不依赖任何框架,MD5 计算在应用层完成
 * </ul>
 *
 * <p><b>使用示例</b>：
 *
 * <pre>{@code
 * // 应用层计算 MD5 哈希后创建去重键
 * String hash = DigestUtil.md5Hex("smithj|john.smith@example.com");
 * DedupKey key = DedupKey.of(hash, "name=Smith,J.|email=john.smith@example.com");
 *
 * // 从数据库读取已存储的去重键
 * DedupKey restored = DedupKey.fromHash("a1b2c3d4e5f6...");
 *
 * // 比较去重键
 * if (key1.value().equals(key2.value())) {
 *     // 可能是同一作者
 * }
 * }</pre>
 *
 * <p><b>注意</b>：MD5 哈希计算应在应用层(AuthorDedupService)完成,领域层只负责存储和验证。
 *
 * @param value MD5 哈希值(32位十六进制字符串)
 * @param rawInput 原始输入(用于调试和审计)
 * @author linqibin
 * @since 0.1.0
 */
public record DedupKey(String value, String rawInput) implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * 紧凑构造器：验证去重键的有效性。
   *
   * @throws IllegalArgumentException 如果去重键为空或格式无效
   */
  public DedupKey {
    Assert.notBlank(value, "去重键不能为空");
    Assert.isTrue(
        value.matches("^[a-f0-9]{32}$"), "去重键格式无效,必须为32位MD5哈希：%s", value);
  }

  /**
   * 创建去重键(带原始输入追溯)。
   *
   * <p>用于应用层计算完 MD5 后创建去重键。
   *
   * @param hash MD5 哈希值(32位十六进制)
   * @param rawInput 原始输入(用于调试)
   * @return 去重键
   */
  public static DedupKey of(String hash, String rawInput) {
    return new DedupKey(hash, rawInput);
  }

  /**
   * 从哈希值创建去重键(无原始输入)。
   *
   * <p>用于从数据库读取已存储的去重键。
   *
   * @param hash MD5 哈希值(32位十六进制)
   * @return 去重键
   */
  public static DedupKey fromHash(String hash) {
    return new DedupKey(hash, "restored");
  }

  @Override
  public String toString() {
    return value;
  }
}

