package dev.patra.interaction.infra.subject.source;

import dev.patra.interaction.domain.subject.exception.SubjectSourceUnavailableException;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.tool.core.error.remote.RemoteCallException;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.ResourceAccessException;

/// 调一次 catalog 内部解析接口并翻译失败，文献与期刊两个来源策略共用。
///
/// 下游的三种失败都翻译成 {@link SubjectSourceUnavailableException}：回了错误响应（`RemoteCallException`；
/// 解析接口不用 404，422 / 400 是本服务的程序错误，日志里看得出来）；连不上或超时（`ResourceAccessException`）；
/// 注册中心里没有 catalog 实例（`IllegalStateException`）。`try` 只包住一次代理调用。
@Slf4j
final class CatalogSourceCalls {

  /// 只有静态方法。
  private CatalogSourceCalls() {}

  /// 调一次代理并翻译失败。
  ///
  /// @param type 对象种类（日志与异常用）
  /// @param request 代理调用
  /// @param keyCount 请求键个数（日志用）
  /// @param <T> 响应类型
  /// @return 响应
  /// @throws SubjectSourceUnavailableException 任何失败
  static <T> T call(SubjectType type, Supplier<T> request, int keyCount) {
    try {
      return request.get();
    } catch (RemoteCallException ex) {
      log.warn(
          "catalog 解析接口返回错误: type={}, keys={}, httpStatus={}, errorCode={}",
          type,
          keyCount,
          ex.getHttpStatus(),
          ex.getErrorCode());
      throw new SubjectSourceUnavailableException(type, ex);
    } catch (ResourceAccessException | IllegalStateException ex) {
      log.warn(
          "catalog 解析接口不可用: type={}, keys={}, cause={}",
          type,
          keyCount,
          ex.getClass().getSimpleName());
      throw new SubjectSourceUnavailableException(type, ex);
    }
  }
}
