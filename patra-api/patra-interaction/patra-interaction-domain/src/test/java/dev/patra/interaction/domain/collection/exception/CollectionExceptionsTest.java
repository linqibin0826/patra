package dev.patra.interaction.domain.collection.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import org.junit.jupiter.api.Test;

/// 每个领域异常带一个标准特征（定 HTTP 状态）和至多一个原因码（门户选文案）。
class CollectionExceptionsTest {

  /// 冲突类异常带各自的原因码。
  @Test
  void should_carry_reason_codes() {
    assertThat(new LibraryFullException().getErrorTraits())
        .containsExactlyInAnyOrder(StandardErrorTrait.CONFLICT, CollectionErrorTrait.LIBRARY_FULL);
    assertThat(new CollectionLimitReachedException().getErrorTraits())
        .containsExactlyInAnyOrder(
            StandardErrorTrait.CONFLICT, CollectionErrorTrait.COLLECTION_LIMIT_REACHED);
    assertThat(PublishNotAllowedException.emailUnverified().getErrorTraits())
        .containsExactlyInAnyOrder(
            StandardErrorTrait.CONFLICT, CollectionErrorTrait.EMAIL_UNVERIFIED);
    assertThat(PublishNotAllowedException.accountRestricted().getErrorTraits())
        .containsExactlyInAnyOrder(
            StandardErrorTrait.CONFLICT, CollectionErrorTrait.ACCOUNT_RESTRICTED);
    assertThat(new ExportTooLargeException(5001).getErrorTraits())
        .containsExactlyInAnyOrder(
            StandardErrorTrait.RULE_VIOLATION, CollectionErrorTrait.EXPORT_TOO_LARGE);
  }

  /// 重名既是原因码，也是名称字段上的错误。
  @Test
  void should_mark_taken_name_on_field() {
    CollectionNameTakenException ex = new CollectionNameTakenException();
    assertThat(ex.getErrorTraits())
        .containsExactlyInAnyOrder(
            StandardErrorTrait.CONFLICT, CollectionErrorTrait.COLLECTION_NAME_TAKEN);
    assertThat(ex.getFieldViolations())
        .extracting(FieldViolation::field, FieldViolation::code)
        .containsExactly(tuple("name", "TAKEN"));
  }

  /// 404、422、503 只带标准特征。
  @Test
  void should_use_standard_traits_for_plain_errors() {
    assertThat(new CollectionNotFoundException().getErrorTraits())
        .containsExactly(StandardErrorTrait.NOT_FOUND);
    assertThat(new LibraryItemNotFoundException().getErrorTraits())
        .containsExactly(StandardErrorTrait.NOT_FOUND);
    assertThat(new InvalidCollectionRequestException("x").getErrorTraits())
        .containsExactly(StandardErrorTrait.RULE_VIOLATION);
    assertThat(new UserDirectoryUnavailableException(new RuntimeException()).getErrorTraits())
        .containsExactly(StandardErrorTrait.DEP_UNAVAILABLE);
  }
}
