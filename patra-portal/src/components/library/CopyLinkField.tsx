"use client";

import { useRef } from "react";
import { CopyButton } from "@/components/library/CopyButton";

/// 只读链接框 + 「复制链接」（公开开关卡、公开收藏夹页的分享浮层）。地址由服务端按门户公开地址拼好
export function CopyLinkField({ url }: { url: string }) {
  const field = useRef<HTMLInputElement>(null);
  return (
    <div className="flex items-center gap-2">
      <input
        ref={field}
        readOnly
        aria-label="链接"
        value={url}
        onFocus={(event) => event.target.select()}
        className="h-9 min-w-0 flex-1 truncate rounded-md border border-border-default bg-bg-elevated px-3 font-mono text-xs text-fg-2 outline-none"
      />
      <CopyButton label="复制链接" text={url} target={() => field.current} />
    </div>
  );
}
