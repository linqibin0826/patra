package dev.patra.identity.adapter.account.rest;

import dev.patra.identity.adapter.account.rest.converter.AvatarUrls;
import dev.patra.identity.adapter.account.rest.response.AvatarResponse;
import dev.patra.identity.app.account.usecase.avatar.ChangeAvatarCommand;
import dev.patra.identity.app.account.usecase.avatar.RemoveAvatarCommand;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.tool.cqrs.CommandBus;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/// 当前用户的头像，网关上 `/me/**` 需要登录。上传与删除即时生效，不跟资料表单一起保存。
@Tag(name = "Me", description = "当前用户的头像")
@RestController
@RequestMapping("/me/avatar")
@RequiredArgsConstructor
public class MeAvatarController {

  private final CommandBus commandBus;

  /// 上传头像（multipart，part 名 `file`）。200 返回新地址；422 原因码在 `errors[0].code`（`REQUIRED`、
  /// `UNSUPPORTED_TYPE`、`TOO_SMALL`、`TOO_MANY_PIXELS`、`UNREADABLE`）；超过 5 MB 413；注销中 409；
  /// 对象存储不可用 503。
  ///
  /// @param file 上传的文件，缺失时交给处理器报 `REQUIRED`
  /// @return 新头像地址
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public AvatarResponse upload(@RequestPart(name = "file", required = false) MultipartFile file) {
    Avatar avatar = commandBus.handle(ChangeAvatarCommand.of(bytesOf(file)));
    return AvatarResponse.of(AvatarUrls.of(avatar));
  }

  /// 移除头像，回到默认头像；没有头像也是 204。注销中 409。
  ///
  /// @return 204
  @DeleteMapping
  public ResponseEntity<Void> remove() {
    commandBus.handle(RemoveAvatarCommand.of());
    return ResponseEntity.noContent().build();
  }

  /// 读出上传的字节；没有文件时为空数组。
  ///
  /// @param file 上传的文件
  /// @return 字节
  private static byte[] bytesOf(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return new byte[0];
    }
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
