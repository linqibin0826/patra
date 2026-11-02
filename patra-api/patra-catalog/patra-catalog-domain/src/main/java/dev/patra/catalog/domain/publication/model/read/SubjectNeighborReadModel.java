package dev.patra.catalog.domain.publication.model.read;

import dev.patra.catalog.domain.publication.model.enums.SubjectNeighborReason;
import lombok.Builder;

/// 一篇同主题邻居。
///
/// @param id 文献主键
/// @param reason 来源
/// @param meshDescriptorUi 命中的主要主题词 UI；同期刊时为 null
/// @param meshTerm 命中的主要主题词原文（`cat_mesh_descriptor.name`）；同期刊时为 null
/// @param venueName 期刊全称；同主题词时为 null
/// @author linqibin
/// @since 0.1.0
@Builder
public record SubjectNeighborReadModel(
    long id,
    SubjectNeighborReason reason,
    String meshDescriptorUi,
    String meshTerm,
    String venueName) {}
