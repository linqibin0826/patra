package dev.patra.interaction.domain.collection.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 已有 200 个收藏夹（409 `COLLECTION_LIMIT_REACHED`）。
public final class CollectionLimitReachedException extends DomainException {

  /// 创建异常。
  public CollectionLimitReachedException() {
    super(
        "最多 200 个收藏夹", StandardErrorTrait.CONFLICT, CollectionErrorTrait.COLLECTION_LIMIT_REACHED);
  }
}
