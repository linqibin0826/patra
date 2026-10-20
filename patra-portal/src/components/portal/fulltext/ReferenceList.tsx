"use client";

import { type ReactNode, useEffect, useState } from "react";
import { flushSync } from "react-dom";
import { cn } from "@/lib/utils";

export interface ReferenceListItem {
  /** 条目的 DOM id（`ref-<JATS id>`） */
  anchorId: string;
  /** JATS 里的参考文献 id，正文引用按它对应；缺失时不可被引用 */
  refId: string | null;
  label: string;
  /** 著录文本（照原文，已渲染） */
  content: ReactNode;
  doi: string | null;
  pmid: string | null;
}

interface ReferenceListProps {
  items: ReferenceListItem[];
  /** 参考文献 id → 正文里第一次引用它的锚点 id */
  firstCiteByRef: Record<string, string>;
}

/** 不超过这个数直接全显示；超过时默认只显示前 COLLAPSED_COUNT 条（简报 §3.4「参考文献」）。 */
const SHOW_ALL_UP_TO = 8;
const COLLAPSED_COUNT = 5;
/** 目标条目亮起的时长；减弱动效时常亮到下一次点击 */
const HIGHLIGHT_MS = 1600;

const LINK = "link-draw text-link hover:text-link-hover";

/** 地址栏 hash 解码；畸形的百分号编码（如 `#%E0`）按原样返回，不让整个列表因 URIError 挂掉。 */
function decodeHash(hash: string): string {
  try {
    return decodeURIComponent(hash);
  } catch {
    return hash;
  }
}

/**
 * 参考文献：前 5 条 + 「展开全部 N 条」（展开后不再收起）。
 * 点正文引用时（事件委托，正文是服务端渲染的普通链接）：记下来源引用、需要时先展开，
 * 再交给浏览器的锚点跳转（平滑滚动由全站 `scroll-behavior` 决定），目标条目亮起。
 * 每条末尾「↩ 回到正文」回到刚才点的引用；没点过就回到第一次被引用的位置。
 */
export function ReferenceList({ items, firstCiteByRef }: ReferenceListProps) {
  const collapsible = items.length > SHOW_ALL_UP_TO;
  const [expanded, setExpanded] = useState(false);
  const [highlighted, setHighlighted] = useState<string | null>(null);
  const [lastCiteByRef, setLastCiteByRef] = useState<Record<string, string>>({});
  const collapsed = collapsible && !expanded;

  useEffect(() => {
    const indexOfAnchor = new Map(items.map((item, i) => [item.anchorId, i]));
    const isCollapsedPart = (anchorId: string) =>
      (indexOfAnchor.get(anchorId) ?? -1) >= COLLAPSED_COUNT;

    // 直接打开 #ref-N 进来，而该条在收起部分
    const opened = decodeHash(window.location.hash.slice(1));
    let frame: number | undefined;
    if (isCollapsedPart(opened)) {
      setExpanded(true);
      // 页面加载时浏览器找不到隐藏的条目，展开后补一次滚动
      frame = requestAnimationFrame(() => document.getElementById(opened)?.scrollIntoView?.());
    }

    let timer: ReturnType<typeof setTimeout> | undefined;
    const onClick = (event: MouseEvent) => {
      const link = (event.target as Element | null)?.closest?.("a[data-cite-ref]");
      if (!(link instanceof HTMLAnchorElement)) return;
      const refId = link.dataset.citeRef ?? "";
      const anchorId = link.hash.slice(1);
      setLastCiteByRef((prev) => ({ ...prev, [refId]: link.id }));
      if (isCollapsedPart(anchorId)) {
        // 同步提交：点击的默认动作（锚点跳转）紧接在监听器之后，那时目标条目必须已经显示
        flushSync(() => setExpanded(true));
      }
      setHighlighted(anchorId);
      clearTimeout(timer);
      if (!window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) {
        timer = setTimeout(() => setHighlighted(null), HIGHLIGHT_MS);
      }
    };
    document.addEventListener("click", onClick);
    return () => {
      document.removeEventListener("click", onClick);
      clearTimeout(timer);
      if (frame !== undefined) cancelAnimationFrame(frame);
    };
  }, [items]);

  return (
    <section id="references" aria-labelledby="references-title" className="mt-14 scroll-mt-[45px]">
      <h2
        id="references-title"
        className="m-0 mb-4 flex scroll-mt-[45px] items-baseline gap-3 font-serif text-2xl font-medium tracking-tight text-fg-1"
      >
        参考文献
        <span className="font-mono text-xs tracking-mono text-fg-3 tabular-nums">
          {items.length}
        </span>
      </h2>
      <ol aria-label="参考文献" className="m-0 list-none border-t border-border-subtle p-0">
        {items.map((item, i) => {
          const back = item.refId
            ? (lastCiteByRef[item.refId] ?? firstCiteByRef[item.refId])
            : undefined;
          return (
            <li
              key={item.anchorId}
              id={item.anchorId}
              hidden={collapsed && i >= COLLAPSED_COUNT}
              data-highlighted={highlighted === item.anchorId || undefined}
              className={cn(
                "grid scroll-mt-[62px] grid-cols-[2.5em_1fr] gap-3 rounded-sm border-b border-border-subtle py-3 pr-2 transition-colors duration-600",
                "data-highlighted:bg-accent-tint",
              )}
            >
              <span className="pt-[3px] text-right font-mono text-xs text-fg-3 tabular-nums">
                {item.label}
              </span>
              <div>
                <p className="m-0 font-serif text-md leading-[1.55] break-words text-fg-2">
                  {item.content}
                </p>
                {(item.doi || item.pmid || back) && (
                  <p className="m-0 mt-1 flex flex-wrap gap-x-3.5 gap-y-1 font-mono text-2xs tracking-mono">
                    {item.doi && (
                      <a
                        className={LINK}
                        href={`https://doi.org/${item.doi}`}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        DOI ↗
                      </a>
                    )}
                    {item.pmid && (
                      <a
                        className={LINK}
                        href={`https://pubmed.ncbi.nlm.nih.gov/${item.pmid}/`}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        PubMed ↗
                      </a>
                    )}
                    {back && (
                      <a className="link-draw text-fg-3 hover:text-fg-1" href={`#${back}`}>
                        ↩ 回到正文
                      </a>
                    )}
                  </p>
                )}
              </div>
            </li>
          );
        })}
      </ol>
      {collapsed && (
        <button
          type="button"
          onClick={() => setExpanded(true)}
          className="mt-4 inline-flex items-center rounded-full border border-border-default bg-paper-50 px-4 py-1.5 font-sans text-sm font-semibold text-fg-1 hover:border-border-hover hover:bg-paper-200"
        >
          展开全部 {items.length} 条
        </button>
      )}
    </section>
  );
}
