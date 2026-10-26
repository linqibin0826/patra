import type { Metadata } from "next";
import { NotFoundState } from "@/components/portal/status/NotFoundState";

export const metadata: Metadata = { title: "没有找到这个收藏夹 · Patra", robots: { index: false } };

/// `/library/<不存在或不是自己的收藏夹>`（简报 §2.2 第 4 条）
export default function CollectionNotFound() {
  return <NotFoundState kind="collection" />;
}
