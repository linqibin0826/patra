"use client";

import { Check, Copy, Link2 } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";

interface IdentifierChipProps {
  label: string;
  value: string | null;
  href?: string | null;
}

/// 标识符 chip：整块 key+value 即复制按钮；可选外链尾格。value 为空 → 不渲染。
export function IdentifierChip({ label, value, href }: IdentifierChipProps) {
  const [done, setDone] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
  }, []);
  if (!value) {
    return null;
  }
  const onCopy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setDone(true);
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
      timerRef.current = setTimeout(() => setDone(false), 1400);
    } catch {
      // 无 clipboard 权限时静默，不打断
    }
  };
  return (
    <span
      className={cn(
        "inline-flex max-w-full items-stretch overflow-hidden rounded-full border bg-paper-50 font-mono transition-colors duration-200 has-[:focus-visible]:shadow-(--ring-focus)",
        done ? "border-moss-500" : "border-border-default hover:border-ink-500",
      )}
    >
      <button
        type="button"
        onClick={onCopy}
        aria-label={`复制 ${label}：${value}`}
        title={`复制 ${label}`}
        className="inline-flex min-w-0 items-stretch bg-transparent transition-colors duration-200 hover:bg-paper-200"
      >
        <span
          className={cn(
            "inline-flex shrink-0 items-center border-r pr-3 pl-4 text-3xs uppercase tracking-mono",
            done ? "border-moss-500 text-status-success-ink" : "border-border-default text-fg-3",
          )}
        >
          {label}
        </span>
        <span
          className={cn(
            // 长值（如 DOI）在窄屏截断，复制图标与外链格保持可见；完整值在按钮的可访问名里
            "min-w-0 self-center truncate px-3 py-2 text-sm tabular-nums",
            done ? "text-status-success-ink" : "text-ink-900",
          )}
        >
          {value}
        </span>
        <span
          className={cn(
            "inline-flex shrink-0 items-center pr-3.5 pl-0.5",
            done ? "text-status-success-ink" : "text-fg-3",
          )}
          aria-hidden
        >
          {done ? <Check size={14} className="anim-pop" /> : <Copy size={13} />}
        </span>
        <span role="status" className="sr-only">
          {done ? `已复制 ${label}` : ""}
        </span>
      </button>
      {href && (
        <a
          href={href}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={`在新窗口打开 ${label}`}
          title="打开链接 ↗"
          className="inline-flex w-10 shrink-0 items-center justify-center border-l border-border-default bg-paper-50 pr-1 text-fg-3 transition-colors duration-200 hover:bg-clay-50 hover:text-clay-700"
        >
          <Link2 size={13} />
        </a>
      )}
    </span>
  );
}
