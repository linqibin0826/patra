package dev.patra.interaction.domain.collection.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 收藏夹不存在、不是自己的，或对这位访客不可见（404，不区分原因）。
public final class CollectionNotFoundException extends DomainException {

  /// 创建异常。
  public CollectionNotFoundException() {
    super("没有找到这个收藏夹", StandardErrorTrait.NOT_FOUND);
  }
}
