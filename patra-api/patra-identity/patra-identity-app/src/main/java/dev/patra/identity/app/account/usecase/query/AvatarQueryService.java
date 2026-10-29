package dev.patra.identity.app.account.usecase.query;

import dev.patra.identity.domain.account.exception.AvatarNotFoundException;
import dev.patra.identity.domain.account.model.read.AvatarOwner;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarSize;
import dev.patra.identity.domain.account.model.vo.avatar.StoredAvatar;
import dev.patra.identity.domain.account.port.avatar.AvatarStoragePort;
import dev.patra.identity.domain.account.port.read.UserReadPort;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// 公开的头像读取：`GET /users/{userId}/avatar?size=&v=`。
///
/// 地址里的版本 `v` 必须等于当前头像的哈希：同一个地址的内容永远不变，响应才能按不可变长期缓存；旧版本的地址
/// 一律 404，不会把新图缓存在旧地址下。地址按用户 ID，改用户名后照常可用（PAP-108）；用户 ID 只认规范的十进制
/// 写法（不认 `+1`、`007`），同一张头像只有一个地址。`UserStatus.servesAvatar()` 为真才下发（`ACTIVE` 与
/// `PENDING_DELETION`），`BANNED` / `DELETED` 404。不看主页可见性：响应是 `public` 缓存，`v` 只出现在
/// 请求者有权看到的响应里，猜不到（spec 第 17 节已拍板 2）。任何不满足一律 404，不区分原因。
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarQueryService {

  private static final Pattern VERSION = Pattern.compile("[0-9a-f]{16}");

  /// 规范的正整数写法，最多 19 位；19 位里超出 `long` 的由解析时拦下。
  private static final Pattern USER_ID = Pattern.compile("[1-9][0-9]{0,18}");

  private final UserReadPort reader;
  private final AvatarStoragePort storage;

  /// 找到要下发的头像。
  ///
  /// @param rawUserId 路径里的用户 ID
  /// @param size 查询参数 `size`，`512` 或 `128`
  /// @param version 查询参数 `v`，当前头像的哈希
  /// @return 命中
  /// @throws AvatarNotFoundException 任一条件不满足
  public AvatarRef locate(String rawUserId, String size, String version) {
    if (version == null || !VERSION.matcher(version).matches()) {
      throw new AvatarNotFoundException();
    }
    long userId = parseUserId(rawUserId);
    AvatarSize avatarSize = AvatarSize.fromPixels(size).orElseThrow(AvatarNotFoundException::new);
    AvatarOwner owner = reader.findAvatarOwner(userId).orElseThrow(AvatarNotFoundException::new);
    if (!owner.status().servesAvatar()) {
      throw new AvatarNotFoundException();
    }
    Avatar avatar = owner.avatar();
    if (avatar == null || !avatar.hash().equals(version)) {
      throw new AvatarNotFoundException();
    }
    return AvatarRef.of(avatar, avatarSize);
  }

  /// 解析路径里的用户 ID：只认规范的正整数写法，溢出也算不合法。
  ///
  /// @param raw 路径里的用户 ID
  /// @return 用户 ID
  /// @throws AvatarNotFoundException 不合法
  private static long parseUserId(String raw) {
    if (raw == null || !USER_ID.matcher(raw).matches()) {
      throw new AvatarNotFoundException();
    }
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException e) {
      throw new AvatarNotFoundException();
    }
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
