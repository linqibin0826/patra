"use client";

import { useState } from "react";
import { Dialog } from "@/components/ui/dialog";
import { useRevisions } from "@/lib/discussion/queries";
import { type DiffPart, diffVersions, resolveMentionMarkers } from "@/lib/discussion/revision-diff";
import { formatMonthDayTime } from "@/lib/format-time";
import { btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 每个版本超过 8 行折叠
const FOLD_LINES = 8;

/// 版本标签：最新的是当前版本，最后一个是最初版本（发表时刻）
function versionLabel(index: number, count: number, at: string): string {
  const time = formatMonthDayTime(at);
  if (index === count - 1) {
    return `最初版本 · ${time} 发表`;
  }
  return index === 0 ? `当前版本 · ${time} 修改` : `${time} 修改`;
}

/// 修改历史（简报 §3.8）：每个版本显示 Markdown 原文，标出与上一版相比的增删（新增下划线、删掉删除线）
export function RevisionHistoryDialog({
  commentId,
  onClose,
}: {
  commentId: string | null;
  onClose: () => void;
}) {
  const query = useRevisions(commentId);
  return (
    <Dialog
      open={commentId !== null}
      onOpenChange={(open) => !open && onClose()}
      title="修改历史"
      description="发布 5 分钟后的修改会留下记录，所有人都能看到。"
      size="lg"
    >
      {query.isError ? (
        <div className="flex flex-col items-start gap-3">
          <p className="text-sm text-fg-2">修改历史暂时打不开</p>
          <button type="button" onClick={() => void query.refetch()} className={btnSecondary}>
            重试
          </button>
        </div>
      ) : query.data ? (
        <div className="flex flex-col">
          {query.data.versions.length > 1 && (
            <p className="mb-2 text-xs text-fg-3">下划线是新增的内容 · 删除线是删掉的内容</p>
          )}
          {query.data.versions.map((version, index, versions) => {
            const mentions = query.data.mentions;
            const text = resolveMentionMarkers(version.body, mentions);
            const older = versions[index + 1];
            const parts: DiffPart[] = older
              ? diffVersions(resolveMentionMarkers(older.body, mentions), text)
              : [{ kind: "same", text }];
            return (
              <Version
                key={version.at}
                label={versionLabel(index, versions.length, version.at)}
                parts={parts}
                long={text.split("\n").length > FOLD_LINES}
              />
            );
          })}
        </div>
      ) : (
        <div aria-hidden="true" className="flex flex-col gap-3">
          <div className="h-16 motion-safe:animate-pulse rounded-md bg-paper-200" />
          <div className="h-16 motion-safe:animate-pulse rounded-md bg-paper-200" />
        </div>
      )}
    </Dialog>
  );
}

/// 片段的 key：它在两版拼起来的文字里的起点（片段只按顺序出现，不会重排）
function keyed(parts: DiffPart[]): (DiffPart & { key: string })[] {
  let offset = 0;
  return parts.map((part) => {
    const key = `${part.kind}-${offset}`;
    offset += part.text.length;
    return { ...part, key };
  });
}

function Version({ label, parts, long }: { label: string; parts: DiffPart[]; long: boolean }) {
  const [expanded, setExpanded] = useState(false);
  return (
    <section className="flex flex-col gap-2 border-t border-border-subtle py-4 first:border-t-0 first:pt-1">
      <h3 className="text-xs font-medium text-fg-1">{label}</h3>
      <p
        className={cn(
          "text-sm leading-relaxed whitespace-pre-wrap text-fg-1 [overflow-wrap:anywhere]",
          long && !expanded && "line-clamp-8",
        )}
      >
        {keyed(parts).map(({ key, kind, text }) =>
          kind === "added" ? (
            <ins
              key={key}
              className="bg-moss-50 text-fg-1 underline decoration-moss-500 underline-offset-3"
            >
              {text}
            </ins>
          ) : kind === "removed" ? (
            <del key={key} className="bg-rust-50 text-fg-2 line-through decoration-rust-500">
              {text}
            </del>
          ) : (
            <span key={key}>{text}</span>
          ),
        )}
      </p>
      {long && (
        <button
          type="button"
          onClick={() => setExpanded((value) => !value)}
          className="self-start text-xs font-medium text-link hover:text-link-hover"
        >
          {expanded ? "收起" : "展开"}
        </button>
      )}
    </section>
  );
}
