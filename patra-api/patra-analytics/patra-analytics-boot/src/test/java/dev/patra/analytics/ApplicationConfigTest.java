package dev.patra.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ApplicationConfigTest {

  private static PropertySource<?> yaml() throws IOException {
    return new YamlPropertySourceLoader()
        .load("application.yml", new ClassPathResource("application.yml"))
        .getFirst();
  }

  @Test
  void ip_geolocation_directory_defaults_to_user_home() throws IOException {
    assertThat(yaml().getProperty("patra.analytics.ip-geolocation.directory"))
        .hasToString("${user.home}/.patra/data/ip2region");
  }

  @Test
  void redis_calls_time_out_fast() throws IOException {
    assertThat(yaml().getProperty("spring.data.redis.timeout")).hasToString("500ms");
    assertThat(yaml().getProperty("spring.data.redis.connect-timeout")).hasToString("500ms");
  }
}
