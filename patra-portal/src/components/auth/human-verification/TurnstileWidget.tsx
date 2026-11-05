"use client";

import { useEffect, useRef } from "react";
import type { HumanVerificationWidgetProps } from "@/components/auth/human-verification/use-human-verification";
import type { HumanVerificationAction } from "@/lib/auth/types";

const SCRIPT_URL = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
/// 这么久还没渲染出来，就算加载失败
const LOAD_TIMEOUT_MS = 15_000;

/// 官方脚本里用到的部分
interface TurnstileApi {
  render: (
    element: HTMLElement,
    options: {
      sitekey: string;
      action: string;
      language: string;
      size: "flexible";
      callback: (token: string) => void;
      "expired-callback": () => void;
      "error-callback": () => void;
    },
  ) => string;
  reset: (id: string) => void;
  remove: (id: string) => void;
}

declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

/// 进行中的加载。失败时清空，下次挂载才能重试
let loading: Promise<TurnstileApi> | null = null;

/// 加载官方脚本，整个页面只注入一次。
function loadScript(): Promise<TurnstileApi> {
  loading ??= new Promise<TurnstileApi>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = SCRIPT_URL;
    script.async = true;
    script.onload = () => {
      if (window.turnstile) {
        resolve(window.turnstile);
      } else {
        script.remove();
        reject(new Error("脚本加载后没有 turnstile"));
      }
    };
    script.onerror = () => {
      script.remove();
      reject(new Error("脚本加载失败"));
    };
    document.head.appendChild(script);
  }).catch((error: unknown) => {
    loading = null;
    throw error;
  });
  return loading;
}

interface TurnstileWidgetProps extends HumanVerificationWidgetProps {
  siteKey: string;
  action: HumanVerificationAction;
}

/// Cloudflare Turnstile：挂载时才加载脚本，所以不要求验证的页面不会碰第三方资源。
export function TurnstileWidget({
  siteKey,
  action,
  resetKey,
  onToken,
  onClear,
  onLoadError,
}: TurnstileWidgetProps) {
  const container = useRef<HTMLDivElement | null>(null);
  const widget = useRef<{ api: TurnstileApi; id: string } | null>(null);
  // 回调每次渲染都是新的，经 ref 取最新的，不让组件反复重建
  const handlers = useRef({ onToken, onClear, onLoadError });
  handlers.current = { onToken, onClear, onLoadError };

  useEffect(() => {
    let active = true;
    const timer = setTimeout(() => handlers.current.onLoadError(), LOAD_TIMEOUT_MS);
    loadScript()
      .then((api) => {
        if (!active || !container.current) return;
        clearTimeout(timer);
        const id = api.render(container.current, {
          sitekey: siteKey,
          action,
          language: "zh-cn",
          size: "flexible",
          callback: (token) => handlers.current.onToken(token),
          "expired-callback": () => handlers.current.onClear(),
          "error-callback": () => handlers.current.onLoadError(),
        });
        widget.current = { api, id };
      })
      .catch(() => {
        if (!active) return;
        clearTimeout(timer);
        handlers.current.onLoadError();
      });
    return () => {
      active = false;
      clearTimeout(timer);
      widget.current?.api.remove(widget.current.id);
      widget.current = null;
    };
  }, [siteKey, action]);

  const lastResetKey = useRef(resetKey);
  useEffect(() => {
    if (lastResetKey.current !== resetKey) {
      lastResetKey.current = resetKey;
      widget.current?.api.reset(widget.current.id);
    }
  }, [resetKey]);

  // 组件要几秒才渲染出来，预留高度，免得提交按钮被顶下去
  return <div ref={container} className="min-h-17.5 w-full" />;
}
