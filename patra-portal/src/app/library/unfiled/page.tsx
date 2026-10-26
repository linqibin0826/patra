import type { Metadata } from "next";
import { LibraryPageBody } from "@/components/library/LibraryPageBody";
import { requireUser } from "@/lib/auth/require-user";
import { parseLibrarySort, parsePage } from "@/lib/library/params";
import { libraryPath } from "@/lib/library/server";

export const metadata: Metadata = { title: "未分类 · 我的文献库 · Patra" };

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

/// 文献库 · 未分类：不在任何收藏夹里的条目
export default async function UnfiledPage({ searchParams }: { searchParams: SearchParams }) {
  const search = await searchParams;
  await requireUser(libraryPath("/library/unfiled", search));
  return (
    <LibraryPageBody
      view="unfiled"
      sort={parseLibrarySort(search.sort)}
      page={parsePage(search.page)}
    />
  );
}
