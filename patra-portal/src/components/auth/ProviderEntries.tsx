"use client";

import { useEffect, useState } from "react";
import { ProviderMark } from "@/components/auth/ProviderMark";
import { PROVIDERS, type Provider, providerName } from "@/lib/auth/oauth-providers";
import { cn } from "@/lib/utils";

interface ProviderEntriesProps {
  page: "login" | "register";
  /// 成功后回到哪里，已经过 `safeNextPath`
  next: string;
}

/// 第三方入口（画板 Auth、Providers）：三个按钮加「或用邮箱登录」分隔线，放在邮箱表单上面。是 `<a>` 不是
/// `next/link`：预取一次就会写一次 Cookie、请求一次 identity。点了之后按钮显示「正在前往」，整页跳走；从第三方按
/// 后退时页面可能从往返缓存恢复，`pageshow` 时复位，否则按钮一直不可点。
export function ProviderEntries({ page, next }: ProviderEntriesProps) {
  const [busy, setBusy] = useState<Provider | null>(null);
  useEffect(() => {
    const reset = () => setBusy(null);
    window.addEventListener("pageshow", reset);
    return () => window.removeEventListener("pageshow", reset);
  }, []);
  const verb = page === "register" ? "注册" : "登录";
  const query = new URLSearchParams({ from: page, next });
  return (
    <>
      <div className="flex flex-col gap-2.5">
        {PROVIDERS.map((provider) => (
          <a
            key={provider}
            href={`/api/auth/oauth/${provider}/start?${query}`}
            aria-disabled={busy !== null || undefined}
            onClick={(event) => {
              if (busy !== null) {
                event.preventDefault();
                return;
              }
              setBusy(provider);
            }}
            className={cn(
              "grid min-h-11.5 w-full grid-cols-[20px_minmax(0,1fr)_20px] items-center gap-2.5 rounded-full border border-border-strong bg-bg-elevated px-4 py-3 text-center text-base leading-5 font-medium text-fg-1 no-underline transition-[border-color,box-shadow] duration-200 max-[880px]:min-h-12",
              busy === null && "hover:border-border-hover hover:shadow-xs",
              busy === provider && "cursor-progress text-fg-2",
              busy !== null && busy !== provider && "cursor-not-allowed text-fg-3",
            )}
          >
            <ProviderMark provider={provider} />
            <span>
              {busy === provider
                ? `正在前往 ${providerName(provider)}…`
                : `用 ${providerName(provider)} 账号${verb}`}
            </span>
            <span aria-hidden="true" />
          </a>
        ))}
      </div>
      <div className="flex items-center gap-3 text-sm leading-5 whitespace-nowrap text-fg-3 before:h-px before:flex-1 before:bg-border-default after:h-px after:flex-1 after:bg-border-default">
        或用邮箱{verb}
      </div>
    </>
  );
}
