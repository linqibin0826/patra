"use client";

import { useQueryClient } from "@tanstack/react-query";
import { CalendarClock } from "lucide-react";
import { useRef, useState } from "react";
import { FormAlert } from "@/components/auth/FormAlert";
import { FormField } from "@/components/auth/FormField";
import { StateIcon } from "@/components/auth/StateIcon";
import { Dialog } from "@/components/ui/dialog";
import { DELETE_ACCOUNT_MESSAGES } from "@/lib/auth/messages";
import type { CurrentUser } from "@/lib/auth/types";
import { useCommonFailureRedirect } from "@/lib/auth/use-common-failure-redirect";
import { addDays, formatDate } from "@/lib/format-time";
import { endDeletedSession, requestDeletion } from "@/lib/identity/account-actions";
import { normalizeUsername } from "@/lib/identity/username-rules";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnDanger, btnPrimary, btnSecondary } from "@/lib/portal-ui";
import { queryKeys } from "@/lib/query-keys";
import { useReauth } from "@/lib/reauth/use-reauth";

const PAGE = "/settings/account";
/// 冷静期天数（release spec 2.1.9）；对话框里的到期日只是提示，以服务端为准
const COOLING_OFF_DAYS = 15;

/// 对话框的三屏
type Step = "consequences" | "confirm" | "done";

interface DeleteAccountDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  user: CurrentUser;
}

/// 注销对话框（简报 §3.15）：后果 → 输用户名确认（经再认证包装提交）→ 已提交。已提交时本机会话已在服务端撤销，
/// 结果屏用本地数据渲染；关闭它（「好的」、`Esc`、遮罩）就把当前用户写成未登录、清掉 Cookie 并回首页。
export function DeleteAccountDialog({ open, onOpenChange, user }: DeleteAccountDialogProps) {
  const queryClient = useQueryClient();
  const leave = useCommonFailureRedirect(PAGE);
  const { withReauth } = useReauth();
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [step, setStep] = useState<Step>("consequences");
  const [value, setValue] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [alert, setAlert] = useState(false);
  const [busy, setBusy] = useState(false);
  const [dueAt, setDueAt] = useState<string | null>(null);
  const matches = normalizeUsername(value) === user.username;

  /// 回到初始状态（下次打开从第 1 屏开始）
  function reset() {
    setStep("consequences");
    setValue("");
    setError(null);
    setAlert(false);
  }

  /// 结果屏关闭：会话已经没了，写成未登录并结束
  function finish() {
    queryClient.setQueryData(queryKeys.currentUser, null);
    void endDeletedSession();
  }

  function onOpenChangeInternal(next: boolean) {
    if (next) return;
    if (step === "done") {
      finish();
      return;
    }
    onOpenChange(false);
    reset();
  }

  /// 提交注销：用户名一致才请求
  async function submit() {
    if (busy || !matches) return;
    setBusy(true);
    setAlert(false);
    setError(null);
    const result = await orUnavailable(withReauth(() => requestDeletion(normalizeUsername(value))));
    setBusy(false);
    if (result.ok) {
      setDueAt(result.deletionDueAt);
      setStep("done");
      return;
    }
    switch (result.reason) {
      case "cancelled":
        return;
      case "mismatch":
        setError(DELETE_ACCOUNT_MESSAGES.mismatch);
        inputRef.current?.focus();
        return;
      case "pending-deletion":
      case "signed-out":
        leave(result.reason);
        return;
      default:
        setAlert(true);
    }
  }

  if (step === "done") {
    return (
      <Dialog
        open={open}
        onOpenChange={onOpenChangeInternal}
        title={DELETE_ACCOUNT_MESSAGES.doneTitle}
        description={`账号将于 ${dueAt ? formatDate(dueAt) : ""}删除。所有设备已退出登录。在此之前用邮箱和密码登录，就可以取消注销。确认邮件已发到 ${user.email}。`}
        icon={<StateIcon icon={CalendarClock} tone="warning" />}
      >
        <div className="flex justify-end">
          <button type="button" className={btnPrimary} onClick={finish}>
            好的
          </button>
        </div>
      </Dialog>
    );
  }

  if (step === "confirm") {
    return (
      <Dialog
        open={open}
        onOpenChange={onOpenChangeInternal}
        title={DELETE_ACCOUNT_MESSAGES.stepTwoTitle}
        description={
          <>
            请输入你的用户名{" "}
            <b className="select-all font-mono font-medium text-fg-1">{user.username}</b> 以确认。
          </>
        }
        dismissible={!busy}
        initialFocus={inputRef}
        alert={alert ? <FormAlert>{DELETE_ACCOUNT_MESSAGES.failed}</FormAlert> : undefined}
      >
        <FormField id="delete-username" label="用户名" error={error ?? undefined}>
          {(control) => (
            <input
              {...control}
              ref={inputRef}
              type="text"
              autoComplete="off"
              autoCapitalize="none"
              spellCheck={false}
              className={`${control.className} font-mono`}
              value={value}
              readOnly={busy}
              onChange={(event) => {
                setValue(event.target.value.toLowerCase());
                if (error) setError(null);
              }}
              onBlur={() => {
                if (value && !matches) setError(DELETE_ACCOUNT_MESSAGES.mismatch);
              }}
            />
          )}
        </FormField>
        <div className="flex justify-end gap-3 max-[880px]:flex-col-reverse">
          <button
            type="button"
            className={btnSecondary}
            onClick={() => {
              if (busy) return;
              setStep("consequences");
              setError(null);
              setAlert(false);
            }}
          >
            返回
          </button>
          <button
            type="button"
            className={btnDanger}
            aria-disabled={busy || !matches || undefined}
            onClick={submit}
          >
            {busy ? DELETE_ACCOUNT_MESSAGES.submitting : DELETE_ACCOUNT_MESSAGES.submit}
          </button>
        </div>
      </Dialog>
    );
  }

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChangeInternal}
      title={DELETE_ACCOUNT_MESSAGES.stepOneTitle}
    >
      <div className="flex flex-col gap-3.5">
        <ul className="flex list-disc flex-col gap-1.5 pl-4.5 text-base leading-5.5 text-fg-2">
          <li>所有设备立即退出登录，主页对别人消失。</li>
          <li>15 天冷静期内用邮箱和密码登录，就可以取消注销。</li>
          <li>
            到期后资料、头像、关注领域被删除，邮箱可以重新注册；用户名 @{user.username}{" "}
            永久保留、不能再被使用。
          </li>
        </ul>
        <p className="text-sm leading-5 text-fg-2">
          到期日：
          <b className="font-medium text-fg-1">
            {formatDate(addDays(Date.now(), COOLING_OFF_DAYS))}
          </b>
        </p>
      </div>
      <div className="flex justify-end gap-3 max-[880px]:flex-col-reverse">
        <button type="button" className={btnSecondary} onClick={() => onOpenChangeInternal(false)}>
          取消
        </button>
        <button type="button" className={btnPrimary} onClick={() => setStep("confirm")}>
          继续
        </button>
      </div>
    </Dialog>
  );
}
