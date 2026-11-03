import { TrackedArticle } from "@/components/analytics/TrackedArticle";
import { TrackedList } from "@/components/analytics/TrackedList";
import { AlsoReadCard } from "@/components/portal/paper-detail/AlsoReadCard";
import { type AlsoReadGroup, groupAlsoRead } from "@/lib/analytics/also-read";
import { fetchAlsoRead } from "@/lib/analytics/insight";
import type { AlsoReadItem } from "@/lib/analytics/insight-schema";
import { logRequestError } from "@/lib/auth/gateway-response";
import { fetchPublicationCards } from "@/lib/portal-api/publications";
import type { Paper } from "@/types/portal";

const COPY: Record<AlsoReadGroup["kind"], { title: string; note: string }> = {
  "co-read": { title: "看过这篇的人还看了", note: "常和这篇在同一天被同一批读者读过的文献" },
  topic: { title: "同主题文献", note: "主要主题词与这篇相同；不够时再看同一本期刊" },
};

/// 文献页底部的整宽区块（简报 v0.18 §3.6）：一起读的一组、同主题补位一组，各自一个标题。推荐由行为数据服务排好，
/// 卡片由 catalog 批量配；任一取不到、或一篇都没有时整块不出现。调用方包在 Suspense 里
export async function AlsoReadSection({ paperId }: { paperId: string }) {
  const items = await fetchAlsoRead(paperId);
  if (!items || items.length === 0) {
    return null;
  }
  let papers: Paper[];
  try {
    papers = await fetchPublicationCards(items.map((item) => item.articleId));
  } catch (error) {
    logRequestError("/patra-catalog/portal/publications/batch", error);
    return null;
  }
  const byId = new Map(papers.map((paper) => [paper.id, paper]));
  const groups = groupAlsoRead(
    items.filter((item) => byId.has(item.articleId)),
    paperId,
  );
  if (groups.length === 0) {
    return null;
  }
  return (
    <div className="mx-auto mt-24 flex max-w-page flex-col gap-14 border-t border-border-default px-gutter pt-12 max-md:mt-16">
      {groups.map((group) => (
        <section key={group.kind} aria-labelledby={`also-read-${group.kind}`}>
          <div className="mb-6 flex flex-wrap items-baseline justify-between gap-x-6 gap-y-2">
            <h2
              id={`also-read-${group.kind}`}
              className="font-serif text-3xl leading-heading font-medium tracking-tight text-ink-900"
            >
              {COPY[group.kind].title}
            </h2>
            <p className="text-sm text-fg-3">{COPY[group.kind].note}</p>
          </div>
          <TrackedList listType={group.kind === "co-read" ? "related" : "related_topic"}>
            <ul className="grid grid-cols-3 gap-x-10 gap-y-8 max-lg:grid-cols-2 max-md:grid-cols-1">
              {group.items.map((item: AlsoReadItem, i) => {
                const paper = byId.get(item.articleId) as Paper;
                return (
                  <li key={item.articleId}>
                    <TrackedArticle articleId={item.articleId} position={i + 1}>
                      <AlsoReadCard paper={paper} item={item} />
                    </TrackedArticle>
                  </li>
                );
              })}
            </ul>
          </TrackedList>
        </section>
      ))}
    </div>
  );
}
