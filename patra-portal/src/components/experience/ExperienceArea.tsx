"use client";

import { useQueryClient } from "@tanstack/react-query";
import { PenLine } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { toast } from "sonner";
import { ExperienceForm } from "@/components/experience/ExperienceForm";
import { ExperienceList } from "@/components/experience/ExperienceList";
import { ExperienceRow } from "@/components/experience/ExperienceRow";
import { ExperienceSummary, type SummaryState } from "@/components/experience/ExperienceSummary";
import { ResendVerificationButton } from "@/components/portal/ResendVerificationButton";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import { SortToggle } from "@/components/portal/SortToggle";
import { authPageHref } from "@/lib/auth/next-path";
import { useViewer, type ViewerSeed } from "@/lib/discussion/viewer";
import {
  afterPublish,
  useExperienceSummary,
  useJournalExperiences,
} from "@/lib/experience/queries";
import { useExperienceSort } from "@/lib/experience/use-experience-sort";
import { formatCount } from "@/lib/format-count";
import type {
  ExperienceSort,
  PostDetail,
  SummaryWindow,
} from "@/lib/interaction/experience-schema";
import { btnPrimary, btnSecondary } from "@/lib/portal-ui";
import { queryKeys } from "@/lib/query-keys";
import { uniqueBy } from "@/lib/unique-by";
import { cn } from "@/lib/utils";

interface ExperienceAreaProps {
  journalId: string;
  journalTitle: string;
  /// 服务端读到的访客，水合后以当前用户查询为准
  viewer: ViewerSeed;
  /// 服务端的当前时间（发布日期写「今年 / 往年」）
  now: number;
}

const SMALL = "px-4 py-1.5 text-sm";

/// 经验列表的两种排序（简报 v0.15 §3.8）
const SORT_OPTIONS = [
  { value: "latest", label: "最新投稿" },
  { value: "helpful", label: "最有帮助" },
] as const satisfies readonly { value: ExperienceSort; label: string }[];

