package com.patra.common.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadOnlyAggregateTest {

    static class R extends ReadOnlyAggregate<String> {
        R(String id) { super(id); }
        R() { super(); }
    }

    @Test
    void equals_hashcode_transient_and_toString() {
        R a = new R("ID");
        R b = new R("ID");
        R c = new R("ID2");

        assertThat(a).isEqualTo(b).isNotEqualTo(c);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(new R().isTransient()).isTrue();
        assertThat(a.isTransient()).isFalse();
        assertThat(a.toString()).contains("R").contains("ID");
    }
}

