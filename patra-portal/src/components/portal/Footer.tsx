import { ExternalLink } from "lucide-react";
import Image from "next/image";

const NAV_LINKS = [
  { label: "数据源", href: "#" },
  { label: "关于", href: "#about" },
  { label: "更新日志", href: "#" },
] as const;

export function Footer() {
  return (
    <footer
      data-section="footer"
      className="mt-16 border-t border-border-default bg-paper-50/60 py-7"
    >
      <div className="container mx-auto flex max-w-[1200px] flex-wrap items-center gap-x-8 gap-y-3 px-6 max-[880px]:flex-col max-[880px]:items-start">
        <a href="#top" className="inline-flex items-center gap-2 text-ink-900" aria-label="Patra">
          <Image src="/brand/patra-mark.svg" alt="" aria-hidden width={6} height={22} />
          <span className="font-serif text-lg">Patra</span>
        </a>

        <nav className="flex flex-1 items-center justify-center gap-7 text-sm max-[880px]:flex-none max-[880px]:justify-start max-[880px]:gap-5">
          {NAV_LINKS.map((link) => (
            <a key={link.label} href={link.href} className="text-clay-700 hover:text-clay-800">
              {link.label}
            </a>
          ))}
          <a
            href="https://github.com"
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1 text-clay-700 hover:text-clay-800"
          >
            GitHub
            <ExternalLink className="h-3.5 w-3.5" strokeWidth={1.5} aria-hidden />
          </a>
        </nav>

        <p className="font-mono text-2xs uppercase tracking-caps text-fg-3">
          V0.4 · 索引快照 17:42 UTC+8
        </p>
      </div>
    </footer>
  );
}
