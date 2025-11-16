package com.patra.common.provenance.api.endpoints;

/**
 * Crossref API 端点路径常量
 *
 * <p>Crossref 提供 RESTful API 访问学术文献元数据。
 *
 * <h3>端点说明</h3>
 *
 * <ul>
 *   <li><b>WORKS</b> - Works 端点，用于查询文献作品
 * </ul>
 *
 * <h3>基础 URL</h3>
 *
 * <pre>
 * https://api.crossref.org
 * </pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class CrossrefEndpoints {
  private CrossrefEndpoints() {
    throw new AssertionError("工具类不应被实例化");
  }

  /**
   * Works - 作品查询端点
   *
   * <p>用于：
   *
   * <ul>
   *   <li>查询文献元数据
   *   <li>支持高级过滤器
   *   <li>支持游标分页
   *   <li>支持字段选择
   * </ul>
   */
  public static final String WORKS = "/works";
}
