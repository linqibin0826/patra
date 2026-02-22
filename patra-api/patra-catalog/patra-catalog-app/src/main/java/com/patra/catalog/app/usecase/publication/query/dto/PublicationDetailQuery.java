package com.patra.catalog.app.usecase.publication.query.dto;

import cn.hutool.core.lang.Assert;

/// 文献详情查询参数。
///
/// @author linqibin
/// @since 0.1.0
public record PublicationDetailQuery(Long id) {

  public PublicationDetailQuery {
    Assert.notNull(id, "出版物 ID 不能为空");
  }

  /// 创建文献详情查询参数。
  ///
  /// @param id 文献 ID
  /// @return 查询参数实例
  public static PublicationDetailQuery of(Long id) {
    return new PublicationDetailQuery(id);
  }
}
