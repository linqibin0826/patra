package com.patra.starter.provenance.common.gateway;

import java.util.Map;

/// API 请求参数接口
/// 
/// 所有请求对象必须实现此接口,以便转换为查询参数映射。用于统一不同数据源API的参数构建逻辑。
/// 
/// **实现要求:**
/// 
/// - 将请求对象字段转换为 `Map<String, String>` 形式
///   - 仅包含非 null 的值
///   - 键名应与上游API的查询参数名一致
///   - 支持GET请求查询字符串和POST表单参数
/// 
/// @author linqibin
/// @since 0.1.0
public interface ApiRequest {

  /// 将请求转换为查询参数映射
/// 
/// 仅包含非 null 的值。
/// 
/// @return 查询参数映射
  Map<String, String> toQueryParams();
}
