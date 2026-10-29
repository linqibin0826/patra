package dev.patra.interaction.infra;

import static org.mockito.Mockito.mock;

import dev.patra.catalog.api.publication.endpoint.PublicationEndpoint;
import dev.patra.catalog.api.venue.endpoint.VenueEndpoint;
import dev.patra.identity.api.endpoint.UserEndpoint;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/// infra 集成测试的启动类，只在测试里存在。
///
/// catalog、identity 的 HTTP 代理在 boot 里装配，infra 的上下文用一个不会被调用的 mock 顶上；要真实 HTTP 的测试
/// （`PublicationSubjectSourceAdapterIT`、`JournalSubjectSourceAdapterIT`）自己手工建客户端。
@SpringBootApplication
class InteractionITBootstrap {

  /// 不会被调用的 catalog 代理。
  ///
  /// @return mock
  @Bean
  PublicationEndpoint publicationEndpoint() {
    return mock(PublicationEndpoint.class);
  }

  /// 不会被调用的 catalog 期刊代理。
  ///
  /// @return mock
  @Bean
  VenueEndpoint venueEndpoint() {
    return mock(VenueEndpoint.class);
  }

  /// 不会被调用的 identity 代理。
  ///
  /// @return mock
  @Bean
  UserEndpoint userEndpoint() {
    return mock(UserEndpoint.class);
  }
}
