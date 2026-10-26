import { BookmarkPlus, CheckCheck, FolderOpen } from "lucide-react";
import Link from "next/link";
import { btnPrimary, btnSecondary } from "@/lib/portal-ui";

const STATES = {
  all: {
    icon: BookmarkPlus,
    title: "文献库还是空的",
    body: "在检索结果、首页或文献页点「收藏」，文献就会收进这里。",
    action: { href: "/papers", label: "去检索文献", primary: true },
  },
  unfiled: {
    icon: CheckCheck,
    title: "都已整理好了",
    body: "每一篇都在至少一个收藏夹里。",
    action: null,
  },
  collection: {
    icon: FolderOpen,
    title: "这个收藏夹还没有文献",
    body: "在文献页的收藏浮层里勾选它，或者在文献库里选中文献，点「放进收藏夹」。",
    action: { href: "/library", label: "去全部文献", primary: false },
  },
} as const;

/// 文献库三种视图的空状态（简报 §3.11）：居中在内容列，48px 线性图标，不画插画
export function LibraryEmpty({ kind }: { kind: keyof typeof STATES }) {
  const { icon: Icon, title, body, action } = STATES[kind];
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-20 text-center">
      <Icon className="size-12 text-fg-3" strokeWidth={1.25} aria-hidden="true" />
      <h2 className="font-serif text-xl font-medium text-fg-1">{title}</h2>
      <p className="max-w-[32em] text-sm leading-6 text-fg-2">{body}</p>
      {action && (
        <Link href={action.href} className={action.primary ? btnPrimary : btnSecondary}>
          {action.label}
        </Link>
      )}
    </div>
  );
}
