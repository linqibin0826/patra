import { ArrowRight, Home } from "lucide-react";
import Link from "next/link";
import { CjkDashText } from "@/components/portal/CjkDashText";
import { StatusScreen } from "@/components/portal/status/StatusScreen";
import { btnPrimary, btnSecondary } from "@/lib/portal-ui";

type NotFoundKind = "journal" | "paper" | "page" | "user" | "collection";

/// 标题：用户主页另有说法，其余都是「没有这一页」
const TITLES: Record<NotFoundKind, string> = {
  journal: "没有这一页",
  paper: "没有这一页",
  page: "没有这一页",
  user: "没有找到这个用户",
  collection: "没有找到这个收藏夹",
};

const LINES: Record<NotFoundKind, string> = {
  journal: "这本期刊不在 Patra 的索引里——可能 ID 有误，或它尚未被收录。",
  paper: "这篇文献不在 Patra 的索引里——可能 ID 有误，或它尚未被采集。",
  page: "这个地址在 Patra 里找不到对应内容。链接可能已失效，或从未存在。",
  /// 不存在、不公开、未验证、冷静期、封禁、已注销都是这一句，不给区分信号（简报 §3.13）
  user: "地址可能有误，或者这个主页没有对你公开。",
  /// 不存在、私有、所有者主页对你不可见都是这一句（简报 §3.14）
  collection: "地址可能有误，或者这个收藏夹没有对你公开。",
};

/// 浏览入口随缺失对象而变：缺期刊引去期刊库，其余引去文献库
const BROWSE: Record<NotFoundKind, { href: string; label: string }> = {
  journal: { href: "/journals", label: "浏览全部期刊" },
  paper: { href: "/papers", label: "浏览全部文献" },
  page: { href: "/papers", label: "浏览全部文献" },
  user: { href: "/papers", label: "浏览全部文献" },
  collection: { href: "/papers", label: "浏览全部文献" },
};

/// 全站复用 404 状态屏（RSC）。暖纸编辑风，不暴露堆栈。
export function NotFoundState({ kind = "page" }: { kind?: NotFoundKind }) {
  return (
    <StatusScreen
      code="404"
      badge="HTTP 404 · not found"
      tone="clay"
      title={TITLES[kind]}
      body={<CjkDashText>{LINES[kind]}</CjkDashText>}
      actions={
        <>
          <Link href="/" className={btnPrimary}>
            <Home size={15} /> 返回首页
          </Link>
          <Link href={BROWSE[kind].href} className={btnSecondary}>
            {BROWSE[kind].label} <ArrowRight size={15} data-icon="trailing" />
          </Link>
        </>
      }
    />
  );
}
