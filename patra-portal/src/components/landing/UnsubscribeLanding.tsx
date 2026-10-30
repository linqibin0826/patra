"use client";

import { CircleAlert, CircleCheck } from "lucide-react";
import Link from "next/link";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { AuthShell } from "@/components/auth/AuthShell";
import { FormAlert } from "@/components/auth/FormAlert";
import { StateIcon } from "@/components/auth/StateIcon";
import { useCurrentUser } from "@/lib/auth/current-user-query";
import { authPageHref } from "@/lib/auth/next-path";
import { UNSUBSCRIBE_MESSAGES as M } from "@/lib/notification/messages";
import type {
  NotificationPreferences,
  PreferenceCategory,
  UnsubscribeLink,
  UnsubscribeResult,
} from "@/lib/notification/schema";
import { readUnsubscribeLink, unsubscribe } from "@/lib/notification/unsubscribe-actions";
import { doneDescription, doneTitle, kindLine } from "@/lib/notification/unsubscribe-copy";
import { readUnsubscribeFragment } from "@/lib/notification/unsubscribe-link";
import { btnBlock, btnPrimary, btnSecondary, btnText } from "@/lib/portal-ui";
import { useFragmentOnce } from "@/lib/use-fragment-once";
import { cn } from "@/lib/utils";

const SETTINGS = "/settings/notifications";
const SKELETON = "rounded-sm bg-paper-200 shimmer";

/// 页面所处的屏（spec 第 7.3 节）
type Screen =
  | { name: "reading" }
  | { name: "unavailable" }
  | { name: "invalid" }
  | { name: "ready"; link: UnsubscribeLink }
  /// 这一类已退订（`turnedOff` 非空）或已经退订过（`turnedOff` 为 null）
  | {
      name: "type-done";
      modes: NotificationPreferences;
      turnedOff: PreferenceCategory[] | null;
      othersEnabled: boolean;
    }
  | { name: "all-done" };

/// 正在进行的请求：重新读、退订这一类、关闭全部
type Busy = "retry" | "type" | "all" | null;

const UNAVAILABLE = { ok: false, reason: "unavailable" } as const;

/// 退订确认页（简报 §3.6，PAP-148 spec 第 7 节）：免登录，只认片段里的令牌与类别。打开时只读一次链接信息（企业
/// 邮箱的安全网关会预先打开链接，打开就退订会被动退订），点按钮才退订。带着登录状态打开也只按令牌工作，登录
/// 状态只决定「去通知设置」怎么写。令牌读到即从地址栏抹掉，之后留在内存里给重试与「同时关闭其余」用。
export function UnsubscribeLanding() {
  const fragment = useFragmentOnce(readUnsubscribeFragment);
  const [screen, setScreen] = useState<Screen>({ name: "reading" });
  const [busy, setBusy] = useState<Busy>(null);
  const [failed, setFailed] = useState(false);
  const mainRef = useRef<HTMLButtonElement | null>(null);
  const started = useRef(false);
  const signedIn = useCurrentUser().status === "authenticated";

  // 严格模式下挂载时 effect 会跑两次：只读一次
  useEffect(() => {
    if (fragment === undefined || started.current) return;
    started.current = true;
    if (fragment === null) {
      setScreen({ name: "invalid" });
      return;
    }
    void readScreen(fragment).then(setScreen);
  }, [fragment]);

  const ready = screen.name === "ready";
  useEffect(() => {
    if (ready) mainRef.current?.focus();
  }, [ready]);

  /// 退订这一类（`type`）或关闭全部（`all`）
  async function submit(which: "type" | "all") {
    if (!fragment || busy) return;
    setBusy(which);
    setFailed(false);
    const result = await unsubscribe(
      fragment.token,
      which === "all" ? "ALL" : fragment.category,
    ).catch(() => UNAVAILABLE);
    setBusy(null);
    if (!result.ok) {
      if (result.reason === "invalid") {
        setScreen({ name: "invalid" });
      } else {
        setFailed(true);
      }
      return;
    }
    setScreen(afterUnsubscribe(which, result.result));
  }

  async function retry() {
    if (!fragment || busy) return;
    setBusy("retry");
    const next = await readScreen(fragment);
    setBusy(null);
    setScreen(next);
  }

  const settingsLink = (
    <Link
      href={signedIn ? SETTINGS : authPageHref("/login", SETTINGS)}
      className={cn(btnSecondary, btnBlock)}
    >
      {signedIn ? M.toSettings : M.loginToSettings}
    </Link>
  );
  const label = (which: Busy, idle: string) => (busy === which ? M.submitting : idle);
  const failure = failed && (
    <div className="mt-6">
      <FormAlert>{M.failed}</FormAlert>
    </div>
  );

  switch (screen.name) {
    case "reading":
      return (
        <AuthShell back="/">
          <div role="status" className="flex flex-col gap-3.5">
            <span className="sr-only">{M.reading}</span>
            <div aria-hidden="true" className={`h-10 w-[70%] ${SKELETON}`} />
            <div aria-hidden="true" className={`h-4.5 w-[92%] ${SKELETON}`} />
            <div aria-hidden="true" className={`mt-3 h-22 ${SKELETON}`} />
            <div aria-hidden="true" className={`mt-5 h-12 ${SKELETON}`} />
          </div>
        </AuthShell>
      );
    case "unavailable":
      return (
        <Result tone="danger" title={M.unavailableTitle} description={M.unavailableDescription}>
          <Actions>
            <button
              type="button"
              aria-disabled={busy !== null || undefined}
              onClick={() => void retry()}
              className={cn(btnPrimary, btnBlock)}
            >
              {busy === "retry" ? M.retrying : M.retry}
            </button>
          </Actions>
          <Note>{M.invalidNote}</Note>
        </Result>
      );
    case "invalid":
      return (
        <Result tone="danger" title={M.invalidTitle} description={M.invalidDescription}>
          <Actions>{settingsLink}</Actions>
          <Note>{M.invalidNote}</Note>
        </Result>
      );
    case "ready": {
      const { link } = screen;
      return (
        <AuthShell title={M.readyTitle} description={M.readyDescription} back="/">
          <dl className="mt-5 border-t border-b border-t-border-strong border-b-border-subtle">
            <InfoRow term={M.address}>
              <span className="font-mono text-sm">{link.maskedEmail}</span>
            </InfoRow>
            <InfoRow term={M.mail} divided>
              {kindLine(link)}
            </InfoRow>
          </dl>
          {failure}
          <Actions>
            <button
              ref={mainRef}
              type="button"
              aria-disabled={busy !== null || undefined}
              onClick={() => void submit("type")}
              className={cn(btnPrimary, btnBlock)}
            >
              {label("type", link.category === "DIGEST" ? M.unsubscribeDigest : M.unsubscribeType)}
            </button>
            {link.othersEnabled && (
              <button
                type="button"
                aria-disabled={busy !== null || undefined}
                onClick={() => void submit("all")}
                className={cn(btnText, "min-h-8 self-center text-base")}
              >
                {label("all", M.turnOffAll)}
              </button>
            )}
          </Actions>
          <Note>{M.readyNote}</Note>
        </AuthShell>
      );
    }
    case "type-done": {
      if (!fragment) return null;
      const { category } = fragment;
      return (
        <Result
          tone="success"
          title={doneTitle(category, screen.turnedOff === null)}
          description={doneDescription(category, screen.modes, screen.turnedOff)}
        >
          {failure}
          <Actions>
            {screen.othersEnabled && (
              <button
                type="button"
                aria-disabled={busy !== null || undefined}
                onClick={() => void submit("all")}
                className={cn(btnSecondary, btnBlock)}
              >
                {label("all", M.turnOffOthers)}
              </button>
            )}
            {settingsLink}
          </Actions>
          <Note>{M.doneNote}</Note>
        </Result>
      );
    }
    case "all-done":
      return (
        <Result tone="success" title={M.allTitle} description={M.allDescription}>
          <Actions>{settingsLink}</Actions>
          <Note>{M.allNote}</Note>
        </Result>
      );
  }
}

