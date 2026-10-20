"use client";

import { Popover } from "@base-ui/react/popover";
import { useEffect, useMemo, useRef, useState } from "react";
import type { ReferenceListItem } from "@/components/portal/fulltext/ReferenceList";

const LINK = "link-draw text-link hover:text-link-hover";

interface OpenCitation {
  anchor: HTMLAnchorElement;
  refIds: string[];
}

/**
 * 引用卡片（spec §5.7）：点正文里的引用号，在引用号下方弹出参考文献卡片，不直接跳到文末。
 * 正文是服务端渲染的普通链接，这里在 document 上用捕获阶段委托：接管所有带 `id` 的 `a[data-cite-ref]`
 * （正文、声明区、附录里的引用号；卡片自己的「跳到文末」带 `data-cite-from`、没有 id，不接管），
 * 调用 preventDefault，参考文献列表的委托看到后就不再处理这次点击。
 * 卡片里的「跳到文末」是普通的 `#ref-…` 链接，带 `data-cite-from`，交给参考文献列表展开、高亮、记来源；
 * 从这里关闭时焦点不回引用号（视口已到参考文献）。Esc 或点外面关闭，焦点回到引用号；
 * 没有 JS 时引用号仍是指向文末条目的链接。
 */
export function CitationPopover({ items }: { items: ReferenceListItem[] }) {
  const [open, setOpen] = useState<OpenCitation | null>(null);
  const anchorRef = useRef<HTMLAnchorElement | null>(null);
  /** 是否经「跳到文末」关闭：这时焦点留给跳转目标，不回引用号 */
  const jumpedRef = useRef(false);
  const byRefId = useMemo(
    () => new Map(items.flatMap((item) => (item.refId ? [[item.refId, item] as const] : []))),
    [items],
  );

  useEffect(() => {
    const onClick = (event: MouseEvent) => {
      const link = (event.target as Element | null)?.closest?.("a[data-cite-ref][id]");
      if (!(link instanceof HTMLAnchorElement)) return;
      const refIds = (link.dataset.citeRefs || link.dataset.citeRef || "")
        .split(" ")
        .filter((id) => byRefId.has(id));
      if (refIds.length === 0) return;
      event.preventDefault();
      anchorRef.current = link;
      jumpedRef.current = false;
      setOpen({ anchor: link, refIds });
    };
    document.addEventListener("click", onClick, true);
    return () => document.removeEventListener("click", onClick, true);
  }, [byRefId]);

  const close = () => setOpen(null);
  const entries = open?.refIds.flatMap((id) => byRefId.get(id) ?? []) ?? [];

  return (
    <Popover.Root
      open={open !== null}
      onOpenChange={(next) => {
        if (!next) close();
      }}
    >
      <Popover.Portal>
        <Popover.Positioner
          anchor={open?.anchor ?? null}
          side="bottom"
          align="start"
          sideOffset={6}
          collisionPadding={16}
          className="z-50"
        >
          <Popover.Popup
            finalFocus={() => (jumpedRef.current ? false : anchorRef.current)}
            className="flex max-h-[min(60vh,420px)] w-[340px] max-w-[calc(100vw-32px)] flex-col gap-4 overflow-y-auto rounded-xl border border-border-default bg-bg-elevated p-4 shadow-lg outline-none"
          >
            <Popover.Title className="sr-only">参考文献</Popover.Title>
            {entries.map((item) => (
              <div
                key={item.anchorId}
                className="flex flex-col border-t border-border-subtle pt-4 first:border-t-0 first:pt-0"
              >
                <span className="font-mono text-3xs tracking-mono text-fg-3">
                  参考文献 {item.label}
                </span>
                <p className="m-0 mt-1.5 font-serif text-md leading-[1.5] break-words text-fg-1">
                  {item.content}
                </p>
                <p className="m-0 mt-2.5 flex flex-wrap gap-x-3.5 gap-y-1 border-t border-border-subtle pt-2.5 font-mono text-2xs tracking-mono">
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
                  <a
                    className="link-draw ml-auto text-fg-2 hover:text-fg-1"
                    href={`#${item.anchorId}`}
                    data-cite-ref={item.refId ?? undefined}
                    data-cite-from={open?.anchor.id}
                    onClick={() => {
                      jumpedRef.current = true;
                      close();
                    }}
                  >
                    跳到文末
                  </a>
                </p>
              </div>
            ))}
          </Popover.Popup>
        </Popover.Positioner>
      </Popover.Portal>
    </Popover.Root>
  );
}
