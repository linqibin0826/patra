"use client";

import { BellOff } from "lucide-react";
import { useRouter } from "next/navigation";
import { useTransition } from "react";
import { SETTINGS_MESSAGES as M } from "@/lib/notification/messages";
import { btnSecondary } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 通知服务不可用时设置 › 通知里唯一的一张卡片（简报 §3.5「取不到」）：说明 + 重试。重试重新渲染这一页（重新读
/// 偏好），进行中按钮不可点。其余分区照常（导航在 layout 里）。
export function NotificationSettingsUnavailable() {
  const router = useRouter();
  const [retrying, startRetry] = useTransition();
  return (
    <section className="flex flex-col items-center gap-3 rounded-xl border border-border-default bg-bg-paper px-6 py-12 text-center">
      <BellOff className="size-10 text-fg-3" strokeWidth={1.25} aria-hidden="true" />
      <h2 className="font-serif text-xl leading-6.5 font-medium text-fg-1">{M.unavailableTitle}</h2>
      <p className="text-sm leading-5 text-fg-3">{M.unavailableNote}</p>
      <button
        type="button"
        aria-disabled={retrying || undefined}
        onClick={() => {
          if (!retrying) startRetry(() => router.refresh());
        }}
        className={cn(btnSecondary, "mt-1 px-4 py-1.5 text-sm")}
      >
        {M.retry}
      </button>
    </section>
  );
}
