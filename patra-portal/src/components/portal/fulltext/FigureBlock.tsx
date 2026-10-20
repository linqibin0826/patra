"use client";

import { Dialog } from "@base-ui/react/dialog";
import { ExternalLink, X } from "lucide-react";
import { type ReactNode, useState } from "react";
import { RailRow } from "@/components/portal/RailRow";
import type { FigureLayout } from "@/lib/portal-api/fulltext-derive";
import { cn } from "@/lib/utils";

interface FigureBlockProps {
  /** 图块的页内锚点（交叉引用 "see Figure 2" 跳到这里） */
  anchorId: string | undefined;
  /** 原文标签，如 `Figure 1`（照原文，不翻译；末尾句点由调用方去掉） */
  label: string | null;
  /** 图题纯文本：图片 alt 与放大层的可访问名称 */
  altText: string;
  /** 图题（图注第一段） */
  title: ReactNode;
  /** 图注其余段落 */
  caption: ReactNode;
  /** 同源图片地址；匹配不到图片文件时为 null，只显示图注 */
  src: string | null;
  width: number | null;
  height: number | null;
  /** 按宽高比选出的宽度分类（`figureLayout`） */
  layout: FigureLayout;
}

const FRAME_WIDTH: Record<FigureLayout, string> = {
  text: "max-w-[612px]",
  portrait: "max-w-[612px]",
  wide: "max-w-[772px]",
  "wide-zoom": "max-w-[772px]",
};

/**
 * 正文图块（spec §5.8）：白底图框按宽高比取宽（正文宽 612 / 加宽 772，竖图在正文栏内居中、最高 640px），
 * 图片带原图宽高属性按比例占位、到了再淡入，不放大超过原图。图注挂在标签栏：标签格放原文标签，
 * 右侧「放大查看 · 原图 ↗」（超宽图常显，其余悬停或聚焦图框时出现）。点图片或「放大查看」打开放大层。
 */
export function FigureBlock({
  anchorId,
  label,
  altText,
  title,
  caption,
  src,
  width,
  height,
  layout,
}: FigureBlockProps) {
  const [loaded, setLoaded] = useState(false);
  const name = label || altText || "图";
  const wide = layout === "wide" || layout === "wide-zoom";

  const body = (
    <>
      {src && (
        <RailRow wide={wide}>
          <div
            data-layout={layout}
            className={cn(
              "w-full rounded-lg border border-border-subtle bg-paper-50 p-3",
              FRAME_WIDTH[layout],
            )}
          >
            <Dialog.Trigger
              aria-label={`放大查看 ${name}`}
              className={cn(
                "flex w-full cursor-zoom-in justify-center rounded-md",
                !loaded && "shimmer",
              )}
            >
              {/* biome-ignore lint/performance/noImgElement: 同源 Route Handler 转发的原图，不走 next/image 的二次压缩 */}
              <img
                ref={(el) => {
                  // 服务端渲染的图片可能在水合之前就加载完了，onLoad 不会再触发
                  if (el?.complete) setLoaded(true);
                }}
                src={src}
                alt={altText || name}
                width={width ?? undefined}
                height={height ?? undefined}
                loading="lazy"
                decoding="async"
                onLoad={() => setLoaded(true)}
                className={cn(
                  "block h-auto max-w-full object-contain transition-opacity duration-300 max-sm:max-h-[360px]",
                  layout === "portrait" && "max-h-[640px]",
                  loaded ? "opacity-100" : "opacity-0",
                )}
              />
            </Dialog.Trigger>
          </div>
        </RailRow>
      )}
      <figcaption className="mt-3.5">
        <RailRow
          label={
            label && (
              <span className="font-mono text-2xs font-medium tracking-mono text-clay-700">
                {label}
              </span>
            )
          }
        >
          <div className="flex max-w-[612px] flex-wrap items-baseline justify-between gap-x-4 gap-y-1.5">
            <div className="min-w-0 flex-1">
              <span className="block font-serif text-md leading-normal text-fg-2">{title}</span>
              <div className="mt-1 font-sans text-sm leading-normal text-fg-2 [&_p]:m-0 [&_p+p]:mt-1">
                {caption}
              </div>
            </div>
            {src && (
              <span
                className={cn(
                  "shrink-0 font-mono text-2xs tracking-mono text-fg-2 transition-opacity duration-200",
                  layout !== "wide-zoom" &&
                    "opacity-0 group-focus-within/fig:opacity-100 group-hover/fig:opacity-100",
                )}
              >
                <Dialog.Trigger className="link-draw cursor-zoom-in hover:text-fg-1">
                  放大查看
                </Dialog.Trigger>
                <span aria-hidden> · </span>
                <a
                  href={src}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="link-draw hover:text-fg-1"
                >
                  原图 ↗
                </a>
              </span>
            )}
          </div>
        </RailRow>
      </figcaption>
    </>
  );

  return (
    <figure id={anchorId} className="group/fig mx-0 my-10 scroll-mt-[45px]">
      {src ? (
        <Dialog.Root>
          {body}
          <FigureLightbox name={name} src={src} altText={altText} title={title} />
        </Dialog.Root>
      ) : (
        body
      )}
    </figure>
  );
}

/** 放大层：ink-900 · 70% 遮罩 + 背景模糊（与快速检索同一层级语言）；移动端全屏，原尺寸图放在可滚动容器里用系统双指缩放。 */
function FigureLightbox({
  name,
  src,
  altText,
  title,
}: {
  name: string;
  src: string;
  altText: string;
  title: ReactNode;
}) {
  return (
    <Dialog.Portal>
      <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink-900/70 backdrop-blur-sm transition-opacity duration-300 data-ending-style:opacity-0 data-starting-style:opacity-0 max-sm:bg-ink-900" />
      <Dialog.Popup className="fixed inset-0 z-50 flex flex-col items-center justify-center gap-4 p-10 transition-opacity duration-300 data-ending-style:opacity-0 data-starting-style:opacity-0 max-sm:justify-start max-sm:px-0 max-sm:pt-16 max-sm:pb-14">
        <Dialog.Title className="sr-only">{name}</Dialog.Title>
        <Dialog.Close
          aria-label="关闭"
          className="absolute top-4 right-4 flex size-11 items-center justify-center rounded-full border border-paper-50/25 bg-ink-900/40 text-paper-50"
        >
          <X size={20} aria-hidden />
        </Dialog.Close>
        <div className="flex max-h-full min-h-0 max-w-full items-center justify-center max-sm:block max-sm:w-full max-sm:flex-1 max-sm:overflow-auto">
          {/* biome-ignore lint/performance/noImgElement: 放大层显示原图 */}
          <img
            src={src}
            alt={altText || name}
            className="max-h-[calc(100vh-200px)] max-w-full rounded-md bg-paper-50 object-contain max-sm:max-h-none max-sm:max-w-none"
          />
        </div>
        <p className="m-0 max-w-[720px] text-center font-serif text-md leading-normal text-paper-50 max-sm:px-4">
          {title}
        </p>
        <a
          href={src}
          target="_blank"
          rel="noopener noreferrer"
          className="absolute bottom-5 left-6 inline-flex items-center gap-1 text-sm text-clay-300 hover:text-clay-200"
        >
          在新标签打开原图 <ExternalLink size={13} aria-hidden />
        </a>
      </Dialog.Popup>
    </Dialog.Portal>
  );
}
