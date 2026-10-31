// patra-learn/src/app/page.tsx
// 首页：最小占位（列出路线与条目），页面由 PAP-162 按画板实现
import Link from "next/link";
import { getCatalog } from "@/lib/content/catalog";
import { buildRouteIndex } from "@/lib/content/indexes";

export default function HomePage() {
  const routes = buildRouteIndex(getCatalog());
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-6 py-10">
      <h1 className="text-3xl font-black tracking-wide">Patra 学习站</h1>
      {routes.length === 0 ? <p className="text-sm text-ink-4">内容建设中。</p> : null}
      {routes.map((route) => (
        <section key={route.slug} className="flex flex-col gap-2">
          <h2 className="text-xl font-bold">{route.title}</h2>
          <ol className="list-decimal pl-6">
            {route.entries.map((entry) => (
              <li key={entry.slug}>
                <Link href={`/entries/${entry.slug}`}>{entry.meta.title}</Link>
              </li>
            ))}
          </ol>
        </section>
      ))}
    </main>
  );
}
