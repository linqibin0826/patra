"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { FormAlert } from "@/components/auth/FormAlert";
import { FormField } from "@/components/auth/FormField";
import { UsernameField } from "@/components/auth/UsernameField";
import { SettingsCard } from "@/components/settings/SettingsCard";
import {
  ACCOUNT_MESSAGES,
  usernameChangedToast,
  usernameHint,
  usernameMessage,
} from "@/lib/auth/messages";
import type { CurrentUser } from "@/lib/auth/types";
import { useCommonFailureRedirect } from "@/lib/auth/use-common-failure-redirect";
import { formatDate } from "@/lib/format-time";
import { changeUsername } from "@/lib/identity/account-actions";
import { normalizeUsername, usernameIssue } from "@/lib/identity/username-rules";
import { btnPrimary, btnSecondary } from "@/lib/portal-ui";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";

const PAGE = "/settings/account";
const SMALL = "px-3.5 py-1.5 text-sm";

interface UsernameCardProps {
  user: CurrentUser;
  /// 页面渲染时的时间：首次渲染用它判断冷却，服务端与浏览器结果一致；挂载后换成浏览器时间
  now: number;
}

/// 账号页 · 用户名卡片（简报 §3.10 卡片 1、§4.7）：展示 `@用户名` 与主页地址；冷却期内「修改」禁用并写出下次
/// 可改的日期；点「修改」在卡片内展开输入框。改名成功后刷新当前用户与页面（设置页标题下的 `@用户名` 来自
/// 服务端渲染）。
export function UsernameCard({ user, now: renderedAt }: UsernameCardProps) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const leave = useCommonFailureRedirect(PAGE);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [now, setNow] = useState(renderedAt);
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [alert, setAlert] = useState<"cooldown" | "failed" | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => setNow(Date.now()), []);

  useEffect(() => {
    if (editing) {
      inputRef.current?.focus();
      inputRef.current?.select();
    }
  }, [editing]);

  const nextChange = user.nextUsernameChangeAt;
  const cooling = nextChange !== null && Date.parse(nextChange) > now;

  /// 进入编辑态：预填当前用户名
  function startEditing() {
    if (cooling) return;
    setValue(user.username);
    setError(null);
    setAlert(null);
    setEditing(true);
  }

  /// 前端能判的问题：规则不符、与当前相同
  function frontEndIssue(raw: string): string | null {
    const issue = usernameIssue(raw);
    if (issue) return usernameMessage(issue);
    return normalizeUsername(raw) === user.username ? usernameMessage("UNCHANGED") : null;
  }

  /// 保存：先按前端规则判，再请求；按结果回展示态或显示错误
  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const issue = frontEndIssue(value);
    if (issue) {
      setError(issue);
      inputRef.current?.focus();
      return;
    }
    setBusy(true);
    setAlert(null);
    const result = await changeUsername(normalizeUsername(value)).catch(
      () => ({ ok: false, reason: "unavailable" }) as const,
    );
    setBusy(false);
    if (result.ok) {
      toast.success(usernameChangedToast(result.username));
      setEditing(false);
      void queryClient.invalidateQueries({ queryKey: queryKeys.currentUser });
      router.refresh();
      return;
    }
    switch (result.reason) {
      case "invalid":
        setError(usernameMessage(result.code));
        return;
      case "taken":
        setError(usernameMessage("ALREADY_TAKEN"));
        return;
      case "cooldown":
        setEditing(false);
        setAlert("cooldown");
        void queryClient.invalidateQueries({ queryKey: queryKeys.currentUser });
        router.refresh();
        return;
      case "pending-deletion":
      case "signed-out":
        leave(result.reason);
        return;
      case "unavailable":
        setAlert("failed");
    }
  }

  const alertText =
    alert === "cooldown"
      ? cooling && nextChange
        ? `${ACCOUNT_MESSAGES.cooldown}，下次可修改：${formatDate(nextChange)}`
        : ACCOUNT_MESSAGES.cooldown
      : alert === "failed"
        ? ACCOUNT_MESSAGES.saveFailed
        : null;

  return (
    <SettingsCard title="用户名">
      {alertText && <FormAlert>{alertText}</FormAlert>}
      {editing ? (
        <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
          <FormField
            id="account-username"
            label="新用户名"
            hint={usernameHint(normalizeUsername(value))}
            error={error ?? undefined}
          >
            {(control) => (
              <>
                <span aria-hidden="true" className="font-mono text-md text-fg-3">
                  @
                </span>
                <UsernameField
                  {...control}
                  className={cn(control.className, "font-mono")}
                  ref={inputRef}
                  value={value}
                  readOnly={busy}
                  onChange={(event) => {
                    setValue(event.target.value);
                    if (error) setError(frontEndIssue(event.target.value));
                  }}
                />
              </>
            )}
          </FormField>
          <div className="flex gap-3">
            <button
              type="submit"
              aria-disabled={busy || undefined}
              className={cn(btnPrimary, SMALL)}
            >
              {busy ? "保存中…" : "保存"}
            </button>
            <button
              type="button"
              onClick={() => setEditing(false)}
              className={cn(btnSecondary, SMALL)}
            >
              取消
            </button>
          </div>
        </form>
      ) : (
        <div className="flex flex-col gap-1.5">
          <div className="flex flex-wrap items-center gap-4">
            <div className="min-w-0 flex-1">
              <p className="truncate font-mono text-xl leading-7 text-fg-1">@{user.username}</p>
              <a
                href={`/u/${user.username}`}
                target="_blank"
                rel="noopener noreferrer"
                className="link-draw font-mono text-sm text-link hover:text-link-hover"
              >
                /u/{user.username}
              </a>
            </div>
            <button
              type="button"
              onClick={startEditing}
              aria-disabled={cooling || undefined}
              className={cn(btnSecondary, SMALL)}
            >
              修改
            </button>
          </div>
          {cooling && nextChange && (
            <p className="text-sm leading-5 text-fg-2">下次可修改：{formatDate(nextChange)}</p>
          )}
        </div>
      )}
      <p className="text-sm leading-5 text-fg-3">{ACCOUNT_MESSAGES.usernameNote}</p>
    </SettingsCard>
  );
}
