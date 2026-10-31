package dev.patra.notification;

import dev.patra.tool.launch.PatraApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// notification 服务的启动入口：站内提醒、提醒偏好与退订、邮件、实时推送。
///
/// 配置从 Nacos 读（`patra-common.yaml`、`patra-notification.yaml`），由启动器导入。
@SpringBootApplication
public class PatraNotificationApplication {

  /// 经启动器启动应用，服务名 `patra-notification`。
  ///
  /// @param args 命令行参数
  public static void main(String[] args) {
    PatraApplication.run("patra-notification", PatraNotificationApplication.class, args);
  }
}
