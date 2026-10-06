package dev.linqibin.patra.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// identity 服务的启动入口：前台用户的账号、凭据，以及以后的会话。
///
/// 没有指定 profile 时默认用 `dev`。
@SpringBootApplication(scanBasePackages = "dev.linqibin")
public class PatraIdentityApplication {

  /// 启动应用。
  ///
  /// @param args 命令行参数
  public static void main(String[] args) {
    if (System.getProperty("spring.profiles.active") == null
        && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
      System.setProperty("spring.profiles.active", "dev");
    }
    SpringApplication.run(PatraIdentityApplication.class, args);
  }
}
