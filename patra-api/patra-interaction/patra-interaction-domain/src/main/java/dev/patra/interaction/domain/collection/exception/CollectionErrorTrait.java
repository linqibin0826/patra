package dev.patra.interaction.domain.collection.exception;

import dev.patra.tool.core.error.trait.ErrorTrait;

/// collection 的原因码：随一个标准特征一起放进异常。错误引擎映射状态码时跳过它，响应的 `traits[]` 里带上它，
/// 门户把它当原因码读（同 identity 的 `IdentityErrorTrait`）。
public enum CollectionErrorTrait implements ErrorTrait {
  /// 文献库已满 20,000 条（409）。
  LIBRARY_FULL,

  /// 已有 200 个收藏夹（409）。
  COLLECTION_LIMIT_REACHED,

  /// 同一用户下已有同名收藏夹（409）。
  COLLECTION_NAME_TAKEN,

  /// 设为公开被拒：账号不是正常状态（封禁、注销冷静期、已注销）（409）。
  ACCOUNT_RESTRICTED,

  /// 设为公开被拒：邮箱未验证（409）。
  EMAIL_UNVERIFIED,

  /// 一次导出超过 5,000 条（422）。
  EXPORT_TOO_LARGE
}
