import { ArrowRight } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { TrackedArticle } from "@/components/analytics/TrackedArticle";
import { TrackedList } from "@/components/analytics/TrackedList";
import { LibraryStatusProvider } from "@/components/library/LibraryStatusProvider";
import { PaperCard } from "@/components/portal/PaperCard";
import { SectionEyebrow } from "@/components/portal/SectionEyebrow";
import { type ArticleStatsMap, fetchArticleStats, fetchHotReads } from "@/lib/analytics/insight";
import type { ListType } from "@/lib/analytics/types";
import { fetchPaperStats, type PaperStatsMap } from "@/lib/interaction/stats";
import { fetchFeed, fetchPublicationCards } from "@/lib/portal-api/publications";
import type { FeedTab, Paper } from "@/types/portal";
import { ExploreFeedEmpty } from "./empty";
import { FeedNote } from "./FeedNote";
import { ExploreFeedTabs } from "./tabs";

/// 头条右侧并列的条目数
const SIDE_COUNT = 3;

const LIST_TYPE: Record<FeedTab, ListType> = {
  recent: "feed_latest",
  cited: "feed_cited",
  hot: "feed_hot",
};

/// 文献流里的一张卡片：序号（热读里是名次；补位卡片没有序号，换成「最新收录」）
interface FeedEntry {
  paper: Paper;
  index: number | null;
  filler: boolean;
}

/// 一个标签的内容：卡片，以及热读标签的说明句数据
type FeedContent =
  | { kind: "error" }
  | { kind: "ok"; entries: FeedEntry[]; note?: { ranked: number; fillers: number } };

/// 最近更新、高被引：catalog 的文献流，序号就是位置
async function loadFeed(tab: "recent" | "cited"): Promise<FeedContent> {
  try {
    const page = await fetchFeed(tab);
    return {
      kind: "ok",
      entries: page.items.map((paper, i) => ({ paper, index: i + 1, filler: false })),
    };
  } catch {
    return { kind: "error" };
  }
}

/// 热读（简报 v0.18 §3.5）：行为数据服务给排好的 ID（上榜在前、最新收录补位在后），卡片由 catalog 批量配；catalog
/// 没回的跳过，说明句的篇数按实际显示的算
async function loadHot(): Promise<FeedContent> {
  const hot = await fetchHotReads();
  if (!hot) {
    return { kind: "error" };
  }
  let papers: Paper[];
  try {
    papers = await fetchPublicationCards(hot.items.map((item) => item.articleId));
  } catch {
    return { kind: "error" };
  }
  const byId = new Map(papers.map((paper) => [paper.id, paper]));
  // 名次按实际显示的重新编号：catalog 去掉一篇上榜的时不断号，也与说明句的篇数一致
  let rank = 0;
  const entries = hot.items.flatMap((item): FeedEntry[] => {
    const paper = byId.get(item.articleId);
    if (!paper) {
      return [];
    }
    if (item.source === "RANKED") {
      rank += 1;
      return [{ paper, index: rank, filler: false }];
    }
    return [{ paper, index: null, filler: true }];
  });
  const fillers = entries.filter((entry) => entry.filler).length;
  return { kind: "ok", entries, note: { ranked: entries.length - fillers, fillers } };
}

export async function ExploreFeed({ tab }: { tab: FeedTab }) {
  const content = tab === "hot" ? await loadHot() : await loadFeed(tab);
  const tabs = <ExploreFeedTabs currentTab={tab} />;
  if (content.kind === "error") {
    return (
      <FeedSection tabs={tabs}>
        <ExploreFeedEmpty reason="error" />
      </FeedSection>
    );
  }
  if (content.entries.length === 0) {
    return (
      <FeedSection tabs={tabs}>
        <ExploreFeedEmpty reason="empty" />
      </FeedSection>
    );
  }
  const keys = content.entries.map((entry) => entry.paper.id);
  // 收藏与讨论（互动服务）、看过（行为数据服务）一屏一批、并行取：取不到就没有那几项，文献流照常
  const [stats, insight] = await Promise.all([fetchPaperStats(keys), fetchArticleStats(keys)]);
  return (
    <FeedSection tabs={tabs}>
      {content.note && <FeedNote {...content.note} />}
      <LibraryStatusProvider keys={keys}>
        {/* 每个标签是一个列表实例：切标签时组件位置不变，用 key 换新的 list_id 与去重集合 */}
        <TrackedList key={tab} listType={LIST_TYPE[tab]}>
          <FeedLayout entries={content.entries} stats={stats} insight={insight} />
        </TrackedList>
      </LibraryStatusProvider>
    </FeedSection>
  );
}

