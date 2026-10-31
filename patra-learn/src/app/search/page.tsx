// patra-learn/src/app/search/page.tsx
// 搜索页：构建时把索引作为 props 交给客户端组件，在浏览器里查，不建服务
import type { Metadata } from "next";
import { SearchBox } from "@/components/search-box";
import { getCatalog } from "@/lib/content/catalog";
import { availableHints, searchDocs } from "@/lib/site/search";
import { PERSONAL_TAG } from "@/lib/site/views";

export const metadata: Metadata = { title: "搜索" };

/// 常搜词；构建时只保留搜得到公开条目的
const HINTS = ["事务", "索引", "发件箱", "Nacos", "会话", "ack", "Docker", "测试"];

export default function SearchPage() {
  const docs = searchDocs(getCatalog());
  return (
    <main className="mx-auto box-border w-full max-w-[880px] px-6 pt-2 pb-24 max-sm:px-4">
      <h1 className="sr-only">搜索</h1>
      <SearchBox
        docs={docs}
        hints={availableHints(docs, HINTS)}
        personalHref={`/tags/${PERSONAL_TAG}`}
      />
    </main>
  );
}
