"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Upload } from "lucide-react";
import { useRouter } from "next/navigation";
import { type ChangeEvent, useRef, useState } from "react";
import { toast } from "sonner";
import { AvatarCropDialog } from "@/components/settings/profile/AvatarCropDialog";
import { FieldError } from "@/components/settings/profile/FieldError";
import { SettingsCard } from "@/components/settings/SettingsCard";
import { Avatar } from "@/components/user/Avatar";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import type { CurrentUser } from "@/lib/auth/types";
import { useCommonFailureRedirect } from "@/lib/auth/use-common-failure-redirect";
import {
  AVATAR_FILE_MESSAGES,
  avatarUploadMessage,
  checkAvatarFile,
} from "@/lib/identity/avatar-file";
import {
  type AvatarMutationResult,
  removeAvatar,
  uploadAvatar,
} from "@/lib/identity/avatar-upload";
import { btnSecondary } from "@/lib/portal-ui";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";

const PAGE = "/settings/profile";

/// 头像卡片（简报 §3.6 卡片 1、§4.6）：即时生效，不进资料表单。上传：选文件 → 本地校验 → 裁剪对话框 →
/// 上传；移除：立即执行，不二次确认。成功后更新当前用户（导航栏、主页随之更新）并刷新页面数据。
export function AvatarCard({ user }: { user: CurrentUser }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const leave = useCommonFailureRedirect(PAGE);
  const state = useCurrentUser();
  const current = state.status === "authenticated" ? state.user : user;
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [removing, setRemoving] = useState(false);

  /// 头像变了：写进当前用户，再让它重新请求一次
  function applyAvatar(avatarUrl: string | null) {
    queryClient.setQueryData<CurrentUser | null>(queryKeys.currentUser, (previous) =>
      previous ? { ...previous, avatarUrl } : previous,
    );
    void queryClient.invalidateQueries({ queryKey: queryKeys.currentUser });
    router.refresh();
  }

  /// 冷静期、会话失效时离开本页；处理了返回 true
  function leaveIfBlocked(result: AvatarMutationResult): boolean {
    if (result.ok) return false;
    if (result.reason === "pending-deletion" || result.reason === "signed-out") {
      leave(result.reason);
      return true;
    }
    return false;
  }

  async function onPick(event: ChangeEvent<HTMLInputElement>) {
    const picked = event.target.files?.[0];
    event.target.value = "";
    if (!picked) return;
    setError(null);
    const issue = await checkAvatarFile(picked);
    if (issue) {
      setError(AVATAR_FILE_MESSAGES[issue]);
      return;
    }
    setFile(picked);
  }

  async function upload(blob: Blob, fileName: string): Promise<string | null> {
    const result = await uploadAvatar(blob, fileName);
    if (result.ok) {
      setFile(null);
      toast.success("头像已更新");
      applyAvatar(result.avatarUrl);
      return null;
    }
    if (leaveIfBlocked(result)) {
      setFile(null);
      return null;
    }
    return avatarUploadMessage(result);
  }

  async function remove() {
    setRemoving(true);
    setError(null);
    const result = await removeAvatar();
    setRemoving(false);
    if (result.ok) {
      toast.success("已恢复默认头像");
      applyAvatar(null);
    } else if (!leaveIfBlocked(result)) {
      setError("没有移除成功，请稍后再试");
    }
  }

  return (
    <SettingsCard title="头像" description="上传后立即生效，不需要点保存。">
      {/* 头像与按钮始终同一行（窄屏 72px 头像，简报 §3.6） */}
      <div className="flex items-center gap-6 max-[880px]:gap-4">
        <Avatar identity={current} size={96} className="max-[880px]:hidden" />
        <Avatar identity={current} size={72} className="hidden max-[880px]:inline-flex" />
        <div className="flex min-w-0 flex-1 flex-col gap-2.5">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => input.current?.click()}
              className={cn(btnSecondary, "px-3.5 py-1.5 text-sm")}
            >
              <Upload className="size-3.75" strokeWidth={1.75} aria-hidden="true" />
              上传新头像
            </button>
            {current.avatarUrl && (
              <button
                type="button"
                aria-disabled={removing || undefined}
                onClick={() => {
                  if (!removing) void remove();
                }}
                className="text-sm font-medium text-link transition-colors hover:text-link-hover aria-disabled:opacity-55"
              >
                移除
              </button>
            )}
          </div>
          <p className="text-sm leading-5 text-fg-3">
            JPG、PNG 或 WebP，不超过 5 MB，短边至少 128 像素
          </p>
        </div>
      </div>
      <input
        ref={input}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        aria-label="选择头像图片"
        className="sr-only"
        tabIndex={-1}
        onChange={(event) => void onPick(event)}
      />
      {error && <FieldError>{error}</FieldError>}
      <AvatarCropDialog file={file} onCancel={() => setFile(null)} onUpload={upload} />
    </SettingsCard>
  );
}
