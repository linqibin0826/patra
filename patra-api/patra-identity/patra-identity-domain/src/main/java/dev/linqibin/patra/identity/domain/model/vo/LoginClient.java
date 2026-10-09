package dev.linqibin.patra.identity.domain.model.vo;

import dev.linqibin.commons.error.field.FieldViolation;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.domain.exception.InvalidUserFieldsException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// 登录或注册时客户端报的自己：客户端类型和可选的设备标识。
///
/// 客户端类型用 `ClientType.code`（区分大小写），不传按网页端。
///
/// @param clientType 客户端类型
/// @param deviceId 设备标识，可以为 `null`
public record LoginClient(ClientType clientType, DeviceId deviceId) {

  /// 客户端类型不能为空。
  public LoginClient {
    Objects.requireNonNull(clientType, "clientType 不能为 null");
  }

  /// 校验两个字段，错误一次报全。
  ///
  /// @param rawClientType 客户端类型的原始输入，可以为 `null`
  /// @param rawDeviceId 设备标识的原始输入，可以为 `null`
  /// @return 字段错误，合法时为空列表
  public static List<FieldViolation> validate(String rawClientType, String rawDeviceId) {
    List<FieldViolation> violations = new ArrayList<>();
    if (rawClientType != null
        && !rawClientType.isBlank()
        && ClientType.fromCode(rawClientType.strip()).isEmpty()) {
      violations.add(UserFieldViolations.clientTypeInvalidFormat());
    }
    DeviceId.validate(rawDeviceId).ifPresent(violations::add);
    return violations;
  }

  /// 从用户输入创建。
  ///
  /// @param rawClientType 客户端类型的原始输入，`null` 或空白按 `web`
  /// @param rawDeviceId 设备标识的原始输入，`null` 或空白按没有
  /// @return 客户端信息
  /// @throws InvalidUserFieldsException 任一字段不合法
  public static LoginClient of(String rawClientType, String rawDeviceId) {
    List<FieldViolation> violations = validate(rawClientType, rawDeviceId);
    if (!violations.isEmpty()) {
      throw new InvalidUserFieldsException(violations);
    }
    ClientType clientType =
        rawClientType == null || rawClientType.isBlank()
            ? ClientType.WEB
            : ClientType.fromCode(rawClientType.strip()).orElseThrow();
    return new LoginClient(clientType, DeviceId.of(rawDeviceId).orElse(null));
  }

  /// 没有设备标识的网页端。
  ///
  /// @return 客户端信息
  public static LoginClient web() {
    return new LoginClient(ClientType.WEB, null);
  }

  /// 设备标识。
  ///
  /// @return 设备标识；没有时为空
  public Optional<DeviceId> device() {
    return Optional.ofNullable(deviceId);
  }
}
