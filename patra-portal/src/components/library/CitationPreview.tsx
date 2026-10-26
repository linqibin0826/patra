"use client";

import type { Ref } from "react";
import { btnText } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

interface CitationPreviewProps {
  /// 已拼好的每一条引用（复制引用对话框带序号，分享面板只有正文）
  lines: string[];
  state: "loading" | "error" | "ready";
  onRetry: () => void;
  /// 最大高度等（对话框 360px，分享面板 6 行）
  className?: string;
  /// 预览框本身：剪贴板降级时要选中它的文字
  ref?: Ref<HTMLDivElement>;
}

/// 引用预览框（简报 §3.4、§3.5）：衬线小字、可选中、超出滚动；加载两行骨架；失败 `引用暂时生成不了` + `重试`。
/// 分享面板与复制引用对话框共用
export function CitationPreview({ lines, state, onRetry, className, ref }: CitationPreviewProps) {
  return (
    <div
      ref={ref}
      className={cn(
        "overflow-y-auto rounded-md bg-paper-100 px-4 py-3 font-serif text-sm leading-6 text-fg-1 select-text",
        className,
      )}
    >
      {state === "error" ? (
        <p className="flex items-center gap-3 font-sans text-fg-2">
          引用暂时生成不了
          <button type="button" className={cn(btnText, "text-sm")} onClick={onRetry}>
            重试
          </button>
        </p>
      ) : state === "ready" ? (
        lines.map((line) => (
          <p key={line} className="m-0 [overflow-wrap:anywhere]">
            {line}
          </p>
        ))
      ) : (
        <div aria-hidden="true" className="flex flex-col gap-2">
          <div className="shimmer h-4 w-full rounded-sm" />
          <div className="shimmer h-4 w-2/3 rounded-sm" />
        </div>
      )}
    </div>
  );
}