/// 读链接信息 → 屏：这一类已经是关的直接出「已经退订过」
async function readScreen(link: {
  token: string;
  category: UnsubscribeLink["category"];
}): Promise<Screen> {
  const result = await readUnsubscribeLink(link.token, link.category).catch(() => UNAVAILABLE);
  if (!result.ok) {
    return result.reason === "invalid" ? { name: "invalid" } : { name: "unavailable" };
  }
  const { link: info } = result;
  return info.alreadyUnsubscribed
    ? {
        name: "type-done",
        modes: { reply: info.reply, mention: info.mention },
        turnedOff: null,
        othersEnabled: info.othersEnabled,
      }
    : { name: "ready", link: info };
}

/// 一次退订成功之后的屏：关闭全部 → 已关闭全部；这一类真的关掉了 → 已退订；没有关掉任何东西（别处已经关了）
/// → 已经退订过
function afterUnsubscribe(which: "type" | "all", result: UnsubscribeResult): Screen {
  if (which === "all") {
    return { name: "all-done" };
  }
  return {
    name: "type-done",
    modes: { reply: result.reply, mention: result.mention },
    turnedOff: result.turnedOff.length > 0 ? result.turnedOff : null,
    othersEnabled: result.othersEnabled,
  };
}

/// 结果屏：标题上方是状态图标（成功绿色对勾、无效与读失败红色感叹号）
function Result({
  tone,
  title,
  description,
  children,
}: {
  tone: "success" | "danger";
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <AuthShell
      title={title}
      description={description}
      back="/"
      icon={<StateIcon icon={tone === "success" ? CircleCheck : CircleAlert} tone={tone} />}
    >
      {children}
    </AuthShell>
  );
}

/// 信息表的一行：等宽小字的名目 + 内容
function InfoRow({
  term,
  divided = false,
  children,
}: {
  term: string;
  divided?: boolean;
  children: ReactNode;
}) {
  return (
    <div
      className={cn(
        "grid grid-cols-[72px_minmax(0,1fr)] gap-4 py-3 text-base leading-5 text-fg-1",
        divided && "border-t border-border-subtle",
      )}
    >
      <dt className="font-mono text-2xs leading-5 tracking-caps text-fg-3">{term}</dt>
      <dd className="min-w-0 [overflow-wrap:anywhere]">{children}</dd>
    </div>
  );
}

/// 按钮区：整宽按钮竖排，文字按钮居中
function Actions({ children }: { children: ReactNode }) {
  return <div className="mt-8 flex flex-col gap-3.5">{children}</div>;
}

/// 页脚说明：细线之上
function Note({ children }: { children: ReactNode }) {
  return (
    <p className="mt-6 border-t border-border-subtle pt-4 text-sm leading-5 text-fg-3">
      {children}
    </p>
  );
}
