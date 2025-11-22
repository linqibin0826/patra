package com.patra.starter.web.req;

/// 由接受分页设置的请求 DTO 实现的分页契约。
///
/// @author linqibin
/// @since 0.1.0
public interface Pageable {

  /// 页面索引（1-based）。
  Integer getPageNo();

  /// 客户端请求的页面大小。
  Integer getPageSize();
}
