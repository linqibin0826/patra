"use client";

import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useId, useState } from "react";
import { CopyLinkField } from "@/components/library/CopyLinkField";
import { ResendVerificationButton } from "@/components/portal/ResendVerificationButton";
import { setVisibility } from "@/lib/interaction/collection-actions";
import type { Collection } from "@/lib/interaction/schema";
import { invalidateLibrary } from "@/lib/library/cache";
import { useLoginRedirect } from "@/lib/library/use-login-redirect";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnText, SWITCH_INPUT } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

interface PublicToggleCardProps {
  collection: Collection;
  emailVerified: boolean;
  email: string;
  /// 本人主页现在对谁可见；读不到时为 null，按「所有人」说明
  profileVisibility: "PUBLIC" | "SIGNED_IN" | "SELF" | null;
  /// 公开链接（按门户公开地址拼好）
  publicLink: string;
}

/// 开关旁的状态（简报 §3.13）：`saving` 设置中；`rejected` 被拒或失败，按原因写说明
type Status = { kind: "idle" } | { kind: "saving" } | { kind: "error"; reason: Rejection };
type Rejection = "email-unverified" | "account-restricted" | "unavailable";

/// 公开开关卡（简报 §3.13）：设为公开要求邮箱已验证、账号正常，未验证时提前禁用；打开不乐观（可能被拒），
/// 关闭乐观、不确认（可逆）。公开后按主页可见性给出三种可见范围说明，并给出链接框
export function PublicToggleCard({
  collection,
  emailVerified,
  email,
  profileVisibility,
  publicLink,
}: PublicToggleCardProps) {
  const client = useQueryClient();
  const redirectToLogin = useLoginRedirect();
  const [visibility, setLocal] = useState(collection.visibility);
  const [status, setStatus] = useState<Status>({ kind: "idle" });
  const [verifiedHere, setVerifiedHere] = useState(emailVerified);
  const id = useId();
  const isPublic = visibility === "PUBLIC";
  const unverified =
    (!verifiedHere || (status.kind === "error" && status.reason === "email-unverified")) &&
    !isPublic;

  async function change(next: "PUBLIC" | "PRIVATE") {
    const previous = visibility;
    if (next === "PRIVATE") {
      setLocal("PRIVATE");
    }
    setStatus(next === "PUBLIC" ? { kind: "saving" } : { kind: "idle" });
    const result = await orUnavailable(setVisibility(collection.collectionId, next));
    if (result.ok) {
      setLocal(result.collection.visibility);
      setStatus({ kind: "idle" });
      void invalidateLibrary(client);
      return;
    }
    if (result.reason === "signed-out") {
      redirectToLogin();
      return;
    }
    setLocal(previous);
    if (result.reason === "email-unverified") {
      setVerifiedHere(false);
    }
    setStatus({
      kind: "error",
      reason:
        result.reason === "email-unverified" || result.reason === "account-restricted"
          ? result.reason
          : "unavailable",
    });
  }

  const saving = status.kind === "saving";
  return (
    <div className="flex flex-col gap-3 rounded-md bg-paper-100 px-4 py-3.5">
      <div className="flex items-start gap-3">
        <input
          id={id}
          type="checkbox"
          role="switch"
          aria-checked={isPublic}
          className={cn(SWITCH_INPUT, "mt-0.5")}
          checked={isPublic}
          disabled={saving || unverified}
          onChange={(event) => void change(event.target.checked ? "PUBLIC" : "PRIVATE")}
        />
        <div className="flex min-w-0 flex-col gap-1">
          <label htmlFor={id} className="text-sm font-medium text-fg-1">
            公开
          </label>
          <Explanation
            status={status}
            isPublic={isPublic}
            unverified={unverified}
            email={email}
            profileVisibility={profileVisibility}
          />
        </div>
      </div>
      {isPublic && <CopyLinkField url={publicLink} />}
    </div>
  );
}

function Explanation({
  status,
  isPublic,
  unverified,
  email,
  profileVisibility,
}: {
  status: Status;
  isPublic: boolean;
  unverified: boolean;
  email: string;
  profileVisibility: PublicToggleCardProps["profileVisibility"];
}) {
  if (status.kind === "saving") {
    return <p className="text-sm text-fg-3">设置中…</p>;
  }
  if (unverified) {
    return (
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-status-warning-ink">
        <span>验证邮箱后才能公开收藏夹。验证邮件已发到 {email}。</span>
        <ResendVerificationButton label="重新发送验证邮件" />
      </div>
    );
  }
  if (status.kind === "error") {
    return (
      <p className="text-sm text-status-danger">
        {status.reason === "account-restricted"
          ? "账号状态异常，暂时不能公开收藏夹。"
          : "没有设置成功，请稍后再试。"}
      </p>
    );
  }
  if (!isPublic) {
    return <p className="text-sm text-fg-3">别人可以通过链接或你的主页看到这个收藏夹。</p>;
  }
  if (profileVisibility === "SIGNED_IN" || profileVisibility === "SELF") {
    return (
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-status-warning-ink">
        <span>
          {profileVisibility === "SIGNED_IN"
            ? "你的主页设为仅登录用户可见，所以这个收藏夹也只有登录用户能看到。"
            : "你的主页设为仅自己可见，所以别人现在看不到这个收藏夹。"}
        </span>
        <Link href="/settings/privacy" className={cn(btnText, "text-sm")}>
          更改主页可见性
        </Link>
      </div>
    );
  }
  return (
    <p className="text-sm text-fg-2">
      所有人都能看到，包括没有登录的访客；也会出现在你主页的「收藏夹」里。
    </p>
  );
}
