"use client";

import { Dialog } from "@/components/ui/dialog";
import { type DiffPart, diffVersions } from "@/lib/discussion/revision-diff";
import { usePostRevisions } from "@/lib/experience/queries";
import { fieldChanges, fieldValues } from "@/lib/experience/revision-fields";
import { formatMonthDayTime } from "@/lib/format-time";
import type { Revision } from "@/lib/interaction/experience-schema";
import { btnSecondary } from "@/lib/portal-ui";

/// 版本标签：第一项是当前版本，最后一项是最初版本（发布时刻）
function versionLabel(index: number, count: number, at: string): string {
  const time = formatMonthDayTime(at);
  if (index === count - 1) {
    return `最初版本 · ${time} 发布`;
  }
  return index === 0 ? `当前版本 · ${time} 修改` : `${time} 修改`;
}

/// 片段的 key：它在两版拼起来的文字里的起点
function keyed(parts: DiffPart[]): (DiffPart & { key: string })[] {
  let offset = 0;
  return parts.map((part) => {
    const key = `${part.kind}-${offset}`;
    offset += part.text.length;
    return { ...part, key };
  });
}

/// 投稿经验的修改历史（spec 8.3）：每一版列出与上一版相比改了的结构化字段（`字段：旧值 → 新值`）与正文的增删；
/// 最初版本只列它的值。正文按词比较，复用讨论区的 `diffVersions`
export function PostRevisionDialog({
  postId,
  open,
  onOpenChange,
}: {
  postId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const query = usePostRevisions(postId, open);
  const data = query.data;
  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title="修改历史"
      description="发布 5 分钟后的修改会留下记录，所有人都能看到。"
      size="lg"
    >
      {query.isError ? (
        <div className="flex flex-col items-start gap-3">
          <p className="text-fg-2 text-sm">修改历史暂时打不开</p>
          <button type="button" onClick={() => void query.refetch()} className={btnSecondary}>
            重试
          </button>
        </div>
      ) : data && "gone" in data ? (
        <p className="text-fg-2 text-sm">这条投稿经验已被删除。</p>
      ) : data ? (
        <ol className="m-0 flex list-none flex-col p-0">
          {data.items.map((revision, index, all) => (
            <Version
              key={revision.writtenAt}
              label={versionLabel(index, all.length, revision.writtenAt)}
              revision={revision}
              older={all[index + 1] ?? null}
            />
          ))}
        </ol>
      ) : (
        <div aria-hidden="true" className="flex flex-col gap-3">
          <div className="h-16 motion-safe:animate-pulse rounded-md bg-paper-200" />
          <div className="h-16 motion-safe:animate-pulse rounded-md bg-paper-200" />
        </div>
      )}
    </Dialog>
  );
}

function Version({
  label,
  revision,
  older,
}: {
  label: string;
  revision: Revision;
  older: Revision | null;
}) {
  const body = revision.body ?? "";
  const bodyChanged = older !== null && (older.body ?? "") !== body;
  return (
    <li className="flex flex-col gap-2 border-border-subtle border-t py-4 first:border-t-0 first:pt-1">
      <h3 className="m-0 font-medium text-fg-1 text-xs">{label}</h3>
      {older ? (
        <ul className="m-0 flex list-none flex-col gap-1 p-0 text-fg-1 text-sm">
          {fieldChanges(older, revision).map((change) => (
            <li key={change.label}>
              {change.label}：{change.from} → {change.to}
            </li>
          ))}
        </ul>
      ) : (
        <dl className="m-0 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-4 gap-y-1 text-sm">
          {fieldValues(revision).map((field) => (
            <div key={field.label} className="contents">
              <dt className="text-fg-3">{field.label}</dt>
              <dd className="m-0 text-fg-1">{field.value}</dd>
            </div>
          ))}
        </dl>
      )}
      {(bodyChanged || (!older && body)) && (
        <p className="m-0 whitespace-pre-wrap text-fg-1 text-sm leading-relaxed [overflow-wrap:anywhere]">
          {keyed(
            bodyChanged ? diffVersions(older?.body ?? "", body) : [{ kind: "same", text: body }],
          ).map(({ key, kind, text }) =>
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
      )}
    </li>
  );
}
