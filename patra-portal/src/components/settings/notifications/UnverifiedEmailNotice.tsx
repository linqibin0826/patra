import { MailWarning } from "lucide-react";
import { Suspense } from "react";
import { ResendVerificationButton } from "@/components/portal/ResendVerificationButton";
import { SETTINGS_MESSAGES as M } from "@/lib/notification/messages";

/// 邮箱未验证时设置 › 通知顶部的警示条（简报 §3.5 第 1 条）：验证之前不发提醒邮件，后面跟「重新发送验证邮件」。
/// 版式同隐私页的未验证提示；通知服务取不到时也照常显示（它只看 `/auth/me`）。
export function UnverifiedEmailNotice() {
  return (
    <div className="flex items-start gap-2.5 rounded-lg bg-status-warning-bg px-3.5 py-3 text-base leading-5 text-fg-1">
      <MailWarning className="mt-0.5 size-4 shrink-0 text-status-warning-ink" aria-hidden="true" />
      <span>
        {M.unverified}
        <Suspense>
          <ResendVerificationButton label={M.resend} className="ml-1 text-base" />
        </Suspense>
      </span>
    </div>
  );
}
