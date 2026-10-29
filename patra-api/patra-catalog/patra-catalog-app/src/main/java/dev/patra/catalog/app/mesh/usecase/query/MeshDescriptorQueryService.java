package dev.patra.catalog.app.mesh.usecase.query;

import dev.patra.catalog.domain.kernel.model.vo.SuggestQuery;
import dev.patra.catalog.domain.mesh.exception.MeshDescriptorNotFoundException;
import dev.patra.catalog.domain.mesh.model.read.MeshDescriptorSuggestion;
import dev.patra.catalog.domain.mesh.model.read.MeshDescriptorSummary;
import dev.patra.catalog.domain.mesh.port.read.MeshDescriptorReadPort;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// MeSH 主题词 CQRS 查询服务：门户的建议与 identity 的按 UI 查询。
///
/// 委托 [MeshDescriptorReadPort]；UI 格式不对时直接当不存在，不查库。
///
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
public class MeshDescriptorQueryService {

  /// 主题词 UI：`D` 加 6 位或 9 位数字（与领域里 `MeshUI` 的主题词正则一致）。
  private static final Pattern DESCRIPTOR_UI = Pattern.compile("^D\\d{6}(\\d{3})?$");

  private final MeshDescriptorReadPort readPort;

  /// 边打边搜的主题词建议。
  ///
  /// @param query 已归一化的查询
  /// @return 候选列表，没有命中为空列表
  @Transactional(readOnly = true)
  public List<MeshDescriptorSuggestion> suggest(SuggestQuery query) {
    return readPort.suggest(query);
  }

  /// 按 UI 取主题词摘要。
  ///
  /// @param ui 主题词 UI
  /// @return 主题词摘要
  /// @throws MeshDescriptorNotFoundException 格式不对或不存在
  @Transactional(readOnly = true)
  public MeshDescriptorSummary getByUi(String ui) {
    if (ui == null || !DESCRIPTOR_UI.matcher(ui).matches()) {
      throw new MeshDescriptorNotFoundException(ui);
    }
    return readPort.findByUi(ui).orElseThrow(() -> new MeshDescriptorNotFoundException(ui));
  }
}
