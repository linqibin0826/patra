"use client";

import { useQueryClient } from "@tanstack/react-query";
import { CircleCheck, Loader2 } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { CollectionChecklist } from "@/components/library/CollectionChecklist";
import { NoteEditor } from "@/components/library/NoteEditor";
import { formatMonthDay } from "@/lib/format-time";
import { collectPublication, removeItems } from "@/lib/interaction/library-actions";
import type { ItemStatus } from "@/lib/interaction/schema";
import { invalidateLibrary, patchItemStatus } from "@/lib/library/cache";
import { COLLECT_FAILURE, removeConfirmText } from "@/lib/library/messages";
import { useLoginRedirect } from "@/lib/library/use-login-redirect";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnDanger, btnText } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 浮层头部：收藏进行中、刚收进、本来就在、收藏失败（简报 §4.1）
export type PopoverPhase =
  | { kind: "saving" }
  | { kind: "saved" }
  | { kind: "existing" }
  | { kind: "failed"; reason: keyof typeof COLLECT_FAILURE };

interface SavePopoverProps {
  paperKey: string;
  phase: PopoverPhase;
  /// 这篇当前的状态（收藏成功后才有 `itemId`）
  status: ItemStatus | null;
  /// 刚收藏回来的条目里的备注（`undefined` = 不知道，按状态里的 `hasNote` 去取）
  knownNote?: string | null;
  onRetry: () => void;
  /// 移除成功：调用方关浮层
  onRemoved: () => void;
}

