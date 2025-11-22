package com.patra.common.provenance.api.endpoints;

/// Crossref API 端点路径常量
/// 
/// Crossref 提供 RESTful API 访问学术出版物元数据。
/// 
/// ### 端点说明
/// 
/// - **WORKS** - Works 端点，用于查询出版物作品
/// 
/// ### 基础 URL
/// 
/// ```
/// 
/// https://api.crossref.org
/// 
/// ```
/// 
/// @author linqibin
/// @since 0.1.0
public final class CrossrefEndpoints {
  private CrossrefEndpoints() {
    throw new AssertionError("工具类不应被实例化");
  }

  /// Works - 作品查询端点
/// 
/// 用于：
/// 
/// - 查询出版物元数据
///   - 支持高级过滤器
///   - 支持游标分页
///   - 支持字段选择
/// 
  public static final String WORKS = "/works";
}
