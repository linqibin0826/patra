import type { Metadata } from "next";
import { notFound, unstable_rethrow } from "next/navigation";
import { LibraryPageBody } from "@/components/library/LibraryPageBody";
import { requireUser } from "@/lib/auth/require-user";
import { fetchMyPrivacy } from "@/lib/identity/profile";
import { isEntityId, parseLibrarySort, parsePage } from "@/lib/library/params";
import { libraryPath, overviewForRequest } from "@/lib/library/server";
import { publicUrl } from "@/lib/public-url";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

interface CollectionPageProps {
  params: Promise<{ collectionId: string }>;
  searchParams: SearchParams;
}

/// 标题 `组会 · 我的文献库 · Patra`；概览取不到时只写「我的文献库」
export async function generateMetadata({ params }: CollectionPageProps): Promise<Metadata> {
  const { collectionId } = await params;
  const overview = isEntityId(collectionId) ? await overviewForRequest() : null;
  const collection =
    overview?.kind === "ok"
      ? overview.data.collections.find((c) => c.collectionId === collectionId)
      : undefined;
  return { title: collection ? `${collection.name} · 我的文献库 · Patra` : "我的文献库 · Patra" };
}

/// 本人主页现在对谁可见（公开开关卡的三种说明要它）；读不到时为 null，按「所有人」说明
async function profileVisibility(): Promise<"PUBLIC" | "SIGNED_IN" | "SELF" | null> {
  try {
    const privacy = await fetchMyPrivacy();
    return privacy === "signed-out" ? null : privacy.profileVisibility;
  } catch (error) {
    // 静态渲染时 Next 用抛错切到动态渲染，这个信号必须原样抛出去
    unstable_rethrow(error);
    return null;
  }
}

/// 文献库 · 某个收藏夹（简报 §3.11）：不存在或不是自己的 → 404（在流式之前判，是真的 404）。概览取不到时
/// 判不了，照常出页面，内容列里是错误块
export default async function CollectionPage({ params, searchParams }: CollectionPageProps) {
  const [{ collectionId }, search] = await Promise.all([params, searchParams]);
  if (!isEntityId(collectionId)) {
    notFound();
  }
  await requireUser(libraryPath(`/library/${collectionId}`, search));
  const overview = await overviewForRequest();
  if (
    overview.kind === "ok" &&
    !overview.data.collections.some((c) => c.collectionId === collectionId)
  ) {
    notFound();
  }
  const [visibility, link] = await Promise.all([
    profileVisibility(),
    publicUrl(`/collections/${collectionId}`),
  ]);
  return (
    <LibraryPageBody
      view={collectionId}
      sort={parseLibrarySort(search.sort)}
      page={parsePage(search.page)}
      collectionContext={{ publicLink: link, profileVisibility: visibility }}
    />
  );
}
