"use client";

import {
  CircleCheckIcon,
  InfoIcon,
  Loader2Icon,
  OctagonXIcon,
  TriangleAlertIcon,
} from "lucide-react";
import type { CSSProperties } from "react";
import { Toaster as Sonner, type ToasterProps } from "sonner";

const Toaster = ({ ...props }: ToasterProps) => {
  return (
    <Sonner
      theme="light"
      className="toaster group"
      // sonner 往 <head> 插入的样式不分层，Tailwind 的类在 @layer utilities 里，写在类名里的变量
      // 压不过它，所以写在行内样式上
      style={
        {
          "--normal-bg": "var(--bg-elevated)",
          "--normal-text": "var(--fg-1)",
          "--normal-border": "var(--border-default)",
          "--border-radius": "var(--radius-lg)",
        } as CSSProperties
      }
      // 每条提示的阴影和字体：sonner 在提示上写死了阴影，带 !important 才压得过；
      // 字体它写在外层列表上，提示上写一个就盖过去
      toastOptions={{ classNames: { toast: "font-sans shadow-lg!" } }}
      icons={{
        success: <CircleCheckIcon className="size-4 text-status-success" />,
        info: <InfoIcon className="size-4" />,
        warning: <TriangleAlertIcon className="size-4" />,
        error: <OctagonXIcon className="size-4" />,
        loading: <Loader2Icon className="size-4 animate-spin" />,
      }}
      {...props}
    />
  );
};

export { Toaster };
