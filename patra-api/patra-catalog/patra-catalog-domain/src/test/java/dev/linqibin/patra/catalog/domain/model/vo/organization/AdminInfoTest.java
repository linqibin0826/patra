package dev.linqibin.patra.catalog.domain.model.vo.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// AdminInfo 值对象单元测试。
///
/// 基于 ROR Schema v2.0 的 admin 字段定义。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("AdminInfo 值对象")
class AdminInfoTest {

  @Nested
  @DisplayName("创建测试")
  class CreationTest {

    @Test
    @DisplayName("应正确创建完整的管理元数据")
    void shouldCreateFullAdminInfo() {
      LocalDate created = LocalDate.of(2019, 1, 20);
      LocalDate modified = LocalDate.of(2024, 12, 11);

      AdminInfo info = AdminInfo.of(created, "1.0", modified, "2.1");

      assertThat(info.createdDate()).isEqualTo(created);
      assertThat(info.createdSchemaVersion()).isEqualTo("1.0");
      assertThat(info.lastModifiedDate()).isEqualTo(modified);
      assertThat(info.lastModifiedSchemaVersion()).isEqualTo("2.1");
    }

    @Test
    @DisplayName("应正确创建空的管理元数据")
    void shouldCreateEmptyAdminInfo() {
      AdminInfo info = AdminInfo.empty();

      assertThat(info.createdDate()).isNull();
      assertThat(info.createdSchemaVersion()).isNull();
      assertThat(info.lastModifiedDate()).isNull();
      assertThat(info.lastModifiedSchemaVersion()).isNull();
    }

    @Test
    @DisplayName("应正确创建部分的管理元数据")
    void shouldCreatePartialAdminInfo() {
      LocalDate modified = LocalDate.of(2024, 12, 11);

      AdminInfo info = AdminInfo.of(null, null, modified, "2.1");

      assertThat(info.createdDate()).isNull();
      assertThat(info.createdSchemaVersion()).isNull();
      assertThat(info.lastModifiedDate()).isEqualTo(modified);
      assertThat(info.lastModifiedSchemaVersion()).isEqualTo("2.1");
    }
  }

  @Nested
  @DisplayName("便捷判断方法测试")
  class ConvenienceMethodsTest {

    @Test
    @DisplayName("hasCreatedInfo() 应正确判断是否有创建信息")
    void shouldCheckHasCreatedInfo() {
      AdminInfo withCreated = AdminInfo.of(LocalDate.of(2019, 1, 20), "1.0", null, null);
      AdminInfo withoutCreated = AdminInfo.of(null, null, LocalDate.now(), "2.1");
      AdminInfo empty = AdminInfo.empty();

      assertThat(withCreated.hasCreatedInfo()).isTrue();
      assertThat(withoutCreated.hasCreatedInfo()).isFalse();
      assertThat(empty.hasCreatedInfo()).isFalse();
    }

    @Test
    @DisplayName("hasLastModifiedInfo() 应正确判断是否有修改信息")
    void shouldCheckHasLastModifiedInfo() {
      AdminInfo withModified = AdminInfo.of(null, null, LocalDate.of(2024, 12, 11), "2.1");
      AdminInfo withoutModified = AdminInfo.of(LocalDate.now(), "1.0", null, null);
      AdminInfo empty = AdminInfo.empty();

      assertThat(withModified.hasLastModifiedInfo()).isTrue();
      assertThat(withoutModified.hasLastModifiedInfo()).isFalse();
      assertThat(empty.hasLastModifiedInfo()).isFalse();
    }

    @Test
    @DisplayName("isEmpty() 应正确判断是否为空")
    void shouldCheckIsEmpty() {
      AdminInfo empty = AdminInfo.empty();
      AdminInfo notEmpty = AdminInfo.of(LocalDate.now(), "1.0", null, null);

      assertThat(empty.isEmpty()).isTrue();
      assertThat(notEmpty.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("isSchemaV2() 应正确判断是否为 Schema 2.x")
    void shouldCheckIsSchemaV2() {
      AdminInfo v2 = AdminInfo.of(null, null, LocalDate.now(), "2.1");
      AdminInfo v1 = AdminInfo.of(null, null, LocalDate.now(), "1.0");
      AdminInfo v20 = AdminInfo.of(null, null, LocalDate.now(), "2.0");

      assertThat(v2.isSchemaV2()).isTrue();
      assertThat(v20.isSchemaV2()).isTrue();
      assertThat(v1.isSchemaV2()).isFalse();
    }
  }

  @Nested
  @DisplayName("相等性测试")
  class EqualityTest {

    @Test
    @DisplayName("相同内容的 AdminInfo 应相等")
    void shouldBeEqualWhenContentSame() {
      LocalDate date = LocalDate.of(2024, 12, 11);

      AdminInfo info1 = AdminInfo.of(date, "1.0", date, "2.1");
      AdminInfo info2 = AdminInfo.of(date, "1.0", date, "2.1");

      assertThat(info1).isEqualTo(info2);
      assertThat(info1.hashCode()).isEqualTo(info2.hashCode());
    }

    @Test
    @DisplayName("空 AdminInfo 应相等")
    void shouldEmptyBeEqual() {
      AdminInfo empty1 = AdminInfo.empty();
      AdminInfo empty2 = AdminInfo.empty();

      assertThat(empty1).isEqualTo(empty2);
    }

    @Test
    @DisplayName("不同内容的 AdminInfo 应不相等")
    void shouldNotBeEqualWhenContentDifferent() {
      AdminInfo info1 = AdminInfo.of(LocalDate.now(), "1.0", null, null);
      AdminInfo info2 = AdminInfo.of(LocalDate.now(), "2.0", null, null);

      assertThat(info1).isNotEqualTo(info2);
    }
  }
}