/// 收藏浮层的内容（简报 §3.2）：头部 → 放进收藏夹 → 备注 → 从文献库移除。收藏失败时只有头部。
/// 打开时已收藏且有备注：用幂等的收藏接口取回条目（契约里没有按单篇读条目的接口），拿到备注正文。
export function SavePopover({
  paperKey,
  phase,
  status,
  knownNote,
  onRetry,
  onRemoved,
}: SavePopoverProps) {
  const client = useQueryClient();
  const redirectToLogin = useLoginRedirect();
  const [note, setNote] = useState<string | null | undefined>(status?.hasNote ? undefined : null);
  const [confirming, setConfirming] = useState(false);
  const [removing, setRemoving] = useState(false);
  const [removeError, setRemoveError] = useState(false);
  useEffect(() => {
    if (knownNote !== undefined) {
      setNote(knownNote);
    }
  }, [knownNote]);
  const itemId = status?.collected ? status.itemId : null;
  const needsNote = itemId !== null && note === undefined;

  useEffect(() => {
    if (!needsNote) {
      return;
    }
    let cancelled = false;
    void orUnavailable(collectPublication(paperKey)).then((result) => {
      if (!cancelled) {
        setNote(result.ok ? result.item.note : null);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [needsNote, paperKey]);

  const collections = status?.collectionIds.length ?? 0;
  const hasNote = typeof note === "string" ? note !== "" : (status?.hasNote ?? false);

  async function remove() {
    if (!itemId) {
      return;
    }
    const confirmed = hasNote || collections > 0;
    setRemoving(true);
    setRemoveError(false);
    const result = await orUnavailable(removeItems([itemId]));
    setRemoving(false);
    if (!result.ok) {
      if (result.reason === "signed-out") {
        redirectToLogin();
        return;
      }
      setRemoveError(true);
      return;
    }
    patchItemStatus(client, paperKey, {
      collected: false,
      itemId: null,
      collectedAt: null,
      collectionIds: [],
      hasNote: false,
    });
    void invalidateLibrary(client);
    onRemoved();
    if (confirmed) {
      toast.success("已从文献库移除");
      return;
    }
    toast.success("已从文献库移除", {
      action: {
        label: "撤销",
        onClick: () => {
          void orUnavailable(collectPublication(paperKey)).then((again) => {
            if (again.ok) {
              patchItemStatus(client, paperKey, {
                collected: true,
                itemId: again.item.itemId,
                collectedAt: again.item.collectedAt,
                collectionIds: again.item.collectionIds,
                hasNote: again.item.note !== null,
              });
              void invalidateLibrary(client);
            }
          });
        },
      },
    });
  }

  return (
    <div className="flex flex-col">
      <PopoverHeader phase={phase} collectedAt={status?.collectedAt ?? null} onRetry={onRetry} />
      {itemId && phase.kind !== "failed" && (
        <>
          <section className="flex flex-col gap-2 border-t border-border-subtle px-4 py-3.5">
            <h3 className="font-mono text-2xs tracking-caps text-fg-3 uppercase">收藏夹</h3>
            <CollectionChecklist
              itemIds={[itemId]}
              memberOf={(id) => (status?.collectionIds.includes(id) ? 1 : 0)}
              onChanged={(id, added) =>
                patchItemStatus(client, paperKey, {
                  collectionIds: added
                    ? [...new Set([...(status?.collectionIds ?? []), id])]
                    : (status?.collectionIds ?? []).filter((other) => other !== id),
                })
              }
            />
          </section>
          <section className="flex flex-col gap-2 border-t border-border-subtle px-4 py-3.5">
            <h3 className="font-mono text-2xs tracking-caps text-fg-3 uppercase">备注</h3>
            {note === undefined ? (
              <div aria-hidden="true" className="shimmer h-5 w-2/3 rounded-sm" />
            ) : (
              <NoteEditor
                itemId={itemId}
                note={note}
                onSaved={(item) => {
                  setNote(item.note);
                  patchItemStatus(client, paperKey, { hasNote: item.note !== null });
                  void invalidateLibrary(client);
                }}
              />
            )}
          </section>
          <div
            className={cn(
              "flex flex-col gap-2.5 border-t border-border-subtle px-4 py-3",
              confirming && "bg-rust-50",
            )}
          >
            {confirming ? (
              <>
                <p className="text-sm leading-5 text-fg-1">
                  {removeConfirmText(hasNote, collections)}
                </p>
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    aria-disabled={removing || undefined}
                    onClick={() => !removing && void remove()}
                    className={cn(btnDanger, "px-3.5 py-1.5 text-sm")}
                  >
                    移除
                  </button>
                  <button
                    type="button"
                    className={cn(btnText, "text-sm")}
                    onClick={() => setConfirming(false)}
                  >
                    取消
                  </button>
                </div>
              </>
            ) : (
              <button
                type="button"
                aria-disabled={removing || undefined}
                onClick={() => {
                  if (removing) {
                    return;
                  }
                  if (hasNote || collections > 0) {
                    setConfirming(true);
                  } else {
                    void remove();
                  }
                }}
                className="self-start text-sm font-medium text-status-danger underline-offset-4 hover:underline"
              >
                从文献库移除
              </button>
            )}
            {removeError && <p className="text-xs text-status-danger">没有移除成功，请稍后再试</p>}
          </div>
        </>
      )}
    </div>
  );
}

function PopoverHeader({
  phase,
  collectedAt,
  onRetry,
}: {
  phase: PopoverPhase;
  collectedAt: string | null;
  onRetry: () => void;
}) {
  const link = (
    <Link href="/library" className="shrink-0 text-sm text-fg-2 transition-colors hover:text-fg-1">
      查看文献库 →
    </Link>
  );
  if (phase.kind === "failed") {
    const failure = COLLECT_FAILURE[phase.reason];
    return (
      <div role="alert" className="flex items-center justify-between gap-3 px-4 py-3.5">
        <span className="text-sm font-medium text-status-danger">{failure.text}</span>
        {failure.retry && (
          <button type="button" className={cn(btnText, "shrink-0 text-sm")} onClick={onRetry}>
            重试
          </button>
        )}
      </div>
    );
  }
  return (
    <div className="flex items-start justify-between gap-3 px-4 py-3.5">
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="inline-flex items-center gap-2 text-sm font-medium text-fg-1">
          {phase.kind === "saving" ? (
            <Loader2 className="size-3.5 animate-spin text-fg-3" aria-hidden="true" />
          ) : (
            <CircleCheck className="size-4 text-status-success" aria-hidden="true" />
          )}
          {phase.kind === "saving"
            ? "正在收藏…"
            : phase.kind === "saved"
              ? "已收进文献库"
              : "已在文献库中"}
        </span>
        {phase.kind === "existing" && collectedAt && (
          <span className="pl-6 text-xs text-fg-3">{formatMonthDay(collectedAt)}收藏</span>
        )}
      </div>
      {link}
    </div>
  );
}
