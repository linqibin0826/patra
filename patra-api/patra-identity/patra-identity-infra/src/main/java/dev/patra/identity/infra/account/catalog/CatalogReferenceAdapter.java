package dev.patra.identity.infra.account.catalog;

import dev.patra.catalog.api.mesh.dto.MeshDescriptorResp;
import dev.patra.catalog.api.mesh.endpoint.MeshEndpoint;
import dev.patra.catalog.api.organization.dto.OrganizationResp;
import dev.patra.catalog.api.organization.endpoint.OrganizationEndpoint;
import dev.patra.identity.domain.account.exception.CatalogUnavailableException;
import dev.patra.identity.domain.account.model.vo.profile.MeshUi;
import dev.patra.identity.domain.account.model.vo.profile.RorId;
import dev.patra.identity.domain.account.port.catalog.CatalogReferencePort;
import dev.patra.tool.core.error.remote.RemoteCallException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/// 经 catalog 的内部 HTTP Interface（`patra-catalog-api`）取机构与主题词的名称快照。
///
/// 下游的三种失败都翻译成 {@link CatalogUnavailableException}：回了错误响应（`RemoteCallException`，
/// 404 + `NOT_FOUND` 除外，那是「不存在」）；连不上或超时（`RestClient` 把 I/O 错误包成
/// `ResourceAccessException`）；注册中心里没有 catalog 实例（Spring Cloud LoadBalancer 抛
/// `IllegalStateException`）。后两种根本没有响应，不会出现 `RemoteCallException`，所以只按这三个类型捕获，
/// 且 `try` 只包住一次代理调用。
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogReferenceAdapter implements CatalogReferencePort {

  private final OrganizationEndpoint organizations;
  private final MeshEndpoint mesh;

  /// 按 ROR ID 取机构名称快照：去首尾空白后的中文名优先，没有就用显示名。
  ///
  /// @param rorId ROR ID
  /// @return 名称；不存在或已停用时为空
  @Override
  public Optional<String> organizationName(RorId rorId) {
    return call(() -> organizations.getOrganization(rorId.value()), "organization", rorId.value())
        .map(CatalogReferenceAdapter::snapshotName);
  }

  /// 按 MeSH UI 取英文名快照（去首尾空白）。
  ///
  /// @param ui 主题词 UI
  /// @return 英文名；不存在时为空
  @Override
  public Optional<String> meshName(MeshUi ui) {
    return call(() -> mesh.getDescriptor(ui.value()), "mesh", ui.value())
        .map(MeshDescriptorResp::name)
        .map(String::strip);
  }

  /// 机构快照：`zhName` 去首尾空白后非空就用它，否则用 `displayName`。
  ///
  /// @param organization 机构摘要
  /// @return 名称
  private static String snapshotName(OrganizationResp organization) {
    String zhName = organization.zhName() == null ? "" : organization.zhName().strip();
    return zhName.isEmpty() ? organization.displayName().strip() : zhName;
  }

  /// 调一次内部接口并翻译失败。
  ///
  /// @param request 代理调用
  /// @param kind 查的是什么（日志用）
  /// @param id 标识（日志用）
  /// @param <T> 响应类型
  /// @return 响应；404 + `NOT_FOUND` 时为空
  /// @throws CatalogUnavailableException 其余失败
  private static <T> Optional<T> call(Supplier<T> request, String kind, String id) {
    try {
      return Optional.of(request.get());
    } catch (RemoteCallException ex) {
      if (ex.getErrorTraits().contains(StandardErrorTrait.NOT_FOUND)) {
        return Optional.empty();
      }
      log.warn(
          "catalog 内部接口返回错误: kind={}, id={}, httpStatus={}, errorCode={}",
          kind,
          id,
          ex.getHttpStatus(),
          ex.getErrorCode());
      throw new CatalogUnavailableException(ex);
    } catch (ResourceAccessException | IllegalStateException ex) {
      log.warn(
          "catalog 内部接口不可用: kind={}, id={}, cause={}", kind, id, ex.getClass().getSimpleName());
      throw new CatalogUnavailableException(ex);
    }
  }
}
