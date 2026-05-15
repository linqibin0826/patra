package dev.linqibin.patra.objectstorage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// 存储元数据服务的Spring Boot应用入口。
///
/// Patra对象存储元数据管理微服务的启动类,负责记录和管理上传到 外部对象存储提供商的文件元数据。
///
/// 默认配置文件设置:如果未指定profile,自动使用'dev'配置。
@SpringBootApplication(scanBasePackages = "dev.linqibin")
public class PatraObjectStorageApplication {

  /// 应用程序入口点,启动 Spring Boot 应用。
  ///
  /// @param args 命令行参数
  public static void main(String[] args) {
    if (System.getProperty("spring.profiles.active") == null
        && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
      System.setProperty("spring.profiles.active", "dev");
    }
    SpringApplication.run(PatraObjectStorageApplication.class, args);
  }
}