/// 编辑排版：第 1 篇为头条（左 7 栏），2–4 篇并列右 5 栏，其余进入两栏编号列表。热读的补位从哪一格开始都可以，不画
/// 分隔线，靠每张卡片自己的「最新收录」
function FeedLayout({
  entries,
  stats,
  insight,
}: {
  entries: FeedEntry[];
  stats: PaperStatsMap | null;
  insight: ArticleStatsMap | null;
}) {
  const card = (entry: FeedEntry, position: number, variant?: "lead") => (
    <TrackedArticle articleId={entry.paper.id} position={position}>
      <PaperCard
        paper={entry.paper}
        variant={variant}
        index={entry.index ?? undefined}
        filler={entry.filler}
        stats={stats?.get(entry.paper.id) ?? null}
        views={insight ? (insight.get(entry.paper.id)?.viewerCount ?? 0) : null}
      />
    </TrackedArticle>
  );
  const [first, ...rest] = entries;
  const side = rest.slice(0, SIDE_COUNT);
  const more = rest.slice(SIDE_COUNT);
  return (
    <>
      <div className="grid grid-cols-1 lg:grid-cols-12 lg:gap-x-12">
        {first && <div className="max-lg:pb-8 lg:col-span-7">{card(first, 1, "lead")}</div>}
        <ol className="flex flex-col lg:col-span-5">
          {side.map((entry, i) => (
            <li key={entry.paper.id} className="flex-1">
              {card(entry, i + 2)}
            </li>
          ))}
        </ol>
      </div>

      {/* 窄屏只露前 8 篇（头条 + 并列 3 篇 + 列表前 4 篇），其余交给「查看更多文献」，避免首页过长 */}
      {more.length > 0 && (
        <ol className="mt-4 grid grid-cols-2 gap-x-12 max-md:grid-cols-1">
          {more.map((entry, i) => (
            <li key={entry.paper.id} className="reveal max-md:[&:nth-child(n+5)]:hidden">
              {card(entry, i + 2 + SIDE_COUNT)}
            </li>
          ))}
        </ol>
      )}

      <div className="mt-14 flex justify-center">
        <Link
          href="/papers"
          className="group/more inline-flex h-12 items-center gap-2 rounded-full border border-ink-900 px-7 text-sm font-semibold text-ink-900 transition-colors duration-300 hover:bg-ink-900 hover:text-paper-50"
        >
          查看更多文献
          <ArrowRight
            className="size-4 motion-safe:transition-transform duration-300 ease-out-expo motion-safe:group-hover/more:translate-x-1"
            strokeWidth={1.75}
            aria-hidden
          />
        </Link>
      </div>
    </>
  );
}

/// 区块外壳：编号眉题 + 标题 + 说明，右侧（窄屏下方）放 tab。骨架屏复用同一外壳，避免布局跳动。
export function FeedSection({ children, tabs }: { children: ReactNode; tabs: ReactNode }) {
  return (
    <section data-section="explore-feed" className="mx-auto max-w-page px-gutter">
      <div className="mb-10 flex items-end justify-between gap-x-10 gap-y-6 border-t border-border-default pt-[clamp(64px,8vw,112px)] max-md:flex-col max-md:items-start">
        <div>
          <SectionEyebrow index="03" className="mb-5">
            文献流
          </SectionEyebrow>
          <h2 className="font-serif text-display-3 leading-heading font-medium tracking-tight text-ink-900">
            值得读一读的文献
          </h2>
          <p className="mt-4 max-w-[40em] text-sm leading-relaxed break-keep text-fg-3">
            最近入库、高被引与近期读的人多的文献。AI
            速读即将上线，上线后也只作线索，不能替代阅读原文。
          </p>
        </div>
        <div className="shrink-0">{tabs}</div>
      </div>
      {children}
    </section>
  );
}
