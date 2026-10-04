import { ArrowUpRight } from "lucide-react";
import Link from "next/link";
import { BrandMark } from "@/components/portal/BrandMark";
import { Venation } from "@/components/portal/Venation";
import { PORTAL_STATS } from "@/data/portal-stats";
import { CHANGELOG_URL, REPO_URL } from "@/lib/site";

const NAMED_SOURCES = ["PubMed", "Europe PMC", "Crossref"] as const;

const BROWSE_LINKS = [
  { label: "首页", href: "/" },
  { label: "文献", href: "/papers" },
  { label: "期刊", href: "/journals" },
] as const;

const COLUMN_TITLE = "font-mono text-2xs uppercase tracking-caps text-fg-inverse-3";
const COLUMN_LINK =
  "link-draw pb-px text-fg-inverse-2 transition-colors duration-200 hover:text-fg-inverse-1";

/// 版权页（Colophon）：墨色底的全站页脚。宣言 + 关于（#about，导航「关于」的落点）+ 浏览 / 数据源（#sources）/ 项目三列
/// + 版本与索引快照；背景是叶脉水印与巨型裁切字标（均为装饰，aria-hidden）。
export function Footer() {
  return (
    <footer
      data-section="footer"
      className="relative isolate mt-24 overflow-hidden bg-bg-inverse text-fg-inverse-2"
    >
      <Venation
        id="footer-leaf"
        tone="inverse"
        className="pointer-events-none absolute top-[-12%] right-[-6%] -z-10 h-[125%] w-auto rotate-[28deg] opacity-[0.13] max-md:right-[-30%] max-md:opacity-[0.08]"
      />

      <div className="mx-auto max-w-page px-gutter pt-24 pb-10 max-md:pt-16">
        <p className="font-serif text-display-2 leading-heading font-medium tracking-tight break-keep text-fg-inverse-1">
          医学知识，自古写在<span className="text-accent-on-inverse">叶</span>上。
        </p>

        <div className="mt-16 grid gap-x-10 gap-y-12 border-t border-border-inverse pt-12 max-md:mt-12 lg:grid-cols-12">
          <section id="about" aria-labelledby="footer-about-title" className="lg:col-span-5">
            <h2 id="footer-about-title" className={COLUMN_TITLE}>
              关于 Patra
            </h2>
            <p className="mt-4 max-w-[44ch] font-serif text-lg leading-relaxed text-pretty text-fg-inverse-2">
              Patra（पत्र）在梵语中意为「叶」。纸张普及之前，南亚的医典写在贝叶上，一叶一叶装订成册。
              Patra 把散落在 {PORTAL_STATS.sources}{" "}
              个来源的医学文献重新装订：统一检索、追溯出处，辅以速读。
            </p>
          </section>

          <nav
            aria-label="页脚导航"
            className="grid grid-cols-3 gap-8 text-sm max-sm:grid-cols-2 lg:col-span-6 lg:col-start-7"
          >
            <div>
              <h3 className={COLUMN_TITLE}>浏览</h3>
              <ul className="mt-4 flex flex-col gap-2.5">
                {BROWSE_LINKS.map((link) => (
                  <li key={link.href}>
                    <Link href={link.href} className={COLUMN_LINK}>
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
            <div>
              <h3 className={COLUMN_TITLE}>数据源</h3>
              <ul id="sources" aria-label="数据来源" className="mt-4 flex flex-col gap-2.5">
                {NAMED_SOURCES.map((source) => (
                  <li key={source} className="text-fg-inverse-2">
                    {source}
                  </li>
                ))}
                <li className="text-fg-inverse-3">
                  及另外 {PORTAL_STATS.sources - NAMED_SOURCES.length} 个来源
                </li>
              </ul>
            </div>
            <div>
              <h3 className={COLUMN_TITLE}>项目</h3>
              <ul className="mt-4 flex flex-col gap-2.5">
                <li>
                  <a href="#about" className={COLUMN_LINK}>
                    关于
                  </a>
                </li>
                <li>
                  <a href="#sources" className={COLUMN_LINK}>
                    数据源
                  </a>
                </li>
                <li>
                  <a
                    href={CHANGELOG_URL}
                    target="_blank"
                    rel="noreferrer"
                    className={`${COLUMN_LINK} inline-flex items-center gap-1`}
                  >
                    更新日志
                    <ArrowUpRight className="size-3.5" strokeWidth={1.5} aria-hidden />
                  </a>
                </li>
                <li>
                  <a
                    href={REPO_URL}
                    target="_blank"
                    rel="noreferrer"
                    className={`${COLUMN_LINK} inline-flex items-center gap-1`}
                  >
                    GitHub
                    <ArrowUpRight className="size-3.5" strokeWidth={1.5} aria-hidden />
                  </a>
                </li>
              </ul>
            </div>
          </nav>
        </div>

        <div className="mt-16 flex flex-wrap items-center justify-between gap-x-8 gap-y-3 border-t border-border-inverse pt-6 font-mono text-2xs tracking-caps text-fg-inverse-3 max-md:mt-12">
          <a
            href="#top"
            aria-label="Patra"
            className="inline-flex items-center gap-2 text-fg-inverse-1 transition-opacity hover:opacity-80"
          >
            <BrandMark width={6} />
            <span className="font-serif text-xl tracking-tight normal-case">Patra</span>
          </a>
          <p className="uppercase">© 2026 Patra · 医学文献门户</p>
          <p className="uppercase">V0.4 · 索引快照 17:42 UTC+8</p>
        </div>
      </div>

      <div
        aria-hidden
        className="pointer-events-none mx-auto -mb-[0.16em] max-w-page px-gutter font-serif text-[clamp(112px,26vw,380px)] leading-[0.8] font-medium tracking-display text-fg-inverse-1/[0.06] select-none"
      >
        Patra
      </div>
    </footer>
  );
}
