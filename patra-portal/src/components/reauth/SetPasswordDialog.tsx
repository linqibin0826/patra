"use client";

import { Mail } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { FormAlert } from "@/components/auth/FormAlert";
import { HumanVerificationWidget } from "@/components/auth/human-verification/HumanVerificationWidget";
import { useHumanVerification } from "@/components/auth/human-verification/use-human-verification";
import { StateIcon } from "@/components/auth/StateIcon";
import { Dialog } from "@/components/ui/dialog";
import { fetchHumanVerification } from "@/lib/auth/human-verification-actions";
import { resendWaitMessage, SET_PASSWORD_MESSAGES, verificationMessage } from "@/lib/auth/messages";
import type { HumanVerificationConfig } from "@/lib/auth/types";
import { requestPasswordReset } from "@/lib/identity/token-actions";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnPrimary, btnSecondary, btnText } from "@/lib/portal-ui";
import { useCountdown } from "@/lib/use-countdown";
import { useReadyAnnouncement } from "@/lib/use-ready-announcement";

/// 没给下次可申请时间时按 60 秒算
const DEFAULT_WAIT_MS = 60_000;

interface SetPasswordDialogProps {
  open: boolean;
  onClose: () => void;
  /// 当前账号的邮箱：设置密码的邮件发到这里
  email: string;
  /// true：从敏感操作进来，要说明为什么；false：从「设置密码」按钮进来
  reason: boolean;
}

/// 设置密码（决策 E，画板 Security）：没有密码的账号走现有的找回密码流程，重设处理器没有凭据行时会新建。设好后所有设备
/// 下线，发送之前就要说清楚。这个对话框里没有密码框。
/// 申请接口每次都要人机验证令牌。对话框所在的页面很多，所以打开时才读厂商配置，读取期间不能发送。
export function SetPasswordDialog({ open, onClose, email, reason }: SetPasswordDialogProps) {
  const humanVerification = useHumanVerification(true);
  const [verification, setVerification] = useState<HumanVerificationConfig | null | undefined>(
    undefined,
  );
  useEffect(() => {
    if (!open) return;
    let active = true;
    setVerification(undefined);
    fetchHumanVerification()
      .catch(() => null)
      .then((config) => {
        if (active) setVerification(config);
      });
    return () => {
      active = false;
    };
  }, [open]);
  const [step, setStep] = useState<"ask" | "sent">("ask");
  const [alert, setAlert] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [nextAt, setNextAt] = useState<number | null>(null);
  const left = useCountdown(nextAt);
  const announcement = useReadyAnnouncement(left > 0);
  const sendRef = useRef<HTMLButtonElement | null>(null);

  /// 发送，首次和「重新发送」共用；失败回到第一步显示原因
  async function send() {
    if (busy || humanVerification.token === null) return;
    setBusy(true);
    setAlert(null);
    const result = await orUnavailable(requestPasswordReset(email, humanVerification.token));
    setBusy(false);
    humanVerification.reset();
    if (result.ok) {
      setNextAt(Date.parse(result.nextRequestAt) || Date.now() + DEFAULT_WAIT_MS);
      setStep("sent");
      return;
    }
    setStep("ask");
    setAlert(
      result.reason === "throttled"
        ? resendWaitMessage(result.retryAfterSeconds ?? 60, true)
        : (verificationMessage(result.reason) ?? SET_PASSWORD_MESSAGES.failed),
    );
  }

  /// 关闭：下次打开从第一步开始；发送中不关（`dismissible`）
  function close() {
    if (busy) return;
    setStep("ask");
    setAlert(null);
    humanVerification.reset();
    onClose();
  }

  const sent = step === "sent";
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) close();
      }}
      title={
        sent
          ? SET_PASSWORD_MESSAGES.sentTitle
          : reason
            ? SET_PASSWORD_MESSAGES.reasonTitle
            : SET_PASSWORD_MESSAGES.title
      }
      description={
        sent
          ? SET_PASSWORD_MESSAGES.sentDescription(email)
          : reason
            ? SET_PASSWORD_MESSAGES.reasonWhy
            : SET_PASSWORD_MESSAGES.why
      }
      icon={sent ? <StateIcon icon={Mail} tone="info" /> : undefined}
      size="md"
      className="min-[881px]:w-115"
      dismissible={!busy}
      initialFocus={sendRef}
      alert={alert ? <FormAlert>{alert}</FormAlert> : undefined}
    >
      {sent ? (
        <>
          <div className="flex flex-col items-start gap-2 text-sm leading-5 text-fg-3">
            <span>{SET_PASSWORD_MESSAGES.spam}</span>
            {busy ? (
              <span>{SET_PASSWORD_MESSAGES.sending}</span>
            ) : left > 0 ? (
              <span>{resendWaitMessage(left, false)}</span>
            ) : (
              !humanVerification.blocked && (
                <button type="button" className={btnText} onClick={() => void send()}>
                  {SET_PASSWORD_MESSAGES.resend}
                </button>
              )
            )}
            {!busy && left <= 0 && (
              <HumanVerificationWidget
                config={verification}
                action="password_reset"
                verification={humanVerification}
              />
            )}
            <span role="status" className="sr-only">
              {announcement}
            </span>
          </div>
          <div className="flex justify-end gap-3 max-[880px]:flex-col-reverse">
            <button type="button" className={btnPrimary} onClick={close}>
              {SET_PASSWORD_MESSAGES.done}
            </button>
          </div>
        </>
      ) : (
        <>
          <ol className="flex list-decimal flex-col gap-1.5 pl-4.5 text-base leading-5.5 text-fg-2 [&_b]:font-medium [&_b]:text-fg-1">
            <li>
              我们发一封邮件到 <b className="[overflow-wrap:anywhere]">{email}</b>
              ，点邮件里的链接设置密码。
            </li>
            <li>
              设置后，<b>所有设备都会退出登录</b>，包括这台。
            </li>
            <li>{reason ? SET_PASSWORD_MESSAGES.reasonLast : SET_PASSWORD_MESSAGES.last}</li>
          </ol>
          <HumanVerificationWidget
            config={verification}
            action="password_reset"
            verification={humanVerification}
          />
          <div className="flex justify-end gap-3 max-[880px]:flex-col-reverse">
            <button
              type="button"
              className={btnSecondary}
              aria-disabled={busy || undefined}
              onClick={close}
            >
              {SET_PASSWORD_MESSAGES.cancel}
            </button>
            <button
              ref={sendRef}
              type="button"
              className={btnPrimary}
              aria-disabled={busy || humanVerification.blocked || undefined}
              onClick={() => void send()}
            >
              {busy ? SET_PASSWORD_MESSAGES.sending : SET_PASSWORD_MESSAGES.send}
            </button>
          </div>
        </>
      )}
    </Dialog>
  );
}
