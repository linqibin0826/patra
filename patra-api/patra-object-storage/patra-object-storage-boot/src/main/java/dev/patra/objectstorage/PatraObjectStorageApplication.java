package dev.patra.objectstorage;

import dev.patra.tool.launch.PatraApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// 存储元数据服务的Spring Boot应用入口。
///
/// Patra对象存储元数据管理微服务的启动类,负责记录和管理上传到 外部对象存储提供商的文件元数据。
///
/// 配置从 Nacos 读（`patra-common.yaml`、`patra-object-storage.yaml`），由启动器导入。
@SpringBootApplication(scanBasePackages = "dev.patra")
public class PatraObjectStorageApplication {

  /// 应用程序入口点：经启动器启动，服务名 `patra-object-storage`。
  ///
  /// @param args 命令行参数
  public static void main(String[] args) {
    PatraApplication.run("patra-object-storage", PatraObjectStorageApplication.class, args);
  }
}
