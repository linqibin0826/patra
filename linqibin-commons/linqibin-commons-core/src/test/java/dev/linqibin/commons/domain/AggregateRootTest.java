package dev.linqibin.commons.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// AggregateRoot 基类单元测试。
///
/// 验证子实体变更追踪功能。
class AggregateRootTest {

  // ========== 测试用具体聚合根实现 ==========

  /// 用于测试的具体聚合根实现
  static class TestAggregate extends AggregateRoot<Long> {

    TestAggregate() {
      super();
    }

    TestAggregate(Long id) {
      super(id);
    }

    /// 模拟业务方法：添加组成部分
    void addChild(TestChildEntity child) {
      trackChildAdded(TestChildEntity.class, child);
    }

    /// 模拟业务方法：更新组成部分
    void updateChild(TestChildEntity child) {
      trackChildUpdated(TestChildEntity.class, child);
    }

    /// 模拟业务方法：删除组成部分
    void removeChild(Long childId) {
      trackChildRemoved(TestChildEntity.class, childId);
    }
  }

  /// 用于测试的子实体
  record TestChildEntity(Long id, String value) {}

  // ========== 子实体变更追踪测试 ==========

  @Nested
  @DisplayName("子实体变更追踪")
  class ChildEntityChangeTests {

    @Test
    @DisplayName("trackChildAdded 应记录新增事件")
    void trackChildAdded_shouldRecordAddedEvent() {
      var aggregate = new TestAggregate(1L);
      var child = new TestChildEntity(100L, "测试值");

      aggregate.addChild(child);

      List<ChildEntityChange> changes = aggregate.peekChildChanges();
      assertThat(changes).hasSize(1);
      assertThat(changes.getFirst()).isInstanceOf(ChildEntityChange.Added.class);

      var added = (ChildEntityChange.Added<?>) changes.getFirst();
      assertThat(added.entityType()).isEqualTo(TestChildEntity.class);
      assertThat(added.entity()).isEqualTo(child);
    }

    @Test
    @DisplayName("trackChildUpdated 应记录更新事件")
    void trackChildUpdated_shouldRecordUpdatedEvent() {
      var aggregate = new TestAggregate(1L);
      var child = new TestChildEntity(100L, "更新后的值");

      aggregate.updateChild(child);

      List<ChildEntityChange> changes = aggregate.peekChildChanges();
      assertThat(changes).hasSize(1);
      assertThat(changes.getFirst()).isInstanceOf(ChildEntityChange.Updated.class);

      var updated = (ChildEntityChange.Updated<?>) changes.getFirst();
      assertThat(updated.entityType()).isEqualTo(TestChildEntity.class);
      assertThat(updated.entity()).isEqualTo(child);
    }

    @Test
    @DisplayName("trackChildRemoved 应记录删除事件")
    void trackChildRemoved_shouldRecordRemovedEvent() {
      var aggregate = new TestAggregate(1L);
      Long childId = 100L;

      aggregate.removeChild(childId);

      List<ChildEntityChange> changes = aggregate.peekChildChanges();
      assertThat(changes).hasSize(1);
      assertThat(changes.getFirst()).isInstanceOf(ChildEntityChange.Removed.class);

      var removed = (ChildEntityChange.Removed<?>) changes.getFirst();
      assertThat(removed.entityType()).isEqualTo(TestChildEntity.class);
      assertThat(removed.entityId()).isEqualTo(childId);
    }

    @Test
    @DisplayName("pullChildChanges 应返回事件并清空列表")
    void pullChildChanges_shouldReturnAndClear() {
      var aggregate = new TestAggregate(1L);
      aggregate.addChild(new TestChildEntity(1L, "v1"));
      aggregate.addChild(new TestChildEntity(2L, "v2"));

      List<ChildEntityChange> pulled = aggregate.pullChildChanges();

      assertThat(pulled).hasSize(2);
      assertThat(aggregate.peekChildChanges()).isEmpty();
      assertThat(aggregate.hasChildChanges()).isFalse();
    }

    @Test
    @DisplayName("peekChildChanges 应返回事件但不清空")
    void peekChildChanges_shouldReturnWithoutClearing() {
      var aggregate = new TestAggregate(1L);
      aggregate.addChild(new TestChildEntity(1L, "v1"));

      List<ChildEntityChange> peeked1 = aggregate.peekChildChanges();
      List<ChildEntityChange> peeked2 = aggregate.peekChildChanges();

      assertThat(peeked1).hasSize(1);
      assertThat(peeked2).hasSize(1);
      assertThat(aggregate.hasChildChanges()).isTrue();
    }

    @Test
    @DisplayName("hasChildChanges 有事件时返回 true")
    void hasChildChanges_withEvents_shouldReturnTrue() {
      var aggregate = new TestAggregate(1L);

      aggregate.addChild(new TestChildEntity(1L, "v1"));

      assertThat(aggregate.hasChildChanges()).isTrue();
    }

    @Test
    @DisplayName("hasChildChanges 无事件时返回 false")
    void hasChildChanges_noEvents_shouldReturnFalse() {
      var aggregate = new TestAggregate(1L);

      assertThat(aggregate.hasChildChanges()).isFalse();
    }

    @Test
    @DisplayName("pullChildChanges 无事件时返回空列表")
    void pullChildChanges_noEvents_shouldReturnEmptyList() {
      var aggregate = new TestAggregate(1L);

      List<ChildEntityChange> pulled = aggregate.pullChildChanges();

      assertThat(pulled).isEmpty();
    }

    @Test
    @DisplayName("多种变更类型可以混合追踪")
    void mixedChanges_shouldBeTrackedInOrder() {
      var aggregate = new TestAggregate(1L);
      var child1 = new TestChildEntity(1L, "新增");
      var child2 = new TestChildEntity(2L, "更新");

      aggregate.addChild(child1);
      aggregate.updateChild(child2);
      aggregate.removeChild(3L);

      List<ChildEntityChange> changes = aggregate.pullChildChanges();

      assertThat(changes).hasSize(3);
      assertThat(changes.get(0)).isInstanceOf(ChildEntityChange.Added.class);
      assertThat(changes.get(1)).isInstanceOf(ChildEntityChange.Updated.class);
      assertThat(changes.get(2)).isInstanceOf(ChildEntityChange.Removed.class);
    }
  }

  // ========== 使用模式匹配处理变更事件 ==========

  @Nested
  @DisplayName("模式匹配处理变更事件")
  class PatternMatchingTests {

    @Test
    @DisplayName("可以使用 switch 模式匹配处理变更事件")
    void patternMatching_shouldWorkWithSwitch() {
      var aggregate = new TestAggregate(1L);
      var child = new TestChildEntity(1L, "测试");
      aggregate.addChild(child);
      aggregate.updateChild(child);
      aggregate.removeChild(2L);

      StringBuilder result = new StringBuilder();

      for (ChildEntityChange change : aggregate.pullChildChanges()) {
        switch (change) {
          case ChildEntityChange.Added(var type, var entity) ->
              result.append("ADD:").append(type.getSimpleName()).append(";");
          case ChildEntityChange.Updated(var type, var entity) ->
              result.append("UPD:").append(type.getSimpleName()).append(";");
          case ChildEntityChange.Removed(var type, var id) ->
              result.append("DEL:").append(type.getSimpleName()).append(":").append(id).append(";");
        }
      }

      assertThat(result.toString())
          .isEqualTo("ADD:TestChildEntity;UPD:TestChildEntity;DEL:TestChildEntity:2;");
    }
  }
}
