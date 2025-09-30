package com.patra.common.error;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HasErrorTraitsTest {

    private static final class TraitException extends RuntimeException implements HasErrorTraits {

        private TraitException(String message) {
            super(message);
        }

        @Override
        public Set<ErrorTrait> getErrorTraits() {
            return Set.of(ErrorTrait.NOT_FOUND, ErrorTrait.TIMEOUT);
        }
    }

    @Test
    void getErrorTraits_shouldExposeSemanticClassifications() {
        HasErrorTraits ex = new TraitException("资源不存在");
        assertThat(ex.getErrorTraits()).containsExactlyInAnyOrder(ErrorTrait.NOT_FOUND, ErrorTrait.TIMEOUT);
    }

    @Test
    void getErrorTraits_shouldBeImmutableSet() {
        HasErrorTraits ex = new TraitException("资源不存在");
        assertThatThrownBy(() -> ex.getErrorTraits().add(ErrorTrait.CONFLICT))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
