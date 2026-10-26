import type { Metadata } from "next";
import { LibraryPageBody } from "@/components/library/LibraryPageBody";
import { requireUser } from "@/lib/auth/require-user";
import { parseLibrarySort, parsePage } from "@/lib/library/params";
import { libraryPath } from "@/lib/library/server";

export const metadata: Metadata = { title: "我的文献库 · Patra" };

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

/// 文献库 · 全部（简报 §3.9 – §3.10）
export default async function LibraryPage({ searchParams }: { searchParams: SearchParams }) {
  const search = await searchParams;
  await requireUser(libraryPath("/library", search));
  return (
    <LibraryPageBody
      view="all"
      sort={parseLibrarySort(search.sort)}
      page={parsePage(search.page)}
    />
  );
}
