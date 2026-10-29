package dev.patra.identity.adapter.account.rest;

import dev.patra.identity.adapter.account.rest.request.UpdatePrivacyRequest;
import dev.patra.identity.adapter.account.rest.request.UpdateProfileRequest;
import dev.patra.identity.adapter.account.rest.response.MyProfileResponse;
import dev.patra.identity.adapter.account.rest.response.PrivacyResponse;
import dev.patra.identity.app.account.usecase.profile.UpdatePrivacyCommand;
import dev.patra.identity.app.account.usecase.profile.UpdateProfileCommand;
import dev.patra.identity.app.account.usecase.query.UserQueryService;
import dev.patra.identity.domain.account.model.aggregate.UserProfile;
import dev.patra.identity.domain.account.model.vo.profile.PrivacySettings;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// 当前用户自己的资料与隐私设置，网关上 `/me/**` 需要登录。
///
/// 资料是整份替换：省略的字段等于清空；机构与关注领域的名称由 identity 向 catalog 取，请求里的名称被忽略。
@Tag(name = "Me", description = "当前用户的资料与隐私设置")
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeProfileController {

  private final CommandBus commandBus;
  private final UserQueryService userQueryService;

  /// 读本人资料（原值，昵称 `null` 表示跟随用户名）。
  ///
  /// @return 资料
  @GetMapping("/profile")
  public MyProfileResponse profile() {
    return MyProfileResponse.from(userQueryService.myProfile());
  }

  /// 整份替换资料。200 返回保存后的资料；422 字段错误或机构 / 主题词不存在（`NOT_FOUND`），
  /// 409 账号注销中或并发修改，503 catalog 不可用（不保存）。
  ///
  /// @param request 请求体
  /// @return 保存后的资料
  @PutMapping("/profile")
  public MyProfileResponse updateProfile(@RequestBody UpdateProfileRequest request) {
    UserProfile saved = commandBus.handle(UpdateProfileCommand.of(request.toDraft()));
    return MyProfileResponse.from(saved);
  }

  /// 读本人隐私设置。
  ///
  /// @return 隐私设置
  @GetMapping("/privacy")
  public PrivacyResponse privacy() {
    return PrivacyResponse.from(userQueryService.myPrivacy());
  }

  /// 改隐私设置，三项必填。200 返回保存后的设置；422 缺失或取值不认识，409 账号注销中。
  ///
  /// @param request 请求体
  /// @return 保存后的设置
  @PutMapping("/privacy")
  public PrivacyResponse updatePrivacy(@RequestBody UpdatePrivacyRequest request) {
    PrivacySettings saved =
        commandBus.handle(
            UpdatePrivacyCommand.of(
                request.profileVisibility(),
                request.professionalVisibility(),
                request.searchIndexing()));
    return PrivacyResponse.from(saved);
  }
}
