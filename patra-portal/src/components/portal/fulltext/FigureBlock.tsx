"use client";

import { Dialog } from "@base-ui/react/dialog";
import { ExternalLink, X } from "lucide-react";
import { type ReactNode, useState } from "react";
import { cn } from "@/lib/utils";

interface FigureBlockProps {
  /** 图块的页内锚点（交叉引用 "see Figure 2" 跳到这里） */
  anchorId: string | undefined;
  /** 原文标签，如 `Figure 1`（照原文，不翻译） */
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
}

/**
 * 正文图块（简报 §3.4「图」）：主栏满宽的纸面托盘，图片限高 `min(70vh, 560px)` 按比例缩放；
 * 有宽高时按比例占位、到了再淡入。点图片打开放大层（焦点困在层内，Esc / 点遮罩关闭，关后焦点回到原图）。
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
}: FigureBlockProps) {
  const [loaded, setLoaded] = useState(false);
  const name = label || altText || "图";

  return (
    <figure id={anchorId} className="mx-0 my-8 scroll-mt-[45px]">
      {src && (
        <Dialog.Root>
          <Dialog.Trigger
            aria-label={`放大查看 ${name}`}
            className={cn(
              "flex w-full cursor-zoom-in justify-center rounded-lg border border-border-subtle bg-bg-paper p-4",
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
                "block h-auto max-h-[min(70vh,560px)] max-w-full object-contain transition-opacity duration-300 max-sm:max-h-[360px]",
                loaded ? "opacity-100" : "opacity-0",
              )}
            />
          </Dialog.Trigger>
          <FigureLightbox name={name} src={src} altText={altText} title={title} />
        </Dialog.Root>
      )}
      <figcaption className="mt-3 max-w-[40em]">
        {label && (
          <span className="block font-mono text-2xs font-medium uppercase tracking-caps text-fg-3">
            {label}
          </span>
        )}
        <span className="mt-1 block font-serif text-md leading-normal font-medium text-fg-1">
          {title}
        </span>
        <div className="mt-1 font-sans text-sm leading-normal text-fg-2 [&_p]:m-0 [&_p+p]:mt-1">
          {caption}
        </div>
      </figcaption>
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
