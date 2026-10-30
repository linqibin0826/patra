# patra-tool-object-storage

## 能力

给服务提供对象存储访问。`ObjectStorageAutoConfiguration` 装配 MinIO provider、`ObjectStorageTemplate` 与重试；`ObjectStorageS3AutoConfiguration` 在 `active-provider=s3` 且类路径有 AWS SDK 时装配 S3 provider。

`ObjectStorageOperations` 提供上传、流式下载、下载到文件、`statObject`，以及删除：`delete(bucket, key)` 幂等（对象不在不算错），`deleteByPrefix(bucket, prefix)` 列出前缀下的全部键、每批 1000 个删除，返回个数，桶不存在返回 0，空前缀拒绝（防止删光整桶）。删除只包重试，不记指标。

## 配置（前缀 `patra.object-storage`）

| 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enabled` | boolean | `true` | 总开关 |
| `active-provider` | String | `minio` | 生效的 provider id（`minio` / `s3`） |
| `providers.<id>.endpoint` | String | 无 | 端点（MinIO 必填，S3 可选） |
| `providers.<id>.region` | String | 无 | 区域（S3 必填） |
| `providers.<id>.access-key` | String | 无 | 访问密钥 |
| `providers.<id>.secret-key` | String | 无 | 私密密钥 |
| `retry.max-attempts` | int | `3` | 上传下载最多尝试次数，最小 1 |
| `retry.wait-duration` | long | `1000` | 首次退避毫秒数，最小 0 |
| `max-file-size` | long | `104857600` | 单文件上限（字节），最小 1 |

## 怎么覆盖

```yaml
patra:
  object-storage:
    active-provider: minio
    providers:
      minio:
        endpoint: http://localhost:9000
        access-key: ${MINIO_ACCESS_KEY}
        secret-key: ${MINIO_SECRET_KEY}
    max-file-size: 209715200
```

## 出哪些默认值

无。
