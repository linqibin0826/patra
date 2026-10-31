// patra-learn/src/app/page.tsx
// 首页（画板 Main）：第一屏今天读这一条 / 从这里开始、学习路线、正在长的主题、标签、最近写的
import Link from "next/link";
import { RecentCards, RouteCardGrid, TodayHero, TopicCardGrid } from "@/components/home";
import { cx, pill, SectionHead, wrap } from "@/components/ui";
import { getCatalog } from "@/lib/content/catalog";
import { homeView, PERSONAL_TAG } from "@/lib/site/views";

export default function HomePage() {
  const view = homeView(getCatalog());
  return (
    <main className={cx(wrap, "flex flex-col gap-14 pt-3 pb-22")}>
      <TodayHero
        pool={view.reviewPool}
        starter={view.starter}
        total={view.total}
        routes={view.routes}
      />

      <section id="routes" className="flex scroll-mt-6 flex-col gap-4.5">
        <SectionHead title="学习路线" note="每条从入门排到实战，一篇可以出现在几条路线里" />
        {view.routes.length > 0 ? (
          <RouteCardGrid routes={view.routes} />
        ) : (
          <p className="text-ink-4">内容建设中。</p>
        )}
      </section>

      {view.topics.length > 0 ? (
        <section className="flex flex-col gap-4.5">
          <SectionHead
            title="正在长的主题"
            note={
              <>
                同一件事在不同版本里又碰到，条目挂在同一个主题下 ·{" "}
                <Link href="/topics">全部主题 →</Link>
              </>
            }
          />
          <TopicCardGrid topics={view.topics} />
        </section>
      ) : null}

      <section className="flex flex-wrap items-start gap-10">
        <div className="flex flex-[1_1_340px] flex-col gap-4">
          <SectionHead title="标签" />
          <div className="flex flex-wrap gap-2">
            {view.tags.tags
              .filter((t) => t.count > 0)
              .map((t) => (
                <Link key={t.slug} href={`/tags/${t.slug}`} className={pill()}>
                  {t.label} <span className="text-ink-4 text-xs">{t.count}</span>
                </Link>
              ))}
            {view.tags.personalCount > 0 ? (
              <Link href={`/tags/${PERSONAL_TAG}`} className={pill()}>
                个人环境 <span className="text-ink-4 text-xs">{view.tags.personalCount}</span>
              </Link>
            ) : null}
          </div>
        </div>
        <div className="flex min-w-0 flex-[999_1_600px] flex-col gap-4">
          <SectionHead title="最近写的" note={<Link href="/search">全部 {view.total} 篇 →</Link>} />
          <RecentCards cards={view.recent} />
        </div>
      </section>
    </main>
  );
}
