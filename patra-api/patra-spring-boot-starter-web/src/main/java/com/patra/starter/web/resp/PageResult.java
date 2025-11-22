package com.patra.starter.web.resp;

import java.util.Collections;
import java.util.List;
import lombok.Data;

/// REST 接口共享的通用分页载荷。
/// 
/// @param <T> 页面中包含的元素类型
@Data
public class PageResult<T> {

  /// 所有页面中匹配的记录总数。
  private long total;

  /// 当前页面的索引（1-based，与 API 契约对齐）。
  private long current;

  /// 请求的页面大小。
  private long size;

  /// 从 `total` 和 `size` 派生的页面总数。
  private long pages;

  /// 当前页面中包含的记录；无数据时为空。
  private List<T> records = Collections.emptyList();

  /// 用提供的元数据和记录构建 {@link PageResult} 实例。
/// 
/// @param total 记录总数
/// @param current 当前页面索引（1-based）
/// @param size 请求的页面大小
/// @param records 页面记录（可为 null）
/// @param <T> 元素类型
/// @return 填充的 {@link PageResult}
  public static <T> PageResult<T> of(long total, long current, long size, List<T> records) {
    PageResult<T> r = new PageResult<>();
    r.total = total;
    r.current = current;
    r.size = size;
    r.pages = size <= 0 ? 0 : (total + size - 1) / size;
    r.records = (records == null ? Collections.emptyList() : records);
    return r;
  }
}
