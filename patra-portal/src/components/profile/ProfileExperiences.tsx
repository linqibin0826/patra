"use client";

import Link from "next/link";
import { ExperienceList } from "@/components/experience/ExperienceList";
import { ExperienceRow } from "@/components/experience/ExperienceRow";
import { InteractionUnavailable } from "@/components/library/InteractionUnavailable";
import { useUserExperiences } from "@/lib/experience/queries";
import { useRefreshOnIdentityChange } from "@/lib/experience/use-identity-refresh";
import { btnSecondary } from "@/lib/portal-ui";

/// 主页「投稿经验」标签的内容（简报 §3.17）：条目列表（第一行是期刊名），按投稿日期倒序，每页 20 条 +「加载更多」。
/// 本人视角上方一行说明，匿名条目标「匿名 · 仅你可见」（interaction 只对本人返回匿名的）；取不到时两种视角一样是
/// 错误块与重试
export function ProfileExperiences({
  userId,
  self,
  now,
}: {
  userId: string;
  self: boolean;
  now: number;
}) {
  const query = useUserExperiences(userId);
  useRefreshOnIdentityChange();
  if (query.isError && !query.data) {
    return (
      <InteractionUnavailable title="投稿经验暂时打不开" onRetry={() => void query.refetch()} />
    );
  }
  if (!query.data) {
    return (
      <div aria-hidden="true" className="flex motion-safe:animate-pulse flex-col gap-4 py-5">
        <div className="h-5 w-2/3 rounded bg-paper-200" />
        <div className="h-4 w-1/2 rounded bg-paper-200" />
        <div className="h-5 w-3/5 rounded bg-paper-200" />
      </div>
    );
  }
  const items = query.data.pages.flatMap((page) => page.items);
  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center gap-3 py-16 text-center">
        <p className="font-serif text-fg-1 text-xl">
          {self ? "还没有写过投稿经验" : "还没有公开的投稿经验"}
        </p>
        {self && (
          <>
            <p className="text-fg-2 text-sm">在期刊页写下你的投稿经历，帮后来的人选刊。</p>
            <Link href="/journals" className={btnSecondary}>
              去期刊列表
            </Link>
          </>
        )}
      </div>
    );
  }
  return (
    <div className="flex max-w-190 flex-col gap-4">
      {self && (
        <p className="m-0 text-fg-3 text-sm">这里列出你写过的投稿经验，匿名发布的只有你看得到。</p>
      )}
      <ExperienceList
        hasMore={query.hasNextPage}
        loadingMore={query.isFetchingNextPage}
        moreFailed={query.isFetchNextPageError}
        onLoadMore={() => void query.fetchNextPage()}
        moreLabel="加载更多"
      >
        {items.map((item) => (
          <ExperienceRow key={item.experience.postId} variant="profile" item={item} now={now} />
        ))}
      </ExperienceList>
    </div>
  );
}
