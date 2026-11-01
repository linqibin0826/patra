// patra-learn/src/app/entries/[slug]/page.tsx
// 条目页（画板 Concept / Debug / Decision / Pitfall）。构建时为每篇条目预渲染（用 @mdx-js/mdx 编译正文，
// 与内容校验同一套 remark 解析），运行时不读 content/。读者进度相关的部分在 entry-parts.tsx，批注在 annotations.tsx
import { evaluate } from "@mdx-js/mdx";
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import * as runtime from "react/jsx-runtime";
import remarkGfm from "remark-gfm";
import { AnnotatableBody, EntryAnnotations, EntryNotesCard } from "@/components/annotations";
import { EntryMastery, Prerequisites, RouteSidebar } from "@/components/entry-parts";
import { LiveMasteryChip } from "@/components/progress-ui";
import { proseComponents } from "@/components/prose";
import {
  cx,
  LevelBadge,
  meta,
  panelLabel,
  plainTitle,
  TitleText,
  TypeBadge,
  wrap,
} from "@/components/ui";
import { getCatalog } from "@/lib/content/catalog";
import { GITHUB_REPO_URL } from "@/lib/content/code-refs";
import { findEntry } from "@/lib/content/indexes";
import { entryPage, PERSONAL_TAG } from "@/lib/site/views";
import { mdxComponents } from "@/mdx-components";
import { Diagnose, type DiagnoseProps } from "@/teaching/diagnose";

export const dynamicParams = false;

