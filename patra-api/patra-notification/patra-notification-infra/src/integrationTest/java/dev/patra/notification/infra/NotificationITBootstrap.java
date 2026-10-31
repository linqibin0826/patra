package dev.patra.notification.infra;

import static org.mockito.Mockito.mock;

import dev.patra.identity.api.account.endpoint.UserEndpoint;
import dev.patra.interaction.api.post.endpoint.PostEndpoint;
import dev.patra.interaction.api.subject.endpoint.SubjectEndpoint;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/// infra 集成测试的启动类，只在测试里存在。
///
/// identity、interaction 的 HTTP 代理在 boot 里装配，infra 的上下文用不会被调用的 mock 顶上（同 interaction）。
@SpringBootApplication
class NotificationITBootstrap {

  /// 不会被调用的 identity 代理。
  ///
  /// @return mock
  @Bean
  UserEndpoint userEndpoint() {
    return mock(UserEndpoint.class);
  }

  /// 不会被调用的 interaction 对象视图代理。
  ///
  /// @return mock
  @Bean
  SubjectEndpoint subjectEndpoint() {
    return mock(SubjectEndpoint.class);
  }

  /// 不会被调用的 interaction 经验结果代理。
  ///
  /// @return mock
  @Bean
  PostEndpoint postEndpoint() {
    return mock(PostEndpoint.class);
  }
}
