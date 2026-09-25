import { EmptyState } from "@/components/portal/browse/EmptyState";
import type { VenueBrowseQuery } from "@/types/portal";

interface JournalEmptyResultProps {
  kind: "no-results" | "empty-library";
  query: VenueBrowseQuery;
}

/**
 * 期刊浏览空态（RSC 纯渲染）。
 * - no-results：搜索/筛选无结果，引导用户清除筛选
 * - empty-library：库中本身无期刊数据
 */
export function JournalEmptyResult({ kind, query }: JournalEmptyResultProps) {
  if (kind === "empty-library") {
    return (
      <EmptyState
        title="库中暂无期刊"
        description="期刊数据尚未入库，请稍后再试。"
        actions={[{ href: "/", label: "返回首页" }]}
      />
    );
  }

  return (
    <EmptyState
      title={query.q ? `未找到匹配 "${query.q}" 的期刊` : "未找到匹配的期刊"}
      description="请尝试更换搜索词，或放宽筛选条件后重试。"
      actions={[
        { href: "/journals", label: "清除全部筛选" },
        { href: "/", label: "返回首页" },
      ]}
    />
  );
}
