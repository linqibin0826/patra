package dev.linqibin.starter.web.error.probe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/// 只在测试里存在的接口，用来走一遍真实的 Bean Validation 失败路径。
@RestController
public class ValidationProbeController {

  /// 校验通过时什么都不做。
  ///
  /// @param request 带校验注解的请求体
  @PostMapping("/probe/validation")
  public void probe(@Valid @RequestBody ProbeRequest request) {
    // 只用来触发校验
  }

  /// 带校验注解的请求体。
  ///
  /// @param email 不能为空
  /// @param password 最长 8 个字符
  public record ProbeRequest(@NotBlank String email, @Size(max = 8) String password) {}
}
