import { Suspense } from "react";
import {
  PaperDiscussionCount,
  PaperSavedCount,
} from "@/components/portal/paper-detail/PaperReaderCounts";
import { fetchArticleStats } from "@/lib/analytics/insight";
import { formatCount } from "@/lib/format-count";
import { fetchPaperStats } from "@/lib/interaction/stats";

/// 文献页事实条下方的读者一行（简报 v0.18 §3.3）：「看过 · 读过全文 · 收藏 · 讨论」。两组来自两个服务，各在自己的
/// Suspense 里到达、各自插入，不画骨架；项之间的「·」由样式画（不靠前面有没有项），四项都没有时整行收起
export function PaperReaders({ paperId }: { paperId: string }) {
  return (
    <div className="mt-3.5 flex flex-wrap items-baseline text-sm text-fg-3 empty:hidden [&>[data-reader-count]~[data-reader-count]]:before:mx-2 [&>[data-reader-count]~[data-reader-count]]:before:text-ink-300 [&>[data-reader-count]~[data-reader-count]]:before:content-['·']">
      <Suspense fallback={null}>
        <AnalyticsReaderCounts paperId={paperId} />
      </Suspense>
      <Suspense fallback={null}>
        <InteractionReaderCounts paperId={paperId} />
      </Suspense>
    </div>
  );
}

/// 看过与读过全文（行为数据服务）：取不到时两项都不出现；为 0 的不写
export async function AnalyticsReaderCounts({ paperId }: { paperId: string }) {
  const map = await fetchArticleStats([paperId]);
  if (!map) {
    return null;
  }
  const stats = map.get(paperId);
  const viewers = stats?.viewerCount ?? 0;
  const readers = stats?.fulltextReaderCount ?? 0;
  return (
    <>
      {viewers > 0 && (
        <span
          data-reader-count
          title="看过的人数：同一个人只算一次（登录用户按账号、访客按设备），不含搜索引擎等爬虫；1 分钟内更新"
        >
          <span className="font-medium text-fg-1 tabular-nums">{formatCount(viewers)}</span> 人看过
        </span>
      )}
      {readers > 0 && (
        <span data-reader-count title="在全文区停留满 10 秒或下载了 PDF 的人数，同一个人只算一次">
          <span className="font-medium text-fg-1 tabular-nums">{formatCount(readers)}</span>{" "}
          人读过全文
        </span>
      )}
    </>
  );
}

/// 收藏与讨论（互动服务）：取不到时两项都不出现（v0.15 简报 §3.14）
export async function InteractionReaderCounts({ paperId }: { paperId: string }) {
  const stats = (await fetchPaperStats([paperId]))?.get(paperId);
  if (!stats) {
    return null;
  }
  return (
    <>
      <PaperSavedCount paperKey={paperId} collectorCount={stats.collectorCount} />
      <PaperDiscussionCount paperKey={paperId} discussionCount={stats.discussionCount} />
    </>
  );
}
