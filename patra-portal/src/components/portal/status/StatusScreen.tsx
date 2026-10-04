import type { ReactNode } from "react";
import { Venation } from "@/components/portal/Venation";
import { cn } from "@/lib/utils";

interface StatusScreenProps {
  /** 状态码（如 "404"）：数字里的 0 画成一片描线入场的叶子——缺的那一页 / 一叶 */
  code: string;
  /** 状态码之上的等宽小标（含色点） */
  badge: string;
  tone: "clay" | "rust";
  title: string;
  body: ReactNode;
  actions: ReactNode;
}

const TONE = {
  clay: { badge: "text-clay-700", dot: "bg-clay-500" },
  rust: { badge: "text-rust-500", dot: "bg-rust-500" },
} as const;

/// 全站状态屏外壳（404 / 500 共用，RSC 可用）：巨号衬线状态码（0 为叶）+ 标题 + 说明 + 操作，右侧叶脉水印。
export function StatusScreen({ code, badge, tone, title, body, actions }: StatusScreenProps) {
  return (
    <div className="relative isolate flex min-h-[calc(100dvh-56px)] items-center overflow-hidden py-20">
      <Venation className="pointer-events-none absolute top-1/2 right-[-8%] -z-10 h-[110%] w-auto -translate-y-1/2 rotate-[22deg] opacity-[0.1] max-md:right-[-40%]" />
      <div className="mx-auto w-full max-w-page px-gutter">
        <p
          className={cn(
            "anim-fade inline-flex items-center gap-2 font-mono text-xs uppercase tracking-caps",
            TONE[tone].badge,
          )}
        >
          <span aria-hidden className={cn("size-1.5 rounded-full", TONE[tone].dot)} />
          {badge}
        </p>
        <p
          aria-hidden
          className="mt-4 flex items-baseline font-serif text-[clamp(112px,22vw,288px)] leading-[0.82] font-medium tracking-display text-ink-900 tabular-nums"
        >
          {[...code].map((ch, i) =>
            ch === "0" ? (
              <Venation
                // 状态码字符序列静态不变，位置即身份
                // biome-ignore lint/suspicious/noArrayIndexKey: 同上
                key={i}
                animated
                className="mx-[0.03em] inline-block h-[0.76em] w-auto -rotate-[10deg]"
              />
            ) : (
              // biome-ignore lint/suspicious/noArrayIndexKey: 同上
              <span key={i} className="anim-rise [--delay:80ms]">
                {ch}
              </span>
            ),
          )}
        </p>
        <h1 className="anim-rise mt-8 font-serif text-display-3 leading-heading font-medium tracking-tight text-balance text-fg-1 [--delay:200ms]">
          {title}
        </h1>
        <p className="anim-rise mt-4 max-w-[34em] font-serif text-xl leading-relaxed text-pretty text-fg-2 [--delay:280ms]">
          {body}
        </p>
        <div className="anim-rise mt-9 flex flex-wrap gap-3 [--delay:360ms]">{actions}</div>
      </div>
    </div>
  );
}
