"use client";

import { Home, RefreshCw } from "lucide-react";
import Link from "next/link";
import { StatusScreen } from "@/components/portal/status/StatusScreen";
import { btnPrimary, btnSecondary } from "@/lib/portal-ui";

interface ErrorStateProps {
  onRetry: () => void;
  context?: string;
}

/// 全站复用 error 兜底屏（client，带重试）。只展示受控文案，绝不渲染 error.message / 堆栈。
export function ErrorState({ onRetry, context = "加载" }: ErrorStateProps) {
  return (
    <StatusScreen
      code="500"
      badge="服务异常 · 兜底页"
      tone="rust"
      title="这一页没能加载出来"
      body={`${context}时出了点问题 —— 可能是上游来源短暂不可用，或一次性的网络抖动。这通常重试就能恢复。`}
      actions={
        <>
          <button type="button" onClick={onRetry} className={btnPrimary}>
            <RefreshCw size={15} /> 重试
          </button>
          <Link href="/" className={btnSecondary}>
            <Home size={15} /> 返回首页
          </Link>
        </>
      }
    />
  );
}
