"use client";

import { type MouseEvent, type ReactNode, useEffect, useRef } from "react";
import { useTrackedList } from "@/components/analytics/TrackedList";
import { createImpressionTimer } from "@/lib/analytics/client/impression";
import { track } from "@/lib/analytics/client/queue";

/// 列表里的一张文献卡片（埋点 spec §5.2）：曝光满足规则时记 Article Impressed，点到这篇文献的链接时记
/// Article Clicked。外层是 `display: contents`，不改变卡片的排版；观察的是卡片本身（第一个子元素）
export function TrackedArticle({
  articleId,
  position,
  children,
}: {
  articleId: string;
  position: number;
  children: ReactNode;
}) {
  const list = useTrackedList();
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const card = ref.current?.firstElementChild;
    if (!list || !card || typeof IntersectionObserver !== "function") {
      return;
    }
    const timer = createImpressionTimer({
      onImpress: () => {
        if (list.impressed.has(articleId)) {
          return;
        }
        list.impressed.add(articleId);
        track("article_impressed", {
          article_id: articleId,
          list_type: list.listType,
          list_id: list.listId,
          position,
        });
      },
      setTimer: (fn, ms) => window.setTimeout(fn, ms),
      clearTimer: (handle) => window.clearTimeout(handle as number),
    });
    const onVisibility = () => timer.visibility(document.visibilityState === "visible");
    onVisibility();
    document.addEventListener("visibilitychange", onVisibility);
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          timer.ratio(entry.intersectionRatio);
        }
      },
      { threshold: [0, 0.5] },
    );
    observer.observe(card);
    return () => {
      observer.disconnect();
      document.removeEventListener("visibilitychange", onVisibility);
      timer.dispose();
    };
  }, [list, articleId, position]);

  function onClickCapture(event: MouseEvent<HTMLDivElement>) {
    if (!list || !(event.target instanceof Element)) {
      return;
    }
    const href = event.target.closest("a")?.getAttribute("href")?.split(/[?#]/)[0];
    if (href === `/papers/${articleId}`) {
      track("article_clicked", {
        article_id: articleId,
        list_type: list.listType,
        list_id: list.listId,
        position,
      });
    }
  }

  return (
    <div ref={ref} className="contents" onClickCapture={onClickCapture}>
      {children}
    </div>
  );
}
