// patra-learn/src/app/tags/[slug]/page.tsx
// 标签页（画板 Tag）：左侧全部标签与计数，「个人环境」单独隔开；右侧按类型筛。
// 普通标签不列个人环境条目，个人环境条目只在 /tags/personal-env 下（决策 E）
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { FilteredEntries } from "@/components/entry-list";
import { cx, meta, panelLabel, wrap } from "@/components/ui";
import { getCatalog } from "@/lib/content/catalog";
import { PERSONAL_TAG, tagList, tagPage } from "@/lib/site/views";

export const dynamicParams = false;

export function generateStaticParams() {
  return [...getCatalog().tags.map(({ slug }) => ({ slug })), { slug: PERSONAL_TAG }];
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const view = tagPage(getCatalog(), (await params).slug);
  return view ? { title: `标签：${view.label}` } : {};
}

function tagItem(on: boolean) {
  return cx(
    "flex h-10 items-center justify-between rounded-[10px] px-3 font-ui text-ui transition-[background-color] duration-160 ease-out hover:bg-surface",
    on
      ? "bg-surface font-semibold text-ink shadow-[inset_0_0_0_1px_var(--color-line)]"
      : "text-ink-2",
  );
}

export default async function TagPage({ params }: { params: Promise<{ slug: string }> }) {
  const catalog = getCatalog();
  const view = tagPage(catalog, (await params).slug);
  if (!view) notFound();
  const { tags, personalCount } = tagList(catalog);
  return (
    <main className={cx(wrap, "flex flex-wrap items-start gap-12 pt-2 pb-24")}>
      <nav aria-label="全部标签" className="flex max-w-60 flex-[1_1_220px] flex-col gap-0.5">
        <span className={cx(panelLabel, "px-3 pb-2 text-ink-3")}>全部标签</span>
        {tags.map((t) => (
          <Link
            key={t.slug}
            href={`/tags/${t.slug}`}
            aria-current={t.slug === view.slug ? "page" : undefined}
            className={tagItem(t.slug === view.slug)}
          >
            <span>{t.label}</span>
            <span className="font-display text-ink-4 text-meta tabular-nums">{t.count}</span>
          </Link>
        ))}
        {personalCount > 0 || view.personal ? (
          <Link
            href={`/tags/${PERSONAL_TAG}`}
            aria-current={view.personal ? "page" : undefined}
            className={cx(
              tagItem(view.personal),
              "mt-2.5 h-[46px] rounded-t-none border-line border-t border-dashed pt-1",
            )}
          >
            <span>个人环境</span>
            <span className="font-display text-ink-4 text-meta tabular-nums">{personalCount}</span>
          </Link>
        ) : null}
      </nav>

      <div className="flex min-w-0 flex-[999_1_600px] flex-col gap-6">
        <header className="flex flex-col gap-2.5">
          <span className={cx(panelLabel, "text-teal")}>标签</span>
          <h1 className="m-0 font-bold font-display text-[36px] text-ink leading-[1.25] max-sm:text-h1">
            {view.label}
          </h1>
          <span className={meta}>
            {view.personal
              ? `${view.cards.length} 篇 · 不进路线，不进首页推荐，不出现在搜索里`
              : `${view.cards.length} 篇`}
          </span>
        </header>
        {view.personal ? (
          <div className="flex items-start gap-3 rounded-card border border-line border-dashed bg-surface-soft px-4 py-3.5 font-ui text-ink-3 text-ui leading-[1.6]">
            这些排查只跟作者本机的代理、防火墙或部署机有关，你的电脑多半碰不到。它们不进学习路线、不进首页推荐、搜索里也不显示，只在这里能找到。排查的思路可以借用。
          </div>
        ) : null}
        <FilteredEntries cards={view.cards} />
      </div>
    </main>
  );
}
