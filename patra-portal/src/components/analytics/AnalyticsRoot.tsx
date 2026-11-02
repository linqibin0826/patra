"use client";

import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";
import { flushWithBeacon, track } from "@/lib/analytics/client/queue";

/// 全站的浏览器埋点入口（埋点 spec §5.1、§6.1），挂在根布局里，不渲染任何东西：
/// - 首次加载与每次路径变化记一条 Page Viewed（只改查询串不算）；页面在后台时等第一次可见再记，可见之前又换了
///   路径就只记当前路径
/// - 页面变为隐藏时用 sendBeacon 把队列里剩下的发出，`pagehide` 兜底（有的浏览器关标签页时不触发 visibilitychange）
export function AnalyticsRoot() {
  const pathname = usePathname();
  /// 已记过 Page Viewed 的路径（StrictMode 重复执行 effect 时不重复记）
  const sent = useRef<string | null>(null);
  /// 页面不可见时进入的路径，等可见再记
  const pending = useRef<string | null>(null);

  useEffect(() => {
    if (sent.current === pathname) {
      return;
    }
    if (document.visibilityState === "visible") {
      sent.current = pathname;
      pending.current = null;
      track("page_viewed", {});
    } else {
      pending.current = pathname;
    }
  }, [pathname]);

  useEffect(() => {
    const onVisibilityChange = () => {
      if (document.visibilityState !== "visible") {
        flushWithBeacon();
        return;
      }
      if (pending.current !== null) {
        sent.current = pending.current;
        pending.current = null;
        track("page_viewed", {});
      }
    };
    document.addEventListener("visibilitychange", onVisibilityChange);
    window.addEventListener("pagehide", flushWithBeacon);
    return () => {
      document.removeEventListener("visibilitychange", onVisibilityChange);
      window.removeEventListener("pagehide", flushWithBeacon);
    };
  }, []);

  return null;
}
