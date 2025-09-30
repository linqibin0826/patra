package com.patra.starter.web.resp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @Test
    void of_shouldCalculatePagesAndPreserveRecords() {
        PageResult<String> result = PageResult.of(23, 2, 10, List.of("A", "B"));
        assertThat(result.getTotal()).isEqualTo(23);
        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getPages()).isEqualTo(3);
        assertThat(result.getRecords()).containsExactly("A", "B");
    }

    @Test
    void of_shouldFallbackToEmptyRecordsAndZeroPagesWhenSizeInvalid() {
        PageResult<String> result = PageResult.of(5, 1, 0, null);
        assertThat(result.getPages()).isZero();
        assertThat(result.getRecords()).isEmpty();
    }
}
