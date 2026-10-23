"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { FormAlert } from "@/components/auth/FormAlert";
import { logout } from "@/lib/auth/actions";
import { DELETION_PENDING_MESSAGES, TOAST_MESSAGES } from "@/lib/auth/messages";
import { useCommonFailureRedirect } from "@/lib/auth/use-common-failure-redirect";
import { cancelDeletion } from "@/lib/identity/account-actions";
import { btnBlock, btnPrimary, btnText } from "@/lib/portal-ui";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";

/// 取消注销页的操作区（简报 §3.16）：主按钮恢复账号，成功后写入恢复后的用户、回到 `next`；下面是
/// 「退出登录」。这一页没有别的出口。`next` 已经过 `safeNextPath`。
export function DeletionPendingPanel({ next }: { next: string }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const leave = useCommonFailureRedirect(next);
  const [busy, setBusy] = useState<"cancel" | "logout" | null>(null);
  const [failed, setFailed] = useState(false);

  /// 恢复账号。提交中再点不重复请求
  async function cancel() {
    if (busy) return;
    setBusy("cancel");
    setFailed(false);
    const result = await cancelDeletion().catch(
      () => ({ ok: false, reason: "unavailable" }) as const,
    );
    if (result.ok) {
      await queryClient.cancelQueries({ queryKey: queryKeys.currentUser });
      queryClient.setQueryData(queryKeys.currentUser, result.user);
      toast.success(TOAST_MESSAGES.deletionCancelled);
      router.replace(next);
      return;
    }
    setBusy(null);
    if (result.reason === "signed-out") {
      leave("signed-out");
      return;
    }
    setFailed(true);
  }

  /// 退出登录后回首页（留在这一页只会被送去登录页）
  async function signOut() {
    if (busy) return;
    setBusy("logout");
    await logout().catch(() => {});
    await queryClient.cancelQueries({ queryKey: queryKeys.currentUser });
    queryClient.setQueryData(queryKeys.currentUser, null);
    router.replace("/");
  }

  return (
    <div className="mt-8 flex flex-col gap-3.5">
      {failed && <FormAlert>{DELETION_PENDING_MESSAGES.failed}</FormAlert>}
      <button
        type="button"
        onClick={cancel}
        aria-disabled={busy !== null || undefined}
        className={cn(btnPrimary, btnBlock)}
      >
        {busy === "cancel"
          ? DELETION_PENDING_MESSAGES.cancelling
          : DELETION_PENDING_MESSAGES.cancel}
      </button>
      <button
        type="button"
        onClick={signOut}
        aria-disabled={busy !== null || undefined}
        className={cn(btnText, "min-h-8 self-center text-base")}
      >
        {busy === "logout"
          ? DELETION_PENDING_MESSAGES.loggingOut
          : DELETION_PENDING_MESSAGES.logout}
      </button>
    </div>
  );
}
