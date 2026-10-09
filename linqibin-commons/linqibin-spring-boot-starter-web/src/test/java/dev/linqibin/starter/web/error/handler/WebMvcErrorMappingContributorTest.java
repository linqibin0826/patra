package dev.linqibin.starter.web.error.handler;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.commons.error.codes.HttpStdErrors;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class WebMvcErrorMappingContributorTest {

  private final WebMvcErrorMappingContributor contributor =
      new WebMvcErrorMappingContributor(HttpStdErrors.of("T"));

  @Test
  void should_map_no_resource_found_to_404() {
    NoResourceFoundException noResource =
        new NoResourceFoundException(HttpMethod.GET, "/nowhere", "No static resource /nowhere.");

    assertThat(contributor.mapException(noResource).orElseThrow().code()).isEqualTo("T-0404");
  }

  @Test
  void should_map_no_handler_found_to_404() {
    NoHandlerFoundException noHandler =
        new NoHandlerFoundException("GET", "/nowhere", new HttpHeaders());

    assertThat(contributor.mapException(noHandler).orElseThrow().code()).isEqualTo("T-0404");
  }

  @Test
  void should_ignore_other_exceptions() {
    assertThat(contributor.mapException(new IllegalStateException("boom"))).isEmpty();
  }
}
