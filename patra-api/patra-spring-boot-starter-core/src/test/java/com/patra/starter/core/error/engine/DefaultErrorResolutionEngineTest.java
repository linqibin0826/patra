package com.patra.starter.core.error.engine;

import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultErrorResolutionEngineTest {

    private ErrorProperties props;

    @BeforeEach
    void setUp() {
        props = new ErrorProperties();
        props.setContextPrefix("ING");
        props.getEngine().setMaxCauseDepth(2);
        props.getEngine().setEnableNamingHeuristic(true);
        props.getEngine().setEnableTraitMapping(true);
    }

    @Test
    void null_exception_and_application_exception_and_mapping_contributor() {
        ErrorMappingContributor contributor = ex ->
                ex instanceof IllegalArgumentException ? Optional.of(code("0400")) : Optional.empty();
        DefaultErrorResolutionEngine engine = new DefaultErrorResolutionEngine(props, List.of(contributor));

        // null → 兜底 500
        assertThat(engine.resolve(null).httpStatus()).isEqualTo(500);

        // ApplicationException 直通
        ApplicationException app = new ApplicationException(code("0409"), "conflict");
        ErrorResolution er = engine.resolve(app);
        assertThat(er.errorCode().code()).isEqualTo("ING-0409");
        assertThat(er.httpStatus()).isEqualTo(409);

        // 映射器命中
        assertThat(engine.resolve(new IllegalArgumentException("bad"))).extracting(ErrorResolution::httpStatus).isEqualTo(400);
    }

    @Test
    void trait_mapping_naming_heuristic_cause_chain_and_fallbacks() {
        DefaultErrorResolutionEngine engine = new DefaultErrorResolutionEngine(props, List.of());

        // Trait 映射
        class NotFoundEx extends RuntimeException implements HasErrorTraits {
            @Override public Set<ErrorTrait> getErrorTraits() { return Set.of(ErrorTrait.NOT_FOUND); }
        }
        assertThat(engine.resolve(new NotFoundEx()).httpStatus()).isEqualTo(404);

        // 命名启发式
        class UserAlreadyExists extends RuntimeException {}
        assertThat(engine.resolve(new UserAlreadyExists()).httpStatus()).isEqualTo(409);

        // 由 cause 解析
        RuntimeException cause = new RuntimeException("Validation");
        RuntimeException outer = new RuntimeException("wrapper", cause);
        assertThat(engine.resolve(outer).httpStatus()).isEqualTo(422);

        // 超过最大深度 → 500
        props.getEngine().setMaxCauseDepth(0);
        DefaultErrorResolutionEngine engine2 = new DefaultErrorResolutionEngine(props, List.of());
        assertThat(engine2.resolve(new RuntimeException(new RuntimeException())).httpStatus()).isEqualTo(500);
    }

    private static ErrorCodeLike code(String suffix) {
        String full = "ING-" + suffix;
        int http = Integer.parseInt(suffix);
        return new ErrorCodeLike() {
            @Override public String code() { return full; }
            @Override public int httpStatus() { return http; }
            @Override public String toString() { return full; }
        };
    }
}

