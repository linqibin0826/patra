"use client";

import { useQueryClient } from "@tanstack/react-query";
import { KeyRound } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { FormAlert } from "@/components/auth/FormAlert";
import { useOAuthAlert } from "@/components/auth/use-oauth-alert";
import { ResendVerificationButton } from "@/components/portal/ResendVerificationButton";
import { SetPasswordDialog } from "@/components/reauth/SetPasswordDialog";
import { SettingsCard } from "@/components/settings/SettingsCard";
import {
  LOGIN_METHOD_ACTION,
  LOGIN_METHOD_ICON,
  LOGIN_METHOD_ROW,
  LoginMethodRow,
} from "@/components/settings/security/LoginMethodRow";
import { Dialog } from "@/components/ui/dialog";
import {
  LOGIN_METHOD_MESSAGES,
  linkedToast,
  linkNoticeAlert,
  type OAuthAlert,
  unlinkedToast,
} from "@/lib/auth/messages";
import type { OAuthNotice } from "@/lib/auth/oauth-notice";
import { PROVIDERS, type Provider, providerName } from "@/lib/auth/oauth-providers";
import { useCommonFailureRedirect } from "@/lib/auth/use-common-failure-redirect";
import { startProviderLink, unlinkProvider } from "@/lib/identity/login-method-actions";
import { leaveSite } from "@/lib/leave-site";
import { useLoginMethodsQuery } from "@/lib/login-methods-query";
import { orUnavailable } from "@/lib/or-unavailable";
import { btnDanger, btnSecondary, btnText } from "@/lib/portal-ui";
import { queryKeys } from "@/lib/query-keys";
import { useReauth } from "@/lib/reauth/use-reauth";
import { cn } from "@/lib/utils";

const PAGE = "/settings/security";

interface LoginMethodsCardProps {
  /// 这个域名能不能发起绑定（决策 G）
  oauthEnabled: boolean;
  email: string;
  emailVerified: boolean;
  /// 从第三方回来时的结果
  notice: OAuthNotice | null;
}

