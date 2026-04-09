package com.patra.catalog.app.usecase.venue.scopus.command;

import com.patra.common.cqrs.Command;

/// Scopus 期刊指标富化命令。
///
/// 无参数命令——富化范围由 Reader 的 JPQL 条件自动确定
/// （`venueType = 'JOURNAL' AND issnL IS NOT NULL AND NOT EXISTS scopus_rating`）。
///
/// @author linqibin
/// @since 0.1.0
public record VenueScopusEnrichCommand() implements Command<VenueScopusEnrichResult> {}
