package dev.patra.interaction.domain.collection.model.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.collection.exception.InvalidCollectionRequestException;
import org.junit.jupiter.api.Test;

/// 视图、排序、可见性的查询参数解析。
class CollectionEnumsTest {

  /// 缺省值与小写参数名。
  @Test
  void should_parse_params_with_defaults() {
    assertThat(LibrarySort.fromParam(null)).isEqualTo(LibrarySort.ADDED);
    assertThat(LibrarySort.fromParam("journal")).isEqualTo(LibrarySort.JOURNAL);
    assertThat(LibrarySort.fromParam("published")).isEqualTo(LibrarySort.PUBLISHED);
    assertThat(LibraryView.fromParam(null)).isEqualTo(LibraryView.ALL);
    assertThat(LibraryView.fromParam("unfiled")).isEqualTo(LibraryView.UNFILED);
    assertThat(CollectionVisibility.fromParam("public")).isEqualTo(CollectionVisibility.PUBLIC);
    assertThat(CollectionVisibility.fromParam("PRIVATE")).isEqualTo(CollectionVisibility.PRIVATE);
  }

  /// 未知取值是请求错误。
  @Test
  void should_reject_unknown_params() {
    assertThatThrownBy(() -> LibrarySort.fromParam("title"))
        .isInstanceOf(InvalidCollectionRequestException.class);
    assertThatThrownBy(() -> LibraryView.fromParam("trash"))
        .isInstanceOf(InvalidCollectionRequestException.class);
    assertThatThrownBy(() -> CollectionVisibility.fromParam(null))
        .isInstanceOf(InvalidCollectionRequestException.class);
  }
}
