package dev.patra.identity.domain.account.port.avatar;

import dev.patra.identity.domain.account.exception.AvatarNotFoundException;
import dev.patra.identity.domain.account.exception.AvatarStorageException;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarRenditions;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarSize;
import dev.patra.identity.domain.account.model.vo.avatar.StoredAvatar;

/// 头像对象的存取：专用桶，键见 `Avatar.objectKey`。对象存储不参与数据库事务，调用方先写对象后提交、
/// 提交后再删旧对象。
public interface AvatarStoragePort {

  /// 写入两档。
  ///
  /// @param avatar 头像
  /// @param renditions 两档字节
  /// @throws AvatarStorageException 写入失败
  void store(Avatar avatar, AvatarRenditions renditions);

  /// 打开一档。
  ///
  /// @param avatar 头像
  /// @param size 尺寸
  /// @return 字节流与长度
  /// @throws AvatarNotFoundException 对象不存在
  /// @throws AvatarStorageException 读取失败
  StoredAvatar open(Avatar avatar, AvatarSize size);

  /// 删掉两档；对象不存在不算错。
  ///
  /// @param avatar 头像
  /// @throws AvatarStorageException 删除失败
  void delete(Avatar avatar);

  /// 删掉一个用户的全部头像对象（注销到期时用）。
  ///
  /// @param userId 用户 ID
  /// @throws AvatarStorageException 删除失败
  void deleteAll(long userId);
}
