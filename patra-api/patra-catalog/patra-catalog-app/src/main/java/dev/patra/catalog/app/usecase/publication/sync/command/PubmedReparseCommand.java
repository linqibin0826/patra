package dev.patra.catalog.app.usecase.publication.sync.command;

import dev.patra.catalog.app.usecase.publication.sync.dto.PubmedReparseResult;
import dev.patra.tool.cqrs.Command;

/// 本地重算命令：按来源表里保存的原文重新解析全部未软删的文献，不访问网络。
///
/// @author linqibin
/// @since 0.1.0
public record PubmedReparseCommand() implements Command<PubmedReparseResult> {}
