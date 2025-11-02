package com.patra.starter.web.req;

import java.util.Set;

/**
 * 排序契约。实现声明用于验证和文档化的白名单。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface Sortable {

  /** 格式为 {@code field,asc|desc;field2,desc} 的排序表达式。 */
  String getSort();

  /** 暴露给客户端的允许排序字段（小驼峰格式，默认为 {@code id}、{@code createdAt}、{@code updatedAt}）。 */
  default Set<String> allowedSortFields() {
    return Set.of("id", "createdAt", "updatedAt");
  }

  /** 排序字段的最大数量（需要更严格限制时覆盖）。 */
  default int maxSortFields() {
    return 3;
  }
}
