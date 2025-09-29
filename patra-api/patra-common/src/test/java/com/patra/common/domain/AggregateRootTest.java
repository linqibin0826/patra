package com.patra.common.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AggregateRootTest {

    static class TestEvent implements DomainEvent {
        private final Instant at;
        TestEvent(Instant at) { this.at = at; }
        @Override public Instant occurredAt() { return at; }
    }

    static class TestAgg extends AggregateRoot<String> {
        void addEvt(Instant t) { addDomainEvent(new TestEvent(nowIfNull(t))); }
    }

    @Test
    void id_and_version_and_transient_flags() {
        TestAgg agg = new TestAgg();
        assertThat(agg.isTransient()).isTrue();
        assertThatThrownBy(() -> agg.assignId(null)).isInstanceOf(NullPointerException.class);
        agg.assignId("A1");
        assertThat(agg.getId()).isEqualTo("A1");
        assertThat(agg.isTransient()).isFalse();

        assertThatThrownBy(() -> agg.assignVersion(-1)).isInstanceOf(IllegalArgumentException.class);
        agg.assignVersion(2);
        assertThat(agg.getVersion()).isEqualTo(2);
    }

    @Test
    void domain_events_pull_and_peek() {
        TestAgg agg = new TestAgg();
        // 添加两条事件，其中一条补当前时间
        agg.addEvt(null);
        agg.addEvt(Instant.EPOCH);

        assertThat(agg.peekDomainEvents()).hasSize(2);
        List<DomainEvent> pulled = agg.pullDomainEvents();
        assertThat(pulled).hasSize(2);
        // 再拉取应为空
        assertThat(agg.pullDomainEvents()).isEmpty();
        // peek 为不可修改视图
        assertThat(agg.peekDomainEvents()).isEmpty();
    }
}

