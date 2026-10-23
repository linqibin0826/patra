"use client";

import { useQueryClient } from "@tanstack/react-query";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { RESEND_MESSAGES, resendWaitMessage } from "@/lib/auth/messages";
import { authPageHref, deletionPendingHref } from "@/lib/auth/next-path";
import { resendVerificationEmail } from "@/lib/identity/verification-actions";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";
import { useVerificationResendStore } from "@/store/verification-resend";

/// 限流时没给等待秒数，按 60 秒算
const DEFAULT_WAIT_MS = 60_000;

interface ResendVerificationButtonProps {
  /// 按钮文字，默认「重新发送」；主页提示条上是「重新发送验证邮件」
  label?: string;
  className?: string;
}

/// 「重新发送」验证邮件与它的倒计时（简报 §3.4 状态表）：可发送 → 发送中… → 已发送 · N 秒后可重发
/// （或限流文案）→ 回到可发送。倒计时每秒更新但不播报；结束时在 `role="status"` 里播报一次。
/// 冷却存在共享的 store 里，横幅和主页提示条看到的是同一个倒计时。
export function ResendVerificationButton({
  label = "重新发送",
  className,
}: ResendVerificationButtonProps) {
  const router = useRouter();
  const pathname = usePathname();
  const query = useSearchParams().toString();
  const queryClient = useQueryClient();
  const { availableAt, limited, startCooldown, markLimited } = useVerificationResendStore();
  const [now, setNow] = useState(() => Date.now());
  const [sending, setSending] = useState(false);
  const [announcement, setAnnouncement] = useState("");
  const waiting = availableAt !== null && availableAt > now;
  const wasWaiting = useRef(waiting);

  useEffect(() => {
    if (availableAt === null || availableAt <= Date.now()) {
      return;
    }
    setNow(Date.now());
    const timer = setInterval(() => setNow(Date.now()), 1_000);
    return () => clearInterval(timer);
  }, [availableAt]);

  useEffect(() => {
    if (wasWaiting.current && !waiting) {
      setAnnouncement(RESEND_MESSAGES.ready);
    }
    if (waiting) {
      setAnnouncement("");
    }
    wasWaiting.current = waiting;
  }, [waiting]);

  async function resend() {
    setSending(true);
    const result = await resendVerificationEmail().catch(
      () => ({ ok: false, reason: "unavailable" }) as const,
    );
    setSending(false);
    const current = query ? `${pathname}?${query}` : pathname;
    if (result.ok) {
      startCooldown(Date.parse(result.nextResendAt) || Date.now() + DEFAULT_WAIT_MS);
      toast.success(RESEND_MESSAGES.sent);
      return;
    }
    switch (result.reason) {
      case "throttled":
        markLimited(
          Date.now() +
            (result.retryAfterSeconds === null
              ? DEFAULT_WAIT_MS
              : result.retryAfterSeconds * 1_000),
        );
        return;
      case "already-verified":
        void queryClient.invalidateQueries({ queryKey: queryKeys.currentUser });
        toast.success(RESEND_MESSAGES.verified);
        return;
      case "pending-deletion":
        router.replace(deletionPendingHref(current));
        return;
      case "signed-out":
        queryClient.setQueryData(queryKeys.currentUser, null);
        router.replace(authPageHref("/login", current));
        return;
      case "unavailable":
        toast.error(RESEND_MESSAGES.failed);
        return;
    }
  }

  return (
    <>
      {waiting ? (
        <span className={cn("text-sm leading-5 text-fg-3 tabular-nums", className)}>
          {resendWaitMessage(Math.ceil((availableAt - now) / 1_000), limited)}
        </span>
      ) : (
        <button
          type="button"
          disabled={sending}
          onClick={() => void resend()}
          className={cn(
            "text-sm leading-5 font-medium text-clay-700 transition-colors hover:text-clay-800 disabled:cursor-not-allowed disabled:opacity-55",
            className,
          )}
        >
          {sending ? RESEND_MESSAGES.sending : label}
        </button>
      )}
      <span role="status" className="sr-only">
        {announcement}
      </span>
    </>
  );
}
