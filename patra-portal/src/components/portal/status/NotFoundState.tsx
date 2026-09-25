import { Home } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { btnPrimary } from "@/lib/portal-ui";

type NotFoundKind = "journal" | "paper" | "page";

const LINES: Record<NotFoundKind, string> = {
  journal: "这本期刊不在 Patra 的索引里 —— 可能 ID 有误，或它尚未被收录。",
  paper: "这篇文献不在 Patra 的索引里 —— 可能 ID 有误，或它尚未被采集。",
  page: "这个地址在 Patra 里找不到对应内容。链接可能已失效，或从未存在。",
};

/// 全站复用 404 状态屏（RSC）。暖纸编辑风，不暴露堆栈。
export function NotFoundState({ kind = "page" }: { kind?: NotFoundKind }) {
  return (
    <div className="flex min-h-[calc(100vh-56px)] items-center justify-center px-6 py-[72px]">
      <div className="flex w-full max-w-[540px] flex-col items-center text-center">
        <div className="mb-7 inline-flex items-center justify-center gap-[clamp(16px,4vw,28px)]">
          <span className="shrink-0 border-r border-border-default pr-[clamp(16px,4vw,28px)] opacity-90">
            <Image src="/brand/patra-mark.svg" alt="" aria-hidden width={25} height={88} />
          </span>
          <span className="font-serif font-medium leading-[0.9] tracking-[-0.04em] text-ink-200 tabular-nums text-[clamp(64px,13vw,120px)]">
            404
          </span>
        </div>
        <span className="mb-3.5 inline-flex items-center gap-2 whitespace-nowrap font-mono text-xs uppercase tracking-[0.1em] text-clay-700">
          <span className="h-1.5 w-1.5 rounded-full bg-clay-500" /> HTTP 404 · not found
        </span>
        <h1 className="mb-3.5 font-serif font-medium leading-[1.18] tracking-tight text-fg-1 text-balance text-[clamp(24px,4vw,32px)]">
          没有这一页
        </h1>
        <p className="mb-7 max-w-[440px] font-serif text-lg leading-relaxed text-fg-2 text-pretty">
          {LINES[kind]}
        </p>
        <div className="flex flex-wrap justify-center gap-2.5">
          <Link href="/" className={btnPrimary}>
            <Home size={15} /> 返回首页
          </Link>
        </div>
      </div>
    </div>
  );
}
