package dev.patra.analytics.simulator.event;

import java.util.Base64;
import java.util.SplittableRandom;

/// 一次运行的合成标识：设备 ID 带运行号前缀，用户 ID 落在运行号专属的区间（spec §4），对账据此只取本次运行的人。
public final class SyntheticIds {

  /// 合成用户 ID 的起点：远在 identity 的雪花 ID 之上，不会撞上真实用户
  public static final long USER_ID_FLOOR = 9_000_000_000_000_000_000L;

  /// 每次运行占用的用户 ID 个数
  public static final long USERS_PER_RUN = 10_000_000L;

  private static final int DEVICE_ID_LENGTH = 43;
  private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

  private final int run;
  private final String prefix;

  /// 创建一次运行的标识规则。
  ///
  /// @param run 运行号，1～899
  public SyntheticIds(int run) {
    if (run < 1 || run > 899) {
      throw new IllegalArgumentException("运行号要在 1～899 之间：" + run);
    }
    this.run = run;
    this.prefix = "sim%03d".formatted(run);
  }

  /// 运行号。
  ///
  /// @return 运行号
  public int run() {
    return run;
  }

  /// 设备 ID 的前缀，如 `sim007`。
  ///
  /// @return 前缀
  public String devicePrefix() {
    return prefix;
  }

  /// 生成一个设备 ID：前缀 + 随机 base64url 字符，共 43 位。
  ///
  /// @param random 随机源
  /// @return 设备 ID
  public String deviceId(SplittableRandom random) {
    byte[] bytes = new byte[28];
    random.nextBytes(bytes);
    return prefix
        + BASE64URL.encodeToString(bytes).substring(0, DEVICE_ID_LENGTH - prefix.length());
  }

  /// 第 `index` 个合成用户的 ID。
  ///
  /// @param index 序号，从 0 起
  /// @return 用户 ID
  public long userId(int index) {
    if (index < 0 || index >= USERS_PER_RUN) {
      throw new IllegalArgumentException("用户序号越界：" + index);
    }
    return userIdLow() + index;
  }

  /// 本次运行用户 ID 区间的下界（含）。
  ///
  /// @return 下界
  public long userIdLow() {
    return USER_ID_FLOOR + run * USERS_PER_RUN;
  }

  /// 本次运行用户 ID 区间的上界（含）。
  ///
  /// @return 上界
  public long userIdHigh() {
    return userIdLow() + USERS_PER_RUN - 1;
  }
}
