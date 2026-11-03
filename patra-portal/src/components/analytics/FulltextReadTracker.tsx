"use client";

import { type ReactNode, useEffect, useRef } from "react";
import { createDwellTracker, isReadingVisible } from "@/lib/analytics/client/dwell";
import { track } from "@/lib/analytics/client/queue";

/// 全文累计在读 10 秒算一次全文阅读（埋点方案 §6.3）
const THRESHOLD_MS = 10_000;

/// 包住全文正文（`FullTextBody` 渲染出的部分），累计在读满 10 秒时记一次 Fulltext Read，同一个页面实例只记一次。
/// 外层是 `display: contents`，不改变正文排版；正文的位置取第一个到最后一个子元素合起来的范围。换一篇文献时由
/// 调用方用 `key` 重新挂载
export function FulltextReadTracker({
  articleId,
  children,
}: {
  articleId: string;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const root = ref.current;
    if (!root) {
      return;
    }
    const dwell = createDwellTracker({
      thresholdMs: THRESHOLD_MS,
      onReach: (seconds) =>
        track("fulltext_read", { article_id: articleId, visible_seconds: seconds }),
      now: () => performance.now(),
      setTimer: (fn, ms) => window.setTimeout(fn, ms),
      clearTimer: (handle) => window.clearTimeout(handle as number),
    });
    const measure = () => {
      const first = root.firstElementChild;
      const last = root.lastElementChild;
      if (!first || !last || document.visibilityState !== "visible") {
        dwell.reading(false);
        return;
      }
      dwell.reading(
        isReadingVisible(
          { top: first.getBoundingClientRect().top, bottom: last.getBoundingClientRect().bottom },
          window.innerHeight,
        ),
      );
    };
    let frame: number | null = null;
    const schedule = () => {
      if (frame === null) {
        frame = window.requestAnimationFrame(() => {
          frame = null;
          measure();
        });
      }
    };
    measure();
    window.addEventListener("scroll", schedule, { passive: true });
    window.addEventListener("resize", schedule, { passive: true });
    document.addEventListener("visibilitychange", measure);
    return () => {
      window.removeEventListener("scroll", schedule);
      window.removeEventListener("resize", schedule);
      document.removeEventListener("visibilitychange", measure);
      if (frame !== null) {
        window.cancelAnimationFrame(frame);
      }
      dwell.dispose();
    };
  }, [articleId]);

  return (
    <div ref={ref} className="contents">
      {children}
    </div>
  );
}
