"use client";

import { useEffect, useRef, useState } from "react";
import type { HumanVerificationWidgetProps } from "@/components/auth/human-verification/use-human-verification";
import type { HumanVerificationAction } from "@/lib/auth/types";

const ORIGIN = "https://api.trustturn.cc";
/// 浏览器不报 iframe 加载失败，这么久没收到票据就提示；交互式验证可能确实慢，之后收到了照常用
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

interface TrustTurnWidgetProps extends HumanVerificationWidgetProps {
  siteKey: string;
  action: HumanVerificationAction;
}

/// TRUSTTURN：运营方和数据处理方式都没有公开说明，所以只用 iframe 加 `sandbox`，绝不把它的脚本加载进页面。
/// 不开放顶层跳转和弹窗，它就不能把用户带去别的页面。
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
      className="min-h-22 w-full border-0"
    />
  );
}
