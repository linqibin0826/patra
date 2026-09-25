"use client";

import { Home, RefreshCw } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { btnPrimary, btnSecondary } from "@/lib/portal-ui";

interface ErrorStateProps {
  onRetry: () => void;
  context?: string;
}

/// 全站复用 error 兜底屏（client，带重试）。只展示受控文案，绝不渲染 error.message / 堆栈。
export function ErrorState({ onRetry, context = "加载" }: ErrorStateProps) {
  return (
    <div className="flex min-h-[calc(100vh-56px)] items-center justify-center px-6 py-[72px]">
      <div className="flex w-full max-w-[540px] flex-col items-center text-center">
        <div className="mb-7 inline-flex items-center justify-center gap-[clamp(16px,4vw,28px)]">
          <span className="shrink-0 border-r border-border-default pr-[clamp(16px,4vw,28px)] opacity-85">
            <Image src="/brand/patra-mark.svg" alt="" aria-hidden width={25} height={88} />
          </span>
          <span className="font-serif font-medium leading-[0.9] tracking-tight text-rust-500 tabular-nums text-[clamp(64px,13vw,120px)]">
            500
          </span>
        </div>
        <span className="mb-3.5 inline-flex items-center gap-2 whitespace-nowrap font-mono text-xs uppercase tracking-caps text-rust-500">
          <span className="h-1.5 w-1.5 rounded-full bg-rust-500" /> 服务异常 · 兜底页
        </span>
        <h1 className="mb-3.5 font-serif font-medium leading-[1.18] tracking-tight text-fg-1 text-balance text-[clamp(24px,4vw,32px)]">
          这一页没能加载出来
        </h1>
        <p className="mb-7 max-w-[440px] font-serif text-lg leading-relaxed text-fg-2 text-pretty">
          {context}时出了点问题 ——
          可能是上游来源短暂不可用，或一次性的网络抖动。这通常重试就能恢复。
        </p>
        <div className="flex flex-wrap justify-center gap-2.5">
          <button type="button" onClick={onRetry} className={btnPrimary}>
            <RefreshCw size={15} /> 重试
          </button>
          <Link href="/" className={btnSecondary}>
            <Home size={15} /> 返回首页
          </Link>
        </div>
      </div>
    </div>
  );
}
