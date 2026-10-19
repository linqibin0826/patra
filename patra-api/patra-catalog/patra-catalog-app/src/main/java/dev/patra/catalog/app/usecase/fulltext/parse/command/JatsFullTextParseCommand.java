package dev.patra.catalog.app.usecase.fulltext.parse.command;

import dev.patra.catalog.app.usecase.fulltext.parse.FullTextParseRunStats;
import dev.patra.tool.cqrs.Command;

/// JATS 全文解析命令（无参数：处理全部待解析记录）。
///
/// @author linqibin
/// @since 0.11.0
public record JatsFullTextParseCommand() implements Command<FullTextParseRunStats> {}
