import Image from "next/image";

export function Footer() {
  return (
    <footer
      data-section="footer"
      className="mt-16 border-t border-border-default bg-paper-50/60 py-10"
    >
      <div className="container mx-auto flex max-w-[1200px] flex-col gap-3 px-6 text-fg-3 max-[880px]:items-start">
        <div className="flex items-center gap-3">
          <Image src="/brand/patra-monogram.svg" alt="" aria-hidden width={24} height={24} />
          <span className="font-serif text-lg text-ink-900">Patra</span>
        </div>
        <p className="max-w-prose text-sm leading-relaxed">
          医学文献门户。汇集 PubMed、Europe PMC、Crossref 等 10
          个来源的学术文献，提供统一检索、出处追溯与 AI 辅助速读。
        </p>
        <p className="font-mono text-2xs uppercase tracking-caps text-fg-4">
          © 2026 · v0.4 portal foundation
        </p>
      </div>
    </footer>
  );
}
