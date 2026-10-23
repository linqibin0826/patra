package dev.patra.identity.infra.config;

import dev.patra.tool.test.container.initializer.MinIOContainerInitializer;

/// identity 的 MinIO 容器初始化器：建头像桶 `patra-identity-avatar`。
public class IdentityITMinIOContainerInitializer extends MinIOContainerInitializer {

  /// 头像桶。
  ///
  /// @return `patra-identity-avatar`
  @Override
  protected String getBucketName() {
    return "patra-identity-avatar";
  }
}
