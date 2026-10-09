package dev.linqibin.starter.web.error.handler;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.starter.core.error.spi.ErrorMappingContributor;
import java.util.Optional;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/// Spring MVC 自带异常的错误码：未匹配路径（静态资源处理器的 `NoResourceFoundException`、
/// 没有处理器的 `NoHandlerFoundException`）映射为 404。
public class WebMvcErrorMappingContributor implements ErrorMappingContributor {

  private final HttpStdErrors.Group http;

  /// 构造。
  ///
  /// @param http 按服务前缀生成错误码的组
  public WebMvcErrorMappingContributor(HttpStdErrors.Group http) {
    this.http = http;
  }

  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    if (exception instanceof NoResourceFoundException
        || exception instanceof NoHandlerFoundException) {
      return Optional.of(http.NOT_FOUND());
    }
    return Optional.empty();
  }
}
