package dev.patra.identity;

import dev.patra.tool.launch.PatraApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// identity 服务的启动入口：前台用户的账号、凭据，以及以后的会话。
///
/// 配置从 Nacos 读（`patra-common.yaml`、`patra-identity.yaml`），由启动器导入。
@SpringBootApplication
public class PatraIdentityApplication {

  /// 经启动器启动应用，服务名 `patra-identity`。
  ///
  /// @param args 命令行参数
  public static void main(String[] args) {
    PatraApplication.run("patra-identity", PatraIdentityApplication.class, args);
  }
}
