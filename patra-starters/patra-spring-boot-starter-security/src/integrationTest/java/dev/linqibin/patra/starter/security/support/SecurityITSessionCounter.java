package dev.linqibin.patra.starter.security.support;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/// 统计应用里创建过多少个 HttpSession。
///
/// Spring Boot 会把实现了 servlet 监听器接口的 Bean 自动注册到内嵌容器。
@Component
public class SecurityITSessionCounter implements HttpSessionListener {

  private final AtomicInteger created = new AtomicInteger();

  /// 每创建一个会话计数加一。
  ///
  /// @param event 会话创建事件
  @Override
  public void sessionCreated(HttpSessionEvent event) {
    created.incrementAndGet();
  }

  /// 返回至今创建过的会话数。
  ///
  /// @return 会话数
  public int createdCount() {
    return created.get();
  }
}
