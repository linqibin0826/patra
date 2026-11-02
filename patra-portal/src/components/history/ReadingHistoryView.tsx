"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Pause, SlidersHorizontal } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { FormAlert } from "@/components/auth/FormAlert";
import { ClearHistoryDialog } from "@/components/history/ClearHistoryDialog";
import { EmptyLink, HistoryEmpty } from "@/components/history/HistoryEmpty";
import { HistoryRow } from "@/components/history/HistoryRow";
import { HistorySkeleton } from "@/components/history/HistorySkeleton";
import { deleteHistoryEntry } from "@/lib/analytics/history-actions";
import { useCommonFailureRedirect } from "@/lib/auth/use-common-failure-redirect";
import { groupHistoryByDay } from "@/lib/history/groups";
import {
  markHistoryCleared,
  removeHistoryEntry,
  SignedOutError,
  useReadingHistory,
} from "@/lib/history/queries";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

const PAGE = "/history";
const SETTINGS = "/settings/privacy#reading";

/// 阅读历史页的主体（简报 v0.18 §3.7）：页头、记录已关闭提示、按天分组的列表、翻页、单条删除与清空、空状态与降级
export function ReadingHistoryView() {
  const client = useQueryClient();
  const leave = useCommonFailureRedirect(PAGE);
  const history = useReadingHistory();
  const [deleting, setDeleting] = useState<ReadonlySet<string>>(new Set());
  const [failed, setFailed] = useState<ReadonlySet<string>>(new Set());
  const [clearing, setClearing] = useState(false);
  const [cleared, setCleared] = useState(false);
  const deleteButtons = useRef(new Map<string, HTMLButtonElement>());
  const clearButton = useRef<HTMLButtonElement>(null);
  /// 空状态的按钮：删掉最后一条、清空之后焦点去这里
  const emptyAction = useRef<HTMLAnchorElement>(null);
  /// 删除成功后要聚焦的行（null 表示「清空历史」按钮）；undefined 表示不移动焦点
  const focusNext = useRef<string | null | undefined>(undefined);

  const pages = history.data?.pages ?? [];
  const items = pages.flatMap((page) => page.items);
  const recordingEnabled = pages[0]?.recordingEnabled ?? true;
  const signedOut = history.error instanceof SignedOutError;
  /// 删光了已加载的条目、后面还有：接着取下一页，不显示空状态。取失败了就不再算补页，改显示失败提示与重试
  const refilling = items.length === 0 && history.hasNextPage && !history.isFetchNextPageError;

  useEffect(() => {
    if (refilling && !history.isFetchingNextPage && !history.isFetchNextPageError) {
      void history.fetchNextPage();
    }
  }, [refilling, history]);

  useEffect(() => {
    if (signedOut) {
      leave("signed-out");
    }
  }, [signedOut, leave]);

  useEffect(() => {
    if (focusNext.current === undefined) {
      return;
    }
    const target = focusNext.current;
    focusNext.current = undefined;
    (target === null
      ? (emptyAction.current ?? clearButton.current)
      : deleteButtons.current.get(target)
    )?.focus();
  });

  const toggle = (set: ReadonlySet<string>, id: string, on: boolean) => {
    const next = new Set(set);
    if (on) next.add(id);
    else next.delete(id);
    return next;
  };

  async function remove(articleId: string) {
    if (deleting.has(articleId)) {
      return;
    }
    setDeleting((current) => toggle(current, articleId, true));
    setFailed((current) => toggle(current, articleId, false));
    const result = await orUnavailable(deleteHistoryEntry(articleId));
    setDeleting((current) => toggle(current, articleId, false));
    if (result.ok) {
      const index = items.findIndex((item) => item.articleId === articleId);
      focusNext.current = items[index + 1]?.articleId ?? items[index - 1]?.articleId ?? null;
      removeHistoryEntry(client, articleId);
    } else if (result.reason === "signed-out") {
      leave("signed-out");
    } else {
      setFailed((current) => toggle(current, articleId, true));
    }
  }

  const loadMoreFailed = (
    <p role="alert" className="flex items-center gap-2 text-xs text-status-danger">
      没能加载更早的记录。
      <button
        type="button"
        onClick={() => void history.fetchNextPage()}
        className="font-medium text-link hover:text-link-hover"
      >
        重试
      </button>
    </p>
  );

  return (
    <div className="mx-auto max-w-[880px] px-gutter pt-10 pb-30 max-md:pt-6">
      <div className="flex items-end justify-between gap-6 max-md:flex-col max-md:items-start">
        <div>
          <h1 className="font-serif text-display-3 leading-heading font-medium tracking-tight text-fg-1">
            阅读历史
          </h1>
          <p className="mt-3 max-w-[48em] text-sm leading-[22px] text-fg-3">
            你在 Patra 上打开过的文献，只有你自己能看到。同一篇只留最近一次，记录保留 12 个月。
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-4">
          <Link
            href={SETTINGS}
            className="inline-flex items-center gap-1 text-sm font-medium text-link hover:text-link-hover"
          >
            <SlidersHorizontal className="size-3.5" strokeWidth={1.75} aria-hidden="true" />
            阅读记录设置
          </Link>
          <button
            ref={clearButton}
            type="button"
            onClick={() => setClearing(true)}
            disabled={items.length === 0}
            className={cn(btnSecondary, "px-3 py-1.5 text-sm")}
          >
            清空历史
          </button>
        </div>
      </div>

      <div className="mt-8">
        {history.isPending || signedOut || refilling ? (
          <HistorySkeleton />
        ) : history.isError && history.data === undefined ? (
          <HistoryEmpty
            title="阅读历史暂时打不开"
            description="请稍后再试。文献页照常能看，只是这里暂时列不出来。"
            action={
              <button
                type="button"
                onClick={() => void history.refetch()}
                className={cn(btnSecondary, "px-4 py-2 text-sm")}
              >
                重试
              </button>
            }
          />
        ) : items.length === 0 && history.isFetchNextPageError ? (
          <div className="flex justify-center py-10">{loadMoreFailed}</div>
        ) : items.length === 0 ? (
          cleared ? (
            <HistoryEmpty
              title="阅读历史已清空"
              description="之后打开的文献会继续记在这里。"
              action={
                <EmptyLink href="/" linkRef={emptyAction}>
                  去看看文献
                </EmptyLink>
              }
            />
          ) : recordingEnabled ? (
            <HistoryEmpty
              title="还没有阅读历史"
              description="你打开过的文献会按时间记在这里，只有你自己能看到。"
              action={
                <EmptyLink href="/" linkRef={emptyAction}>
                  去看看文献
                </EmptyLink>
              }
            />
          ) : (
            <HistoryEmpty
              title="阅读记录已关闭"
              description="打开之后，你看过的文献会按时间记在这里，只有你自己能看到。"
              action={
                <EmptyLink href={SETTINGS} linkRef={emptyAction}>
                  去设置里开启
                </EmptyLink>
              }
            />
          )
        ) : (
          <>
            {!recordingEnabled && (
              <div className="mb-2">
                <FormAlert tone="info" icon={Pause}>
                  阅读记录已关闭：现在打开的文献不会记到这里，下面是关闭之前的记录。
                  <Link
                    href={SETTINGS}
                    className="ml-1 font-medium text-link hover:text-link-hover"
                  >
                    去设置里开启
                  </Link>
                </FormAlert>
              </div>
            )}
            {groupHistoryByDay(items, Date.now()).map((group, index) => (
              <section key={group.key} aria-labelledby={`history-day-${group.key}`}>
                <h2
                  id={`history-day-${group.key}`}
                  className={cn(
                    "border-b border-border-subtle pb-2 text-xs font-medium tracking-wide text-fg-3",
                    index === 0 ? "mt-5" : "mt-7",
                  )}
                >
                  {group.label}
                </h2>
                {group.items.map((entry) => (
                  <HistoryRow
                    key={entry.articleId}
                    entry={entry}
                    deleting={deleting.has(entry.articleId)}
                    failed={failed.has(entry.articleId)}
                    onDelete={() => void remove(entry.articleId)}
                    deleteRef={(button) => {
                      if (button) deleteButtons.current.set(entry.articleId, button);
                      else deleteButtons.current.delete(entry.articleId);
                    }}
                  />
                ))}
              </section>
            ))}
            <div className="mt-7 flex flex-col items-center gap-4">
              {history.isFetchNextPageError ? (
                loadMoreFailed
              ) : history.hasNextPage ? (
                <button
                  type="button"
                  onClick={() => void history.fetchNextPage()}
                  disabled={history.isFetchingNextPage}
                  className="text-sm font-medium text-link hover:text-link-hover"
                >
                  {history.isFetchingNextPage ? "加载中…" : "加载更早的记录"}
                </button>
              ) : (
                <p className="text-xs text-fg-3">没有更早的了。超过 12 个月的记录会自动清理。</p>
              )}
            </div>
          </>
        )}
      </div>

      <ClearHistoryDialog
        open={clearing}
        page={PAGE}
        recordingEnabled={recordingEnabled}
        onOpenChange={setClearing}
        finalFocus={() => emptyAction.current}
        onCleared={() => {
          markHistoryCleared(client);
          setCleared(true);
        }}
      />
    </div>
  );
}
