package dev.patra.identity.domain.account.port.catalog;

import dev.patra.identity.domain.account.exception.CatalogUnavailableException;
import dev.patra.identity.domain.account.model.vo.profile.MeshUi;
import dev.patra.identity.domain.account.model.vo.profile.RorId;
import java.util.Optional;

/// 向 catalog 校验资料里的权威自然键并取名称快照（决策 F）：不信前端传来的名称。
public interface CatalogReferencePort {

  /// 按 ROR ID 取机构名称快照：中文名优先，没有就用 ROR 显示名。
  ///
  /// @param rorId ROR ID
  /// @return 名称；catalog 里没有这个机构或它已停用时为空
  /// @throws CatalogUnavailableException catalog 不可用
  Optional<String> organizationName(RorId rorId);

  /// 按 MeSH UI 取主题词的英文名快照。
  ///
  /// @param ui 主题词 UI
  /// @return 英文名；catalog 里没有这个主题词时为空
  /// @throws CatalogUnavailableException catalog 不可用
  Optional<String> meshName(MeshUi ui);
}
