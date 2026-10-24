package dev.patra.interaction;

import dev.patra.tool.launch.PatraApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// interaction 服务的启动入口：社区互动（本版是文献库与收藏夹）。
///
/// 配置从 Nacos 读（`patra-common.yaml`、`patra-interaction.yaml`），由启动器导入。
@SpringBootApplication
public class PatraInteractionApplication {

  /// 经启动器启动应用，服务名 `patra-interaction`。
  ///
  /// @param args 命令行参数
  public static void main(String[] args) {
    PatraApplication.run("patra-interaction", PatraInteractionApplication.class, args);
  }
}
