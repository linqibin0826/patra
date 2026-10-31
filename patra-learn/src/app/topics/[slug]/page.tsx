// patra-learn/src/app/topics/[slug]/page.tsx
// 主题页（画板 Theme）：按出处版本排的时间轴，被取代的决策划掉并注明谁取代了它。
// 全由个人环境条目组成的主题也有页面，只是不进主题列表与首页（简报 4.3）
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { LiveMasteryChip, ProgressBar, ReadCount } from "@/components/progress-ui";
import {
  cardClass,
  cx,
  LevelBadge,
  meta,
  panelLabel,
  TitleText,
  TypeBadge,
  wrap,
} from "@/components/ui";
import { getCatalog } from "@/lib/content/catalog";
import { topicPage } from "@/lib/site/views";

export const dynamicParams = false;

export function generateStaticParams() {
  return getCatalog().topics.map(({ slug }) => ({ slug }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const view = topicPage(getCatalog(), (await params).slug);
  return view ? { title: view.title, description: view.summary } : {};
}

export default async function TopicPage({ params }: { params: Promise<{ slug: string }> }) {
  const view = topicPage(getCatalog(), (await params).slug);
  if (!view) notFound();
  const slugs = view.groups.flatMap((g) => g.items.map((i) => i.card.slug));
  return (
    <main className={cx(wrap, "flex flex-wrap items-start gap-14 pt-2 pb-24")}>
      <div className="flex min-w-0 max-w-[820px] flex-[999_1_600px] flex-col gap-8">
        <header className="flex flex-col gap-3">
          <span className={cx(panelLabel, "text-teal")}>
            {view.personalOnly ? "个人环境主题" : "正在长的主题"}
          </span>
          <h1 className="m-0 font-bold font-display text-[40px] text-ink leading-[1.25] max-sm:text-h1">
            {view.title}
          </h1>
          <p className="m-0 max-w-[56ch] text-[17px] text-ink-2">{view.summary}</p>
          <div className={cx(meta, "flex flex-wrap gap-4")}>
            <span>{view.count} 篇</span>
            {view.from && view.to ? (
              <span>{view.from === view.to ? view.from : `${view.from} → ${view.to}`}</span>
            ) : null}
            {view.supersededCount > 0 ? <span>{view.supersededCount} 条决策已被取代</span> : null}
          </div>
        </header>

        <ol className="m-0 flex list-none flex-col p-0">
          {view.groups.map((group) => (
            <li
              key={group.version}
              className="grid grid-cols-[112px_22px_minmax(0,1fr)] gap-x-4.5 max-sm:grid-cols-[22px_minmax(0,1fr)]"
            >
              <div className="pt-0.5 text-right max-sm:hidden">
                <div className="font-display font-semibold text-[22px] text-ink tabular-nums leading-[1.2]">
                  {group.version}
                </div>
                {group.issues.length > 0 ? (
                  <div className={meta}>{group.issues.join(" · ")}</div>
                ) : null}
              </div>
              <div className="flex flex-col items-center">
                <i className="mt-2 box-border block size-3.5 flex-none rounded-full border-2 border-teal bg-surface" />
                <b className="mt-[3px] min-h-2.5 w-0.5 flex-1 bg-line" />
              </div>
              <div className="flex flex-col gap-2.5 pb-8">
                <span className={cx(meta, "sm:hidden")}>
                  {group.version}
                  {group.issues.length > 0 ? ` · ${group.issues.join(" · ")}` : ""}
                </span>
                {group.items.map(({ card, supersededBy, replaces }) => (
                  <Link
                    key={card.slug}
                    href={`/entries/${card.slug}`}
                    className={cx(
                      cardClass(true),
                      "flex flex-col gap-1.5 px-4.5 py-3.5",
                      supersededBy && "border-dashed bg-surface-soft shadow-none",
                    )}
                  >
                    <span className="flex flex-wrap items-center gap-2">
                      <TypeBadge type={card.type} />
                      <LevelBadge difficulty={card.difficulty} />
                      {supersededBy ? (
                        <span className="inline-flex h-[26px] items-center rounded-full border border-amber px-2.5 font-semibold font-ui text-amber text-tiny">
                          已被取代
                        </span>
                      ) : null}
                      {card.personalEnv ? <span className={meta}>个人环境</span> : null}
                      <span className="flex-1" />
                      <LiveMasteryChip slug={card.slug} />
                    </span>
                    <span
                      className={cx(
                        "font-bold font-display text-h3 leading-[1.45]",
                        supersededBy
                          ? "text-ink-3 line-through decoration-ink-5 decoration-1"
                          : "text-ink",
                      )}
                    >
                      <TitleText text={card.title} />
                    </span>
                    <span className="font-ui text-ink-3 text-sm leading-[1.6]">{card.summary}</span>
                    {supersededBy ? (
                      <span className={meta}>后来改成了「{supersededBy}」</span>
                    ) : null}
                    {replaces.length > 0 ? (
                      <span className={cx(meta, "font-semibold text-decision")}>
                        ↺ 取代了「{replaces.join("」「")}」
                      </span>
                    ) : null}
                  </Link>
                ))}
              </div>
            </li>
          ))}
          <li className="grid grid-cols-[112px_22px_minmax(0,1fr)] gap-x-4.5 max-sm:grid-cols-[22px_minmax(0,1fr)]">
            <span className="max-sm:hidden" />
            <div className="flex flex-col items-center">
              <i className="mt-2 box-border block size-3 flex-none rounded-full border-2 border-ink-5 border-dashed bg-surface" />
            </div>
            <span className={cx(meta, "pt-1")}>下一次碰到相关的事，新条目会长在这里。</span>
          </li>
        </ol>
      </div>

      <aside className="flex min-w-0 max-w-aside flex-[1_1_240px] flex-col gap-5">
        <div className={cx(cardClass(), "flex flex-col gap-2.5 p-4.5")}>
          <div className="font-bold font-display text-base text-ink">这个主题里你的进度</div>
          <span className="font-ui text-ink-3 text-ui">
            <ReadCount slugs={slugs} empty="还没标过掌握度" />
            ，共 {slugs.length} 篇
          </span>
          <ProgressBar slugs={slugs} height="h-2" />
        </div>
        {view.others.length > 0 ? (
          <div className="rounded-card border border-line bg-surface-soft p-4.5">
            <div className="mb-2.5 font-bold font-display text-base text-ink">其他正在长的主题</div>
            <div className="flex flex-col gap-2 font-ui text-sm">
              {view.others.map((t) => (
                <Link key={t.slug} href={`/topics/${t.slug}`}>
                  {t.title} · {t.count} 篇
                </Link>
              ))}
            </div>
          </div>
        ) : null}
      </aside>
    </main>
  );
}
