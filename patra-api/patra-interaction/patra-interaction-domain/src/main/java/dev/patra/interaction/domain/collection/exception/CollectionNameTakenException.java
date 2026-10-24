package dev.patra.interaction.domain.collection.exception;

import dev.patra.interaction.domain.collection.model.vo.CollectionName;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.error.field.HasFieldViolations;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.List;

/// 同一用户下已有同名收藏夹（409 `COLLECTION_NAME_TAKEN`，`errors[]` 带 `name` / `TAKEN`）。
public final class CollectionNameTakenException extends DomainException
    implements HasFieldViolations {

  private static final String MESSAGE = "已有同名收藏夹";

  /// 创建异常。
  public CollectionNameTakenException() {
    super(MESSAGE, StandardErrorTrait.CONFLICT, CollectionErrorTrait.COLLECTION_NAME_TAKEN);
  }

  /// 名称字段上的错误。
  ///
  /// @return 一条字段错误
  @Override
  public List<FieldViolation> getFieldViolations() {
    return List.of(FieldViolation.of(CollectionName.FIELD, "TAKEN", MESSAGE));
  }
}
