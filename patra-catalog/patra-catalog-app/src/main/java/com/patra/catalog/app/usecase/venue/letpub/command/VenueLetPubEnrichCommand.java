package com.patra.catalog.app.usecase.venue.letpub.command;

import com.patra.catalog.app.usecase.venue.VenueEnrichRunStats;
import dev.linqibin.commons.cqrs.Command;

/// LetPub 期刊富化命令。
///
/// @param targetYear 目标评级年份（如 2025），Reader 筛选缺少该年份 JCR 评级数据的期刊
/// @param minCitedByCount 最低被引次数阈值，0 表示不过滤
/// @author linqibin
/// @since 0.1.0
public record VenueLetPubEnrichCommand(short targetYear, int minCitedByCount)
    implements Command<VenueEnrichRunStats> {}
