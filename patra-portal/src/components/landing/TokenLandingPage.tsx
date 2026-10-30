"use client";

import { CircleAlert, CircleCheck } from "lucide-react";
import { type FormEvent, type ReactNode, useEffect, useRef, useState } from "react";
import { AuthShell } from "@/components/auth/AuthShell";
import { FormAlert } from "@/components/auth/FormAlert";
import { StateIcon } from "@/components/auth/StateIcon";
import { readFragmentToken } from "@/lib/identity/email-token";
import { btnBlock, btnDanger, btnPrimary } from "@/lib/portal-ui";
import { useFragmentOnce } from "@/lib/use-fragment-once";
import { cn } from "@/lib/utils";

/// 一次提交的结果：成功（带数据）、链接无效、冲突（带数据）、服务错误（回就绪态 + 通用提示条）、
/// 留在就绪态（页面已自己显示了错误，比如重设密码的字段错误与限流）、页面要跳走了（重设成功去登录页：按钮
/// 保持进行中，不能再点）
export type LandingOutcome<S, C = never> =
  | { kind: "succeeded"; data: S }
  | { kind: "invalid" }
  | { kind: "conflict"; data: C }
  | { kind: "unavailable" }
  | { kind: "stay" }
  | { kind: "leaving" };

/// 结果屏：成功是绿色对勾，无效与冲突是红色感叹号；`actions` 是下一步按钮
export interface LandingScreen {
  tone: "success" | "danger";
  title: string;
  description: ReactNode;
  actions?: ReactNode;
}

interface TokenLandingPageProps<S, C> {
  title: string;
  description: ReactNode;
  actionLabel: string;
  pendingLabel: string;
  /// 主按钮用危险色（撤销改邮箱）
  danger?: boolean;
  /// 就绪态里说明与按钮之间的内容（撤销页的说明列表、重设页的密码字段），与按钮在同一个表单里。传函数时拿到
  /// 「提交中」，字段据此只读（简报 §6）
  children?: ReactNode | ((busy: boolean) => ReactNode);
  /// 就绪态把焦点放在主按钮上；重设密码页由自己的第一个输入框接管，传 false
  focusAction?: boolean;
  /// 点主按钮后提交令牌
  submit: (token: string) => Promise<LandingOutcome<S, C>>;
  renderSucceeded: (data: S) => LandingScreen;
  renderInvalid: () => LandingScreen;
  renderConflict?: (data: C) => LandingScreen;
}

/// 页面所处的阶段
type Phase<S, C> =
  | { name: "reading" }
  | { name: "ready"; token: string; failed: boolean }
  | { name: "submitting"; token: string }
  | { name: "succeeded"; data: S }
  | { name: "invalid" }
  | { name: "conflict"; data: C };

/// 四个邮件落地页共用的状态机（spec §8.3，简报 §3.18）。首次渲染（服务端与浏览器一致）是「读取中」，挂载后
/// 从片段读令牌并立即抹掉（`useFragmentOnce`；刷新后就是「链接不完整」→ 无效屏）。
/// 打开页面不发请求，点按钮才提交，避免邮件安全网关预取把一次性令牌用掉。
export function TokenLandingPage<S, C = never>({
  title,
  description,
  actionLabel,
  pendingLabel,
  danger = false,
  children,
  focusAction = true,
  submit,
  renderSucceeded,
  renderInvalid,
  renderConflict,
}: TokenLandingPageProps<S, C>) {
  const [phase, setPhase] = useState<Phase<S, C>>({ name: "reading" });
  const actionRef = useRef<HTMLButtonElement | null>(null);
  const token = useFragmentOnce(readFragmentToken);

  useEffect(() => {
    if (token !== undefined) {
      setPhase(token ? { name: "ready", token, failed: false } : { name: "invalid" });
    }
  }, [token]);

  const ready = phase.name === "ready";
  useEffect(() => {
    if (ready && focusAction) actionRef.current?.focus();
  }, [ready, focusAction]);

  /// 提交令牌，按结果换屏
  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (phase.name !== "ready") return;
    const { token } = phase;
    setPhase({ name: "submitting", token });
    const outcome = await submit(token).catch(() => ({ kind: "unavailable" }) as const);
    switch (outcome.kind) {
      case "succeeded":
        setPhase({ name: "succeeded", data: outcome.data });
        return;
      case "conflict":
        setPhase({ name: "conflict", data: outcome.data });
        return;
      case "invalid":
        setPhase({ name: "invalid" });
        return;
      case "leaving":
        return;
      default:
        setPhase({ name: "ready", token, failed: outcome.kind === "unavailable" });
    }
  }

  const screen =
    phase.name === "succeeded"
      ? renderSucceeded(phase.data)
      : phase.name === "invalid"
        ? renderInvalid()
        : phase.name === "conflict" && renderConflict
          ? renderConflict(phase.data)
          : null;

  if (screen) {
    return (
      <AuthShell
        title={screen.title}
        description={screen.description}
        back="/"
        icon={
          screen.tone === "success" ? (
            <StateIcon icon={CircleCheck} tone="success" />
          ) : (
            <StateIcon icon={CircleAlert} tone="danger" />
          )
        }
      >
        {screen.actions && <div className="mt-8 flex flex-col gap-3">{screen.actions}</div>}
      </AuthShell>
    );
  }

  const busy = phase.name !== "ready";
  return (
    <AuthShell title={title} description={description} back="/">
      <form
        // 页面还没接管表单就提交时（脚本没加载完），浏览器按这里的 method 提交：用 POST，重设密码的新密码不进地址栏
        method="post"
        noValidate
        onSubmit={onSubmit}
        className="mt-6 flex flex-col gap-5"
      >
        {phase.name === "ready" && phase.failed && <FormAlert>没有成功，请稍后再试</FormAlert>}
        {typeof children === "function" ? children(busy) : children}
        <button
          ref={actionRef}
          type="submit"
          aria-disabled={busy || undefined}
          className={cn(danger ? btnDanger : btnPrimary, btnBlock, "mt-2")}
        >
          {phase.name === "submitting" ? pendingLabel : actionLabel}
        </button>
      </form>
    </AuthShell>
  );
}
