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
        "inline-flex items-stretch overflow-hidden rounded-md border bg-paper-50 font-mono transition-colors has-[:focus-visible]:shadow-(--ring-focus)",
        done ? "border-moss-500" : "border-border-default hover:border-ink-300",
      )}
    >
      <button
        type="button"
        onClick={onCopy}
        aria-label={`复制 ${label}：${value}`}
        title={`复制 ${label}`}
        className="inline-flex items-stretch bg-transparent transition-colors hover:bg-paper-200"
      >
        <span
          className={cn(
            "inline-flex items-center border-r px-2.5 text-3xs uppercase tracking-mono",
            done ? "border-moss-500 text-status-success-ink" : "border-border-default text-fg-3",
          )}
        >
          {label}
        </span>
        <span
          className={cn(
            "inline-flex items-center px-2.5 py-1.5 text-sm tabular-nums",
            done ? "text-status-success-ink" : "text-ink-900",
          )}
        >
          {value}
        </span>
        <span
          className={cn(
            "inline-flex items-center pr-2.5 pl-0.5",
            done ? "text-status-success-ink" : "text-fg-3",
          )}
          aria-hidden
        >
          {done ? <Check size={13} /> : <Copy size={13} />}
        </span>
      </button>
      {href && (
        <a
          href={href}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={`在新窗口打开 ${label}`}
          title="打开链接 ↗"
          className="inline-flex w-8 items-center justify-center border-l border-border-default bg-paper-50 text-fg-3 transition-colors hover:bg-clay-50 hover:text-clay-700"
        >
          <Link2 size={13} />
        </a>
      )}
    </span>
  );
}
