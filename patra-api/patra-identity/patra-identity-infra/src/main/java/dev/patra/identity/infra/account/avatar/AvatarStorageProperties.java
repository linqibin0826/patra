package dev.patra.identity.infra.account.avatar;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// 头像存储配置（前缀 `patra.identity.avatar`）。
///
/// @param bucket 头像桶（私有；`minio-init` 建，第一次上传时也会按需建）
@ConfigurationProperties("patra.identity.avatar")
public record AvatarStorageProperties(@DefaultValue("patra-identity-avatar") String bucket) {}
