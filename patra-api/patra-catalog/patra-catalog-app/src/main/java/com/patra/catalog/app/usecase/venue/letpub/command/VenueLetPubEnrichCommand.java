package com.patra.catalog.app.usecase.venue.letpub.command;

import com.patra.common.cqrs.Command;

/// LetPub 期刊富化命令。
///
/// 无参数命令——富化范围由 Reader 的 JPQL 条件自动确定
/// （`venueType = 'JOURNAL' AND issnL IS NOT NULL AND letPubData IS NULL`）。
///
/// @author linqibin
/// @since 0.1.0
public record VenueLetPubEnrichCommand() implements Command<VenueLetPubEnrichResult> {}
