// patra-learn/src/app/routes/page.tsx
// 全部路线：与首页同一组路线卡
import type { Metadata } from "next";
import { RouteCardGrid } from "@/components/home";
import { cx, meta, panelLabel, wrap } from "@/components/ui";
import { getCatalog } from "@/lib/content/catalog";
import { routeCards } from "@/lib/site/views";

export const metadata: Metadata = { title: "学习路线" };

export default function RoutesPage() {
  const routes = routeCards(getCatalog());
  return (
    <main className={cx(wrap, "flex flex-col gap-6 pt-2 pb-24")}>
      <header className="flex flex-col gap-2.5">
        <span className={cx(panelLabel, "text-teal")}>学习路线</span>
        <h1 className="m-0 font-bold font-display text-h1 text-ink leading-[1.3]">
          {routes.length} 条路线，每条从入门排到实战
        </h1>
        <span className={meta}>一篇可以出现在几条路线里；进度只记在这个浏览器里</span>
      </header>
      <RouteCardGrid routes={routes} />
    </main>
  );
}