/// 期刊页经验区的客户端部分（简报 §3.13–§3.15、spec 6）：区块标题与「写投稿经验」、原地展开的表单、
/// 「同行经验」汇总、经验列表。汇总或列表在没有任何数据时取不到，整块降级为「同行经验暂不可用」：
/// 按钮隐藏、列表不渲染（简报 §3.21）
export function ExperienceArea({
  journalId,
  journalTitle,
  viewer: seed,
  now,
}: ExperienceAreaProps) {
  const viewer = useViewer(seed);
  const client = useQueryClient();
  const router = useRouter();
  const [window, setWindow] = useState<SummaryWindow>("3y");
  const [writing, setWriting] = useState(false);
  const writeButton = useRef<HTMLButtonElement | null>(null);
  const summary = useExperienceSummary(journalId, window);
  const { sort, setSort } = useExperienceSort();
  const list = useJournalExperiences(journalId, sort);

  /// 首屏的列表排序：整块降级只看它；换到另一种排序取不到时只在列表处提示
  const [firstSort] = useState(sort);
  // 整块降级只看首屏的两份（近 3 年汇总、首屏排序的列表）；切到「全部」或另一种排序取不到时只就地提示，
  // 按钮、工具行与切换控件照常
  const summaryFailed = summary.isError && !summary.data;
  const listFailed = list.isError && !list.data;
  const unavailable = (listFailed && sort === firstSort) || (window === "3y" && summaryFailed);
  const summaryState: SummaryState =
    unavailable || summaryFailed
      ? { kind: "unavailable" }
      : summary.data
        ? { kind: "ok", summary: summary.data }
        : { kind: "loading" };
  // 「最有帮助」翻页期间数字会变，同一条可能出现在两页里：只渲染先加载的那份（决策 D）
  const items = uniqueBy(
    list.data?.pages.flatMap((page) => page.items) ?? [],
    (experience) => experience.postId,
  );
  // 换排序失败时列表没有数据，工具行用上一次取到的总条数（不随排序变）
  const [knownTotal, setKnownTotal] = useState(0);
  const loadedTotal = list.data?.pages[0]?.total;
  if (loadedTotal !== undefined && loadedTotal !== knownTotal) {
    setKnownTotal(loadedTotal);
  }
  const total = loadedTotal ?? knownTotal;
  /// 换排序 = 从第一页重新取：先清掉目标排序已缓存的页，取回前列表留着当前的（`keepPreviousData`）
  const changeSort = (next: ExperienceSort) => {
    if (next === sort) {
      return;
    }
    client.removeQueries({ queryKey: queryKeys.experienceList(journalId, next), exact: true });
    setSort(next);
  };
  const listEmpty = list.data !== undefined && items.length === 0;

  const retry = () => {
    void summary.refetch();
    void list.refetch();
  };

  const published = (detail: PostDetail) => {
    setWriting(false);
    void afterPublish(client, journalId);
    toast.success("投稿经验已发布", {
      action: { label: "查看", onClick: () => router.push(`/posts/${detail.postId}`) },
    });
    requestAnimationFrame(() => writeButton.current?.focus());
  };

  const loginHref = authPageHref("/login", `/journals/${journalId}`);
  const write = (label: string, size: "sm" | "md") => {
    if (viewer.status === "anonymous") {
      return (
        <Link href={loginHref} className={cn(btnSecondary, size === "sm" && SMALL)}>
          登录后写投稿经验
        </Link>
      );
    }
    if (!viewer.emailVerified) {
      return (
        <button
          type="button"
          aria-disabled="true"
          className={cn(btnPrimary, size === "sm" && SMALL)}
        >
          <PenLine className="size-4" aria-hidden="true" />
          {label}
        </button>
      );
    }
    return (
      <button
        ref={size === "sm" ? writeButton : undefined}
        type="button"
        onClick={() => setWriting(true)}
        className={cn(btnPrimary, size === "sm" && SMALL)}
      >
        <PenLine className="size-4" aria-hidden="true" />
        {label}
      </button>
    );
  };

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <SectionEyebrow as="h2" id="experiences-title" className="mb-0">
          投稿经验 · 审稿周期
        </SectionEyebrow>
        {!writing && !unavailable && (
          <div className="flex flex-col items-end gap-1.5">
            {write("写投稿经验", "sm")}
            {viewer.status === "authenticated" && !viewer.emailVerified && (
              <span className="flex flex-wrap items-center justify-end gap-x-2 text-amber-700 text-xs">
                验证邮箱后才能写投稿经验
                <ResendVerificationButton label="重新发送验证邮件" />
              </span>
            )}
          </div>
        )}
      </div>

      {writing && (
        <ExperienceForm
          mode="create"
          journalId={journalId}
          journalTitle={journalTitle}
          onDone={published}
          onCancel={() => {
            setWriting(false);
            requestAnimationFrame(() => writeButton.current?.focus());
          }}
        />
      )}

      <ExperienceSummary
        state={summaryState}
        window={window}
        onWindowChange={setWindow}
        onRetry={retry}
      />

      {!unavailable && listEmpty && (
        <div className="flex flex-col items-center gap-2 rounded-lg border border-border-default border-dashed px-6 py-10 text-center">
          <p className="m-0 font-medium font-serif text-fg-1 text-xl">
            还没有人分享这本期刊的投稿经验
          </p>
          <p className="m-0 text-fg-2 text-sm">
            投过这本期刊？写下投稿、审稿和结果，帮后来的人选刊。
          </p>
          {!writing && <div className="mt-1.5">{write("写第一条投稿经验", "md")}</div>}
        </div>
      )}

      {!unavailable && total > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="m-0 text-fg-2 text-sm">{formatCount(total)} 条投稿经验</p>
          {total > 1 && <SortToggle value={sort} options={SORT_OPTIONS} onChange={changeSort} />}
        </div>
      )}

      {!unavailable && listFailed && (
        <div className="flex flex-col items-center gap-2 py-6">
          <p role="alert" className="m-0 text-center text-sm text-status-danger">
            没有加载成功，请稍后再试
          </p>
          <button
            type="button"
            onClick={() => void list.refetch()}
            className={cn(btnSecondary, SMALL)}
          >
            重试
          </button>
        </div>
      )}

      {!unavailable && items.length > 0 && (
        <ExperienceList
          hasMore={list.hasNextPage}
          loadingMore={list.isFetchingNextPage}
          moreFailed={list.isFetchNextPageError}
          onLoadMore={() => void list.fetchNextPage()}
          moreLabel="加载更多投稿经验"
        >
          {items.map((experience) => (
            <ExperienceRow
              key={experience.postId}
              variant="journal"
              experience={experience}
              now={now}
            />
          ))}
        </ExperienceList>
      )}
    </div>
  );
}
