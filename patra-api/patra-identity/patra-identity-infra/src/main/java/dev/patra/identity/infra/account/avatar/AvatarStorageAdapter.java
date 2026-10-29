package dev.patra.identity.infra.account.avatar;

import dev.patra.identity.domain.account.exception.AvatarNotFoundException;
import dev.patra.identity.domain.account.exception.AvatarStorageException;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarRenditions;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarSize;
import dev.patra.identity.domain.account.model.vo.avatar.StoredAvatar;
import dev.patra.identity.domain.account.port.avatar.AvatarStoragePort;
import dev.patra.tool.objectstorage.ObjectStorageOperations;
import dev.patra.tool.objectstorage.domain.DownloadResult;
import dev.patra.tool.objectstorage.domain.ObjectMetadata;
import dev.patra.tool.objectstorage.domain.ObjectNotFoundException;
import java.io.ByteArrayInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 头像对象写进专用桶：两档 `image/webp`；第二档失败时尽量删掉第一档再报错。对象存储的运行时异常一律包成
/// `AvatarStorageException`（503），对象不存在包成 `AvatarNotFoundException`（404）。
@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarStorageAdapter implements AvatarStoragePort {

  private static final String CONTENT_TYPE = "image/webp";

  private final ObjectStorageOperations storage;
  private final AvatarStorageProperties properties;

  /// 写入两档。
  ///
  /// @param avatar 头像
  /// @param renditions 两档字节
  @Override
  public void store(Avatar avatar, AvatarRenditions renditions) {
    put(avatar, AvatarSize.LARGE, renditions.large());
    try {
      put(avatar, AvatarSize.SMALL, renditions.small());
    } catch (AvatarStorageException e) {
      try {
        storage.delete(properties.bucket(), avatar.objectKey(AvatarSize.LARGE));
      } catch (RuntimeException cleanup) {
        e.addSuppressed(cleanup);
      }
      throw e;
    }
  }

  /// 打开一档。
  ///
  /// @param avatar 头像
  /// @param size 尺寸
  /// @return 字节流与长度
  @Override
  public StoredAvatar open(Avatar avatar, AvatarSize size) {
    try {
      DownloadResult result = storage.download(properties.bucket(), avatar.objectKey(size));
      return StoredAvatar.of(result.getContent(), result.getContentLength());
    } catch (ObjectNotFoundException e) {
      throw new AvatarNotFoundException();
    } catch (RuntimeException e) {
      throw new AvatarStorageException("读取头像失败: " + avatar.objectKey(size), e);
    }
  }

  /// 删掉两档。
  ///
  /// @param avatar 头像
  @Override
  public void delete(Avatar avatar) {
    try {
      storage.delete(properties.bucket(), avatar.objectKey(AvatarSize.LARGE));
      storage.delete(properties.bucket(), avatar.objectKey(AvatarSize.SMALL));
    } catch (RuntimeException e) {
      throw new AvatarStorageException("删除头像失败: userId=" + avatar.userId(), e);
    }
  }

  /// 按前缀删掉一个用户的全部头像对象。
  ///
  /// @param userId 用户 ID
  @Override
  public void deleteAll(long userId) {
    try {
      int deleted = storage.deleteByPrefix(properties.bucket(), Avatar.prefix(userId));
      log.info("已删除用户的全部头像对象: userId={}, objects={}", userId, deleted);
    } catch (RuntimeException e) {
      throw new AvatarStorageException("删除用户的全部头像失败: userId=" + userId, e);
    }
  }

  /// 上传一档。
  ///
  /// @param avatar 头像
  /// @param size 尺寸
  /// @param bytes 字节
  private void put(Avatar avatar, AvatarSize size, byte[] bytes) {
    try {
      storage.upload(
          properties.bucket(),
          avatar.objectKey(size),
          new ByteArrayInputStream(bytes),
          ObjectMetadata.builder().contentLength(bytes.length).contentType(CONTENT_TYPE).build());
    } catch (RuntimeException e) {
      throw new AvatarStorageException("写入头像失败: " + avatar.objectKey(size), e);
    }
  }
}
