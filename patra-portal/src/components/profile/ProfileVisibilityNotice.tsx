import { Eye } from "lucide-react";
import Link from "next/link";
import { Suspense } from "react";
import { ResendVerificationButton } from "@/components/portal/ResendVerificationButton";
import type { ProfileVisibility } from "@/lib/identity/profile-schema";

interface ProfileVisibilityNoticeProps {
  audience: ProfileVisibility;
  emailVerified: boolean;
}

/// 本人视角的可见范围提示条（简报 §3.13）：主页不是公开时出现在头部之上，不能关。两个原因同时成立时
/// 显示未验证那一条。
export function ProfileVisibilityNotice({ audience, emailVerified }: ProfileVisibilityNoticeProps) {
  if (audience === "PUBLIC") {
    return null;
  }
  const text = !emailVerified
    ? "目前只有你能看到这个主页：验证邮箱后，别人才能看到。"
    : audience === "SELF"
      ? "目前只有你能看到这个主页。"
      : "目前只有登录用户能看到这个主页。";
  return (
    <div role="note" className="border-b border-status-info/20 bg-status-info-bg">
      <div className="mx-auto flex min-h-11 max-w-page items-center gap-2.5 px-gutter py-2.5 text-sm leading-5 text-fg-1">
        <Eye className="size-4 shrink-0 text-status-info" aria-hidden="true" />
        <span className="min-w-0 flex-1">{text}</span>
        {emailVerified ? (
          <Link
            href="/settings/privacy"
            className="shrink-0 text-sm font-medium text-link transition-colors hover:text-link-hover"
          >
            更改可见性
          </Link>
        ) : (
          <Suspense>
            <ResendVerificationButton label="重新发送验证邮件" className="shrink-0" />
          </Suspense>
        )}
      </div>
    </div>
  );
}