/// 安全页 · 登录方式（画板 Security）：邮箱和密码一行，三家各一行。绑定、解绑都经再认证；没有密码的人再认证不了（决策 E），
/// 直接开设置密码，不用先请求一次。只剩一种又没有密码时不能解绑，说明原因和办法。
export function LoginMethodsCard({
  oauthEnabled,
  email,
  emailVerified,
  notice,
}: LoginMethodsCardProps) {
  const queryClient = useQueryClient();
  const leave = useCommonFailureRedirect(PAGE);
  const { withReauth } = useReauth();
  const { data, error, refetch } = useLoginMethodsQuery();
  const { oauthAlert, dismiss } = useOAuthAlert(notice, linkNoticeAlert);
  const [alert, setAlert] = useState<OAuthAlert | null>(null);
  const [unverified, setUnverified] = useState(!emailVerified);
  const [binding, setBinding] = useState<Provider | null>(null);
  const [unlinking, setUnlinking] = useState<Provider | null>(null);
  const [confirming, setConfirming] = useState<Provider | null>(null);
  const [setPassword, setSetPassword] = useState<{ reason: boolean } | null>(null);
  const toasted = useRef(false);

  useEffect(() => {
    if (error?.signedOut) leave("signed-out");
  }, [error, leave]);

  // 绑定成功是 toast；严格模式下 effect 跑两次也只弹一次
  useEffect(() => {
    if (notice?.kind === "linked" && !toasted.current) {
      toasted.current = true;
      toast.success(linkedToast(notice.provider));
    }
  }, [notice]);

  // 从第三方按后退时页面可能从往返缓存恢复，「正在前往」要复位
  useEffect(() => {
    const reset = () => setBinding(null);
    window.addEventListener("pageshow", reset);
    return () => window.removeEventListener("pageshow", reset);
  }, []);

  const hasPassword = data?.hasPassword;
  const linked = data?.linked ?? [];
  const busy = binding !== null || unlinking !== null;
  const confirmingMethod = linked.find((method) => method.provider === confirming);
  const shown = alert ?? oauthAlert;

  /// 开始一次操作前收起上一次的提示
  function clearAlerts() {
    setAlert(null);
    dismiss();
  }

  /// 绑定：发起成功后整页跳走，这一行一直显示「正在前往」
  async function bind(provider: Provider) {
    if (busy || unverified) return;
    if (hasPassword === false) {
      setSetPassword({ reason: true });
      return;
    }
    clearAlerts();
    setBinding(provider);
    const result = await orUnavailable(withReauth(() => startProviderLink(provider)));
    if (result.ok) {
      leaveSite(result.authorizationUrl);
      return;
    }
    setBinding(null);
    switch (result.reason) {
      case "cancelled":
        return;
      case "signed-out":
      case "pending-deletion":
        leave(result.reason);
        return;
      case "email-not-verified":
        setUnverified(true);
        return;
      case "already-linked":
        setAlert(linkNoticeAlert({ kind: "already-linked", provider }));
        void queryClient.invalidateQueries({ queryKey: queryKeys.loginMethods });
        return;
      default:
        setAlert(linkNoticeAlert({ kind: "unavailable", provider }));
    }
  }

  /// 点解绑：只剩一种不响应；没有密码开设置密码；否则先确认
  function askUnlink(provider: Provider, lastOnly: boolean) {
    if (busy || lastOnly) return;
    if (hasPassword === false) {
      setSetPassword({ reason: true });
      return;
    }
    setConfirming(provider);
  }

  /// 确认后解绑，经再认证
  async function unlink(provider: Provider) {
    setConfirming(null);
    clearAlerts();
    setUnlinking(provider);
    const result = await orUnavailable(withReauth(() => unlinkProvider(provider)));
    setUnlinking(null);
    if (result.ok) {
      toast.success(unlinkedToast(provider));
      void queryClient.invalidateQueries({ queryKey: queryKeys.loginMethods });
      return;
    }
    switch (result.reason) {
      case "cancelled":
        return;
      case "signed-out":
      case "pending-deletion":
        leave(result.reason);
        return;
      case "last-method":
        setAlert({ tone: "danger", text: LOGIN_METHOD_MESSAGES.lastMethodRejected });
        void queryClient.invalidateQueries({ queryKey: queryKeys.loginMethods });
        return;
      default:
        setAlert({ tone: "danger", text: LOGIN_METHOD_MESSAGES.unlinkFailed });
    }
  }

  return (
    <SettingsCard
      id="login-methods"
      title="登录方式"
      description={
        hasPassword === false
          ? LOGIN_METHOD_MESSAGES.descriptionNoPassword
          : LOGIN_METHOD_MESSAGES.description
      }
    >
      {shown && <FormAlert tone={shown.tone}>{shown.text}</FormAlert>}
      {unverified && oauthEnabled && data && (
        <FormAlert tone="warning">
          绑定前要先验证邮箱。验证邮件已发到 {email}。<ResendVerificationButton />
        </FormAlert>
      )}
      {data ? (
        <>
          <ul aria-label="登录方式">
            <li className={LOGIN_METHOD_ROW}>
              <span className={LOGIN_METHOD_ICON} aria-hidden="true">
                <KeyRound className="size-4.5" strokeWidth={1.6} />
              </span>
              <div className="min-w-0">
                <div className="text-base leading-5 font-medium text-fg-1">
                  {LOGIN_METHOD_MESSAGES.passwordRow}
                </div>
                <div className="mt-0.5 text-sm leading-5 text-fg-3">
                  {data.hasPassword
                    ? LOGIN_METHOD_MESSAGES.passwordSet
                    : LOGIN_METHOD_MESSAGES.passwordUnset}
                </div>
              </div>
              {data.hasPassword ? (
                <span />
              ) : (
                <button
                  type="button"
                  className={LOGIN_METHOD_ACTION}
                  onClick={() => setSetPassword({ reason: false })}
                >
                  {LOGIN_METHOD_MESSAGES.setPassword}
                </button>
              )}
            </li>
            {PROVIDERS.map((provider) => {
              const method = linked.find((item) => item.provider === provider);
              const lastOnly = method !== undefined && !data.hasPassword && linked.length === 1;
              return (
                <LoginMethodRow
                  key={provider}
                  provider={provider}
                  linked={method}
                  canBind={oauthEnabled}
                  bindDisabled={busy || unverified}
                  binding={binding === provider}
                  unlinkDisabled={busy || lastOnly}
                  unlinking={unlinking === provider}
                  lastOnly={lastOnly}
                  onBind={() => void bind(provider)}
                  onUnlink={() => askUnlink(provider, lastOnly)}
                  onSetPassword={() => setSetPassword({ reason: false })}
                />
              );
            })}
          </ul>
          {!oauthEnabled && (
            <p className="text-sm leading-5 text-fg-3">{LOGIN_METHOD_MESSAGES.tailnetNote}</p>
          )}
        </>
      ) : error && !error.signedOut ? (
        <FormAlert
          action={
            <button type="button" onClick={() => void refetch()} className={btnText}>
              {LOGIN_METHOD_MESSAGES.retry}
            </button>
          }
        >
          {LOGIN_METHOD_MESSAGES.loadFailed}
        </FormAlert>
      ) : (
        <div
          role="status"
          aria-label={LOGIN_METHOD_MESSAGES.loading}
          className="flex flex-col gap-5.5"
        >
          {["w-[30%]", "w-1/4", "w-[28%]"].map((width) => (
            <div key={width} className="flex items-center gap-3.5">
              <span className="size-10 shrink-0 rounded-md bg-paper-200 shimmer" />
              <div className="flex flex-1 flex-col gap-2">
                <span className={cn("h-4 rounded-md bg-paper-200 shimmer", width)} />
                <span className="h-3.5 w-2/5 rounded-md bg-paper-200 opacity-70 shimmer" />
              </div>
            </div>
          ))}
        </div>
      )}
      <Dialog
        open={confirming !== null}
        onOpenChange={(next) => {
          if (!next) setConfirming(null);
        }}
        title={confirming ? `解绑 ${providerName(confirming)} 账号？` : ""}
        description={
          confirming && confirmingMethod
            ? LOGIN_METHOD_MESSAGES.unlinkConfirm(
                providerName(confirming),
                confirmingMethod.accountLabel,
                email,
              )
            : undefined
        }
        size="sm"
      >
        <div className="flex justify-end gap-3 max-[880px]:flex-col-reverse">
          <button type="button" className={btnSecondary} onClick={() => setConfirming(null)}>
            {LOGIN_METHOD_MESSAGES.cancel}
          </button>
          <button
            type="button"
            className={btnDanger}
            onClick={() => {
              if (confirming) void unlink(confirming);
            }}
          >
            {LOGIN_METHOD_MESSAGES.unlink}
          </button>
        </div>
      </Dialog>
      <SetPasswordDialog
        open={setPassword !== null}
        onClose={() => setSetPassword(null)}
        email={email}
        reason={setPassword?.reason ?? false}
      />
    </SettingsCard>
  );
}
