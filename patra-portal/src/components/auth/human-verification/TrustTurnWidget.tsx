"use client";

import { useEffect, useRef, useState } from "react";
import type { HumanVerificationWidgetProps } from "@/components/auth/human-verification/use-human-verification";
import type { HumanVerificationAction } from "@/lib/auth/types";

const ORIGIN = "https://api.trustturn.cc";
/// 浏览器不报 iframe 加载失败，所以用超时兜底：这么久既没收到就绪消息、也没收到票据，就提示加载失败。
/// 收到就绪消息就取消计时，之后用户多久不点都不提示；提示出现后再收到票据，照常交给表单。
/// 不能只等票据：组件是要用户点一下的复选框，用户没点就没有票据，只等票据会把「还没点」误报成加载失败
const LOAD_TIMEOUT_MS = 15_000;
/// 票据 60 秒过期，TRUSTTURN 的接入文档里没有过期事件，所以提前一点作废，并重建 iframe 取新票据
const TICKET_TTL_MS = 55_000;

/// 从组件发来的消息里取票据，形状不对时为 null
function ticketOf(data: unknown): string | null {
  if (typeof data !== "object" || data === null) return null;
  const { type, detail } = data as { type?: unknown; detail?: unknown };
  if (type !== "trustturn:verified" || typeof detail !== "object" || detail === null) return null;
  const ticket = (detail as { verificationTicket?: unknown }).verificationTicket;
  return typeof ticket === "string" ? ticket : null;
}

/// 组件加载完、复选框可以点了。接入文档没写这条消息，是 2026-11 在线上实测看到的：先发 `verifying`，几秒后发 `ready`
function isReady(data: unknown): boolean {
  if (typeof data !== "object" || data === null) return false;
  const { type, state } = data as { type?: unknown; state?: unknown };
  return type === "trustturn:state" && state === "ready";
}

interface TrustTurnWidgetProps extends HumanVerificationWidgetProps {
  siteKey: string;
  action: HumanVerificationAction;
}

/// TRUSTTURN：运营方和数据处理方式都没有公开说明，所以只用 iframe 加 `sandbox`，绝不把它的脚本加载进页面。
/// 不开放顶层跳转和弹窗，它就不能把用户带去别的页面。
///
/// iframe 高度固定为 65px：TRUSTTURN 的组件页里只有一张卡片，页面自己的样式把卡片限定为 65px 高、超出的部分隐藏，
/// 实测各个状态都是这个高度。不设高度时 iframe 用浏览器默认的 150px，下面多出一截空白。
/// 组件页会发 `trustturn:resize` 消息，但里面的高度是 iframe 自身的高度（iframe 低于 120px 时固定报 120），
/// 不是卡片的高度，所以不按它调。
export function TrustTurnWidget({
  siteKey,
  action,
  resetKey,
  onToken,
  onClear,
  onLoadError,
}: TrustTurnWidgetProps) {
  const frame = useRef<HTMLIFrameElement | null>(null);
  const [reloads, setReloads] = useState(0);
  const handlers = useRef({ onToken, onClear, onLoadError });
  handlers.current = { onToken, onClear, onLoadError };
  const instance = `${resetKey}-${reloads}`;

  // biome-ignore lint/correctness/useExhaustiveDependencies: instance 变了 iframe 就换了，计时要重来
  useEffect(() => {
    let ticketTimer: ReturnType<typeof setTimeout> | undefined;
    const loadTimer = setTimeout(() => handlers.current.onLoadError(), LOAD_TIMEOUT_MS);
    function onMessage(event: MessageEvent) {
      if (event.origin !== ORIGIN || event.source !== frame.current?.contentWindow) return;
      if (isReady(event.data)) {
        clearTimeout(loadTimer);
        return;
      }
      const ticket = ticketOf(event.data);
      if (ticket === null) return;
      clearTimeout(loadTimer);
      clearTimeout(ticketTimer);
      handlers.current.onToken(ticket);
      ticketTimer = setTimeout(() => {
        handlers.current.onClear();
        setReloads((count) => count + 1);
      }, TICKET_TTL_MS);
    }
    window.addEventListener("message", onMessage);
    return () => {
      window.removeEventListener("message", onMessage);
      clearTimeout(loadTimer);
      clearTimeout(ticketTimer);
    };
  }, [instance]);

  const query = new URLSearchParams({ site_key: siteKey, action });
  return (
    <iframe
      key={instance}
      ref={frame}
      title="TRUSTTURN 人机验证"
      src={`${ORIGIN}/widget.html?${query}`}
      sandbox="allow-scripts allow-same-origin allow-forms"
      className="h-[65px] w-full border-0"
    />
  );
}