export function generateStaticParams() {
  return getCatalog().entries.map(({ slug }) => ({ slug }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}): Promise<Metadata> {
  const entry = findEntry(getCatalog(), (await params).slug);
  return entry ? { title: plainTitle(entry.meta.title), description: entry.meta.summary } : {};
}

/// 编译条目正文；失败时带上 slug，方便在构建日志里定位
async function compileBody(slug: string, body: string) {
  try {
    return (await evaluate(body, { ...runtime, remarkPlugins: [remarkGfm] })).default;
  } catch (error) {
    throw new Error(`条目 ${slug} 的 MDX 编译失败：${(error as Error).message}`);
  }
}

function SupersededBanner({
  target,
}: {
  target: { slug: string; title: string; version?: string };
}) {
  return (
    <div className="flex items-start gap-3.5 rounded-card border border-[#e6d4ae] bg-amber-bg px-4.5 py-4">
      <svg
        aria-hidden="true"
        width="20"
        height="20"
        viewBox="0 0 24 24"
        fill="none"
        stroke="#8F5500"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="mt-[3px] flex-none"
      >
        <path d="M3 12a9 9 0 1 0 3-6.7" />
        <path d="M3 4v5h5" />
      </svg>
      <div className="flex flex-col gap-1">
        <span className="font-semibold font-ui text-amber">这条决策已被取代</span>
        <span className="font-ui text-ink-2 text-ui leading-[1.6]">
          {target.version ? `${target.version} ` : ""}改成了{" "}
          <Link
            href={`/entries/${target.slug}`}
            className="underline decoration-link-line underline-offset-[3px]"
          >
            <TitleText text={target.title} />
          </Link>
          。这篇留着，是为了看清当时为什么这么选。
        </span>
      </div>
    </div>
  );
}

function PersonalNote() {
  return (
    <div className="flex items-start gap-3 rounded-card border border-line border-dashed bg-surface-soft px-4 py-3.5">
      <svg
        aria-hidden="true"
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="#5F6B63"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="mt-[3px] flex-none"
      >
        <rect x="3" y="4" width="18" height="12" rx="2" />
        <path d="M8 20h8" />
        <path d="M12 16v4" />
      </svg>
      <span className="font-ui text-ink-3 text-ui leading-[1.6]">
        <b className="text-ink">个人环境</b>
        　这一篇和作者本机的环境有关，你的电脑多半不会这样。它不进学习路线，也不会出现在首页推荐，只在
        <Link
          href={`/tags/${PERSONAL_TAG}`}
          className="underline decoration-link-line underline-offset-[3px]"
        >
          「个人环境」标签
        </Link>
        下能找到。排查思路是通用的。
      </span>
    </div>
  );
}

export default async function EntryPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const catalog = getCatalog();
  const entry = findEntry(catalog, slug);
  const view = entryPage(catalog, slug);
  if (!entry || !view) notFound();
  const { card } = view;
  const Body = await compileBody(slug, entry.body);
  const prerequisites = view.prerequisites.map(({ slug: s, title, meta: m }) => ({
    slug: s,
    title,
    meta: m,
  }));
  const components = {
    ...mdxComponents,
    ...proseComponents,
    Diagnose: (props: DiagnoseProps) => <Diagnose {...props} prerequisites={prerequisites} />,
  };
  const [firstSource] = view.sources;

  return (
    <main className={cx(wrap, "flex flex-wrap items-start justify-center gap-14 pt-2 pb-24")}>
      <EntryAnnotations slug={slug}>
        <article className="flex min-w-0 max-w-article flex-[999_1_560px] flex-col gap-9 [overflow-wrap:anywhere]">
          {view.supersededBy ? <SupersededBanner target={view.supersededBy} /> : null}
          {card.personalEnv ? <PersonalNote /> : null}

          <header className="flex flex-col gap-3.5">
            {view.crumb ? (
              <nav aria-label="位置" className={cx(meta, "flex flex-wrap gap-1.5")}>
                <Link href={view.crumb.href}>{view.crumb.label}</Link>
                {view.mainRoute ? (
                  <>
                    <span>›</span>
                    <span>
                      第 {view.mainRoute.position} 篇，共 {view.mainRoute.total} 篇
                    </span>
                  </>
                ) : null}
              </nav>
            ) : null}
            <div className="flex flex-wrap items-center gap-2.5">
              <TypeBadge type={card.type} />
              <LevelBadge difficulty={card.difficulty} />
              {card.superseded ? (
                <span className="inline-flex h-[26px] items-center rounded-full border border-amber px-2.5 font-semibold font-ui text-amber text-tiny">
                  已被取代
                </span>
              ) : (
                <LiveMasteryChip slug={slug} />
              )}
              <span className={meta}>{card.tagLabels.join(" · ")}</span>
            </div>
            <h1 className="m-0 font-bold font-display text-display text-ink leading-[1.3] max-sm:text-h1">
              <TitleText text={card.title} />
            </h1>
            <div className={meta}>
              {firstSource ? `来自 ${firstSource.version} · ` : ""}读完约 {card.minutes} 分钟
            </div>
          </header>

          {view.prerequisites.length > 0 ? (
            <Prerequisites items={view.prerequisites} indirect={view.indirect} />
          ) : null}

          <AnnotatableBody>
            <div className="text-base leading-[1.85] [&>:first-child]:mt-0 [&>:last-child]:mb-0 [&>:not(p,h2,h3,ul,ol,blockquote,pre,div)]:my-7">
              <Body components={components} />
            </div>
          </AnnotatableBody>

          <EntryMastery slug={slug} />

          {view.prev || view.next ? (
            <footer className="flex flex-wrap justify-between gap-4 border-line border-t pt-5">
              {view.prev ? (
                <Link href={`/entries/${view.prev.slug}`} className="flex flex-col gap-0.5">
                  <span className={meta}>← 上一篇</span>
                  <span className="font-bold font-display text-ink">
                    <TitleText text={view.prev.title} />
                  </span>
                </Link>
              ) : (
                <span />
              )}
              {view.next ? (
                <Link
                  href={`/entries/${view.next.slug}`}
                  className="flex flex-col gap-0.5 text-right"
                >
                  <span className={meta}>下一篇 →</span>
                  <span className="font-bold font-display text-ink">
                    <TitleText text={view.next.title} />
                  </span>
                </Link>
              ) : null}
            </footer>
          ) : null}
        </article>

        <aside className="flex min-w-0 max-w-aside flex-[1_1_240px] flex-col gap-5">
          <EntryNotesCard />
          {view.mainRoute ? <RouteSidebar route={view.mainRoute} current={slug} /> : null}
          {card.routes.length > 1 ? (
            <div className={cx(meta, "px-1")}>
              也在{" "}
              {card.routes.slice(1).map((r, i) => (
                <span key={r.slug}>
                  {i > 0 ? "、" : ""}
                  <Link href={`/routes/${r.slug}`}>{r.title}</Link> 第 {r.position} 篇
                </span>
              ))}
            </div>
          ) : null}
          {view.topics.map((topic) => (
            <div key={topic.slug} className="rounded-card border border-line bg-surface-soft p-4.5">
              <div className="mb-1.5 font-bold font-display text-base text-ink">同一主题里的</div>
              <div className="flex flex-col gap-2 font-ui text-sm leading-[1.6]">
                <Link href={`/topics/${topic.slug}`} className="font-semibold text-ink">
                  {topic.title} · {topic.entries.length} 篇
                </Link>
                {topic.entries.map((e) =>
                  e.slug === slug ? (
                    <span key={e.slug} className="font-semibold text-ink">
                      ● <TitleText text={e.title} />
                      （本页）
                    </span>
                  ) : (
                    <Link key={e.slug} href={`/entries/${e.slug}`}>
                      <TitleText text={e.title} />
                      {e.personalEnv ? <span className="text-ink-4"> · 个人环境</span> : null}
                    </Link>
                  ),
                )}
              </div>
            </div>
          ))}
          {view.followUps.length > 0 ? (
            <div className="rounded-card border border-line bg-surface-soft p-4.5">
              <div className="mb-1.5 font-bold font-display text-base text-ink">读完可以接着读</div>
              <div className="flex flex-col gap-2 font-ui text-sm leading-[1.6]">
                {view.followUps.map((e) => (
                  <Link key={e.slug} href={`/entries/${e.slug}`}>
                    <TitleText text={e.title} />
                  </Link>
                ))}
              </div>
            </div>
          ) : null}
          {view.sources.length > 0 || view.commits.length > 0 ? (
            <div className={cx(meta, "flex flex-col gap-0.5 px-1")}>
              {view.sources.length > 0 ? (
                <span>
                  <span className={cx(panelLabel, "text-ink-3")}>出处 </span>
                  {view.sources.map((s, i) => (
                    <span key={`${s.version}-${s.issue ?? ""}-${s.pr ?? ""}`}>
                      {i > 0 ? "；" : ""}
                      {[s.version, s.issue].filter(Boolean).join(" · ")}
                      {s.pr ? (
                        <>
                          {" · "}
                          <a href={`${GITHUB_REPO_URL}/pull/${s.pr}`}>PR #{s.pr}</a>
                        </>
                      ) : null}
                    </span>
                  ))}
                </span>
              ) : null}
              {view.commits.length > 0 ? <span>代码钉在提交 {view.commits.join("、")}</span> : null}
            </div>
          ) : null}
        </aside>
      </EntryAnnotations>
    </main>
  );
}
