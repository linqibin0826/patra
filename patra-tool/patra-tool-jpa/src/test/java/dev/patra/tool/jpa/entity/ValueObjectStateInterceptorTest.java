package dev.patra.tool.jpa.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ValueObjectStateInterceptor：按值对象基类的新行标记告诉 Hibernate 新旧")
class ValueObjectStateInterceptorTest {

  private final ValueObjectStateInterceptor interceptor = new ValueObjectStateInterceptor();

  @Test
  @DisplayName("新建的值对象行是新行")
  void should_report_fresh_value_object_as_transient() {
    assertThat(interceptor.isTransient(new Row())).isTrue();
  }

  @Test
  @DisplayName("加载或插入过的值对象行不是新行")
  void should_report_loaded_value_object_as_not_transient() {
    Row row = new Row();
    row.markNotNew();

    assertThat(interceptor.isTransient(row)).isFalse();
  }

  @Test
  @DisplayName("其他实体交还 Hibernate 自己判断")
  void should_leave_other_entities_to_hibernate() {
    assertThat(interceptor.isTransient(new Object())).isNull();
  }

  private static final class Row extends ValueObjectJpaEntity {}
}
