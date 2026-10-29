package dev.patra.catalog.adapter.rest.internal;

import dev.patra.catalog.api.mesh.dto.MeshDescriptorResp;
import dev.patra.catalog.api.mesh.endpoint.MeshEndpoint;
import dev.patra.catalog.app.usecase.mesh.query.MeshDescriptorQueryService;
import dev.patra.catalog.domain.model.read.mesh.MeshDescriptorSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/// MeSH 内部端点：`GET /_internal/mesh/descriptors/{ui}`。
///
/// 权限：内部服务间调用（`/_internal` 路径，网关对外 403）。不存在时由查询服务抛出
/// `MeshDescriptorNotFoundException`，错误引擎映射为 404。
///
/// @author linqibin
/// @since 0.1.0
@RestController
@RequiredArgsConstructor
public class MeshEndpointImpl implements MeshEndpoint {

  private final MeshDescriptorQueryService meshDescriptorQueryService;

  @Override
  public MeshDescriptorResp getDescriptor(String ui) {
    MeshDescriptorSummary summary = meshDescriptorQueryService.getByUi(ui);
    return MeshDescriptorResp.of(summary.ui(), summary.name(), summary.treeNumbers());
  }
}
