package dev.patra.identity.app.usecase.avatar.query;

import dev.patra.identity.domain.exception.AvatarNotFoundException;
import dev.patra.identity.domain.model.enums.UserStatus;
import dev.patra.identity.domain.model.read.AvatarOwner;
import dev.patra.identity.domain.model.vo.Username;
import dev.patra.identity.domain.model.vo.avatar.Avatar;
import dev.patra.identity.domain.model.vo.avatar.AvatarSize;
import dev.patra.identity.domain.model.vo.avatar.StoredAvatar;
import dev.patra.identity.domain.port.avatar.AvatarStoragePort;
import dev.patra.identity.domain.port.read.UserReadPort;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 公开的头像读取：`GET /users/{username}/avatar?size=&v=`。
///
/// 地址里的版本 `v` 必须等于当前头像的哈希：同一个地址的内容永远不变，响应才能按不可变长期缓存；旧版本的地址
/// 一律 404，不会把新图缓存在旧地址下。只认当前用户名（旧名不跳转），`ACTIVE` 与 `PENDING_DELETION` 下发
/// （冷静期里本人还能登录），`BANNED` / `DELETED` 404。不看主页可见性：响应是 `public` 缓存，`v` 只出现在
/// 请求者有权看到的响应里，猜不到（spec 第 17 节已拍板 2）。任何不满足一律 404，不区分原因。
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarQueryService {

  private static final Pattern VERSION = Pattern.compile("[0-9a-f]{16}");

  private final UserReadPort reader;
  private final AvatarStoragePort storage;

  /// 找到要下发的头像。
  ///
  /// @param rawUsername 路径里的用户名，大小写不敏感
  /// @param size 查询参数 `size`，`512` 或 `128`
  /// @param version 查询参数 `v`，当前头像的哈希
  /// @return 命中
  /// @throws AvatarNotFoundException 任一条件不满足
  public AvatarRef locate(String rawUsername, String size, String version) {
    if (Username.violation(rawUsername).isPresent()
        || version == null
        || !VERSION.matcher(version).matches()) {
      throw new AvatarNotFoundException();
    }
    AvatarSize avatarSize = AvatarSize.fromPixels(size).orElseThrow(AvatarNotFoundException::new);
    AvatarOwner owner =
        reader.findAvatarOwner(Username.of(rawUsername)).orElseThrow(AvatarNotFoundException::new);
    if (owner.status() != UserStatus.ACTIVE && owner.status() != UserStatus.PENDING_DELETION) {
      throw new AvatarNotFoundException();
    }
    Avatar avatar = owner.avatar();
    if (avatar == null || !avatar.hash().equals(version)) {
      throw new AvatarNotFoundException();
    }
    return AvatarRef.of(avatar, avatarSize);
  }

  /// 打开命中的那一档。
  ///
  /// @param ref 命中
  /// @return 字节流与长度
  /// @throws AvatarNotFoundException 资料里有键但对象不在（库与对象不一致，记告警）
  public StoredAvatar open(AvatarRef ref) {
    try {
      return storage.open(ref.avatar(), ref.size());
    } catch (AvatarNotFoundException e) {
      log.warn(
          "头像对象缺失: userId={}, hash={}, size={}",
          ref.avatar().userId(),
          ref.avatar().hash(),
          ref.size().getPixels());
      throw e;
    }
  }
}
