import { ArrowRight, Home } from "lucide-react";
import Link from "next/link";
import { StatusScreen } from "@/components/portal/status/StatusScreen";
import { btnPrimary, btnSecondary } from "@/lib/portal-ui";

type NotFoundKind = "journal" | "paper" | "page";

const LINES: Record<NotFoundKind, string> = {
  journal: "这本期刊不在 Patra 的索引里 —— 可能 ID 有误，或它尚未被收录。",
  paper: "这篇文献不在 Patra 的索引里 —— 可能 ID 有误，或它尚未被采集。",
  page: "这个地址在 Patra 里找不到对应内容。链接可能已失效，或从未存在。",
};

/// 浏览入口随缺失对象而变：缺期刊引去期刊库，其余引去文献库
const BROWSE: Record<NotFoundKind, { href: string; label: string }> = {
  journal: { href: "/journals", label: "浏览全部期刊" },
  paper: { href: "/papers", label: "浏览全部文献" },
  page: { href: "/papers", label: "浏览全部文献" },
};

/// 全站复用 404 状态屏（RSC）。暖纸编辑风，不暴露堆栈。
export function NotFoundState({ kind = "page" }: { kind?: NotFoundKind }) {
  return (
    <StatusScreen
      code="404"
      badge="HTTP 404 · not found"
      tone="clay"
      title="没有这一页"
      body={LINES[kind]}
      actions={
        <>
          <Link href="/" className={btnPrimary}>
            <Home size={15} /> 返回首页
          </Link>
          <Link href={BROWSE[kind].href} className={btnSecondary}>
            {BROWSE[kind].label} <ArrowRight size={15} />
          </Link>
        </>
      }
    />
  );
}
