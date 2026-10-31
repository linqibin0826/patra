// patra-learn/src/app/routes/[slug]/page.tsx
// 路线页（画板 Route）：全部路线切换、路线说明与进度、按入门 / 基础 / 进阶 / 实战分段的篇目
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { RouteBands, RouteProgressCard } from "@/components/route-parts";
import { cx, meta, panelLabel, pill, wrap } from "@/components/ui";
import { getCatalog } from "@/lib/content/catalog";
import { buildRouteIndex } from "@/lib/content/indexes";
import { DIFFICULTIES } from "@/lib/content/types";
import { DIFFICULTY_LABELS } from "@/lib/site/labels";
import { routePage } from "@/lib/site/views";

export const dynamicParams = false;

export function generateStaticParams() {
  return buildRouteIndex(getCatalog()).map(({ slug }) => ({ slug }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const view = routePage(getCatalog(), (await params).slug);
  return view ? { title: view.title, description: view.summary } : {};
}

/// 分钟数写成「约 2 小时」或「约 40 分钟」
function duration(minutes: number) {
  return minutes >= 90 ? `约 ${Math.round(minutes / 60)} 小时` : `约 ${minutes} 分钟`;
}

export default async function RoutePage({ params }: { params: Promise<{ slug: string }> }) {
  const view = routePage(getCatalog(), (await params).slug);
  if (!view) notFound();
  const levels = DIFFICULTIES.filter((d) => view.levels[d] > 0)
    .map((d) => `${DIFFICULTY_LABELS[d]} ${view.levels[d]}`)
    .join(" · ");
  return (
    <main className={cx(wrap, "flex flex-col gap-9 pt-2 pb-24")}>
      <nav aria-label="全部路线" className="flex flex-wrap gap-2">
        {view.all.map((r) => (
          <Link
            key={r.slug}
            href={`/routes/${r.slug}`}
            aria-current={r.slug === view.slug ? "page" : undefined}
            className={pill(r.slug === view.slug)}
          >
            {r.title} <span className="text-ink-4 text-xs">{r.count}</span>
          </Link>
        ))}
      </nav>

      <section className="flex flex-wrap items-end gap-8">
        <div className="flex min-w-0 flex-[999_1_560px] flex-col gap-3">
          <span className={cx(panelLabel, "text-teal")}>学习路线 · 第 {view.index} 条</span>
          <h1 className="m-0 font-bold font-display text-[40px] text-ink leading-[1.25] max-sm:text-h1">
            {view.title}
          </h1>
          <p className="m-0 max-w-[54ch] text-[17px] text-ink-2">{view.summary}</p>
          <div className={cx(meta, "flex flex-wrap gap-4")}>
            <span>
              {view.count} 篇 · {duration(view.minutes)}
            </span>
            <span>{levels}</span>
          </div>
        </div>
        <RouteProgressCard bands={view.bands} />
      </section>

      <RouteBands bands={view.bands} />

      {view.next.length > 0 ? (
        <section className="box-border flex max-w-[880px] flex-wrap items-center gap-6 rounded-card border border-line bg-surface-soft px-6 py-5">
          <span className="flex-[1_1_220px] font-bold font-display text-h3 text-ink">
            读完这条，可以接着走
          </span>
          {view.next.map((r) => (
            <Link key={r.slug} href={`/routes/${r.slug}`} className={pill()}>
              {r.title} <span className="text-ink-4 text-xs">{r.count} 篇</span>
            </Link>
          ))}
        </section>
      ) : null}
    </main>
  );
}
