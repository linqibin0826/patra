package com.patra.ingest.smoke;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.patra.expr.Exprs;
import com.patra.ingest.domain.model.vo.ExprCompilationRequest;
import com.patra.ingest.domain.model.vo.ExprCompilationResult;
import com.patra.ingest.domain.port.ExpressionCompilerPort;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      "spring.cloud.nacos.config.enabled=false",
      "spring.cloud.nacos.discovery.enabled=false",
      "spring.cloud.discovery.client.simple.instances.patra-registry[0].uri=http://localhost:6000",
      // Allow duplicate FeignClientSpecification beans registered by scanning
      "spring.main.allow-bean-definition-overriding=true",
      // Disable XXL-Job to avoid missing property failures in test profile
      "xxl.job.enabled=false",
      // Force STRICT mode via configuration for this test context
      "expr.strict=true"
    })
@ActiveProfiles("dev")
class ExprStrictSmokeTest {

  @Autowired private ExpressionCompilerPort compiler;

  private static final Path SAMPLES_DIR =
      Path.of("../../docs/expr/smoke/samples").toAbsolutePath().normalize();

  @Test
  @DisplayName("STRICT: NOT on PubMed date RANGE must error")
  void strict_mode_not_on_range_errors() throws Exception {
    assumeTrue(runSmoke(), "Set RUN_SMOKE=1 to enable smoke tests");

    String exprJson =
        Exprs.toJson(
            Exprs.not(
                Exprs.rangeDate(
                    "entrez_date",
                    java.time.LocalDate.parse("2024-01-01"),
                    java.time.LocalDate.parse("2024-12-31"))));
    ExprCompilationResult strict = compiler.compile(new ExprCompilationRequest("PUBMED", exprJson));
    assertFalse(strict.isValid(), "STRICT mode should produce an error for NOT on RANGE");
    assertNotNull(strict.errors());
    assertTrue(
        strict.errors().contains("E-NOT-UNSUPPORTED")
            || strict.errors().contains("E-NOT-OP-UNSUPPORTED"),
        () -> strict.errors());
  }

  private static boolean runSmoke() {
    String v = System.getenv("RUN_SMOKE");
    return v != null && ("1".equals(v) || "true".equalsIgnoreCase(v));
  }
}
