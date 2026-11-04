import { ProviderMark } from "@/components/auth/ProviderMark";
import { LOGIN_METHOD_MESSAGES } from "@/lib/auth/messages";
import { type Provider, providerName } from "@/lib/auth/oauth-providers";
import { formatDateTime } from "@/lib/format-time";
import type { LinkedMethod } from "@/lib/identity/login-methods";
import { btnSecondary, btnText } from "@/lib/portal-ui";
import { cn } from "@/lib/utils";

/// 行的版式（画板 `.pt-lm-row`）：标识方块 / 名称与说明 / 操作。「邮箱和密码」一行也用
export const LOGIN_METHOD_ROW =
  "grid grid-cols-[40px_minmax(0,1fr)_auto] items-center gap-3.5 border-t border-border-subtle py-3.5 first:border-t-0 first:pt-0 last:pb-0 max-[880px]:grid-cols-[36px_minmax(0,1fr)_auto] max-[880px]:gap-3";
export const LOGIN_METHOD_ICON =
  "inline-flex size-10 items-center justify-center rounded-lg border border-border-subtle bg-bg-elevated text-fg-2 max-[880px]:size-9";
export const LOGIN_METHOD_ACTION = cn(btnSecondary, "px-3.5 py-1.5 text-sm");

interface LoginMethodRowProps {
  provider: Provider;
  /// 已绑定的账号；未绑定时为 undefined
  linked: LinkedMethod | undefined;
  /// 这个域名能不能发起绑定（决策 G）
  canBind: boolean;
  /// 绑定按钮不可点：邮箱未验证，或别的操作进行中
  bindDisabled: boolean;
  binding: boolean;
  /// 解绑按钮不可点：只剩这一种，或别的操作进行中
  unlinkDisabled: boolean;
  unlinking: boolean;
  /// 这是唯一的登录方式，又没有密码：说明原因和办法
  lastOnly: boolean;
  onBind: () => void;
  onUnlink: () => void;
  onSetPassword: () => void;
}

/// 账号标识：Google 是邮箱，GitHub 是用户名（等宽），ORCID 是 iD（等宽，链到它的主页）
function AccountLabel({ linked }: { linked: LinkedMethod }) {
  if (linked.provider === "orcid") {
    return (
      <a
        href={`https://orcid.org/${linked.accountLabel}`}
        target="_blank"
        rel="noopener noreferrer"
        className="link-draw font-mono text-link hover:text-link-hover"
      >
        {linked.accountLabel}
      </a>
    );
  }
  return (
    <span className={cn("text-fg-2", linked.provider === "github" && "font-mono")}>
      {linked.accountLabel}
    </span>
  );
}

/// 「登录方式」卡片里一家第三方的一行（画板 Security）
export function LoginMethodRow({
  provider,
  linked,
  canBind,
  bindDisabled,
  binding,
  unlinkDisabled,
  unlinking,
  lastOnly,
  onBind,
  onUnlink,
  onSetPassword,
}: LoginMethodRowProps) {
  const name = providerName(provider);
  const noteId = `login-method-note-${provider}`;
  return (
    <li className={LOGIN_METHOD_ROW}>
      <span className={LOGIN_METHOD_ICON} aria-hidden="true">
        <ProviderMark provider={provider} />
      </span>
      <div className="min-w-0">
        <div className="text-base leading-5 font-medium text-fg-1">{name}</div>
        <div className="mt-0.5 text-sm leading-5 text-fg-3 [overflow-wrap:anywhere]">
          {linked ? (
            <>
              <div>
                <AccountLabel linked={linked} />
              </div>
              <div>{`绑定于 ${formatDateTime(linked.linkedAt).slice(0, 10)}`}</div>
            </>
          ) : provider === "orcid" ? (
            LOGIN_METHOD_MESSAGES.unlinkedOrcid
          ) : (
            LOGIN_METHOD_MESSAGES.unlinked
          )}
        </div>
      </div>
      <div>
        {linked ? (
          <button
            type="button"
            aria-label={`解绑 ${name}`}
            aria-disabled={unlinkDisabled || undefined}
            aria-describedby={lastOnly ? noteId : undefined}
            onClick={onUnlink}
            className="min-h-8 text-sm font-medium text-status-danger not-aria-disabled:hover:underline aria-disabled:cursor-not-allowed aria-disabled:text-fg-4"
          >
            {unlinking ? LOGIN_METHOD_MESSAGES.unlinking : LOGIN_METHOD_MESSAGES.unlink}
          </button>
        ) : (
          canBind && (
            <button
              type="button"
              aria-label={`绑定 ${name}`}
              aria-disabled={bindDisabled || undefined}
              onClick={onBind}
              className={LOGIN_METHOD_ACTION}
            >
              {binding ? `正在前往 ${name}…` : LOGIN_METHOD_MESSAGES.bind}
            </button>
          )
        )}
      </div>
      {lastOnly && (
        <p id={noteId} className="col-[2/-1] -mt-1 text-sm leading-5 text-fg-2">
          {LOGIN_METHOD_MESSAGES.lastOnly}
          <button type="button" className={btnText} onClick={onSetPassword}>
            {LOGIN_METHOD_MESSAGES.setPassword}
          </button>
          {LOGIN_METHOD_MESSAGES.lastOnlyAfter}
        </p>
      )}
    </li>
  );
}
