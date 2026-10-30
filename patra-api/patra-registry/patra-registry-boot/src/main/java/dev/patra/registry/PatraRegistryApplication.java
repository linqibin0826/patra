package dev.patra.registry;

import dev.patra.tool.launch.PatraApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// Patra Registry 服务启动类。
///
/// Registry 是 Patra 平台的字典服务,作为平台级字典(国家、语言等)及其参考标准的单一事实来源(SSOT)。
///
/// 主要功能:
///
/// - 提供平台级字典和枚举值查询
/// - 按参考标准把外部原始值解析为字典项(供 catalog 导入时使用)
///
/// @author linqibin
/// @since 0.1.0
@SpringBootApplication
public class PatraRegistryApplication {

  /// 应用程序入口点：经启动器启动，服务名 `patra-registry`，配置从 Nacos 读。
  ///
  /// @param args 命令行参数
  public static void main(String[] args) {
    PatraApplication.run("patra-registry", PatraRegistryApplication.class, args);
  }
}
