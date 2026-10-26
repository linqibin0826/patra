import { notFound } from "next/navigation";
import { InteractionUnavailable } from "@/components/library/InteractionUnavailable";
import { PublicCollectionView } from "@/components/library/PublicCollectionView";
import { Footer } from "@/components/portal/Footer";
import { TopNav } from "@/components/portal/TopNav";
import { lookupSession } from "@/lib/auth/current-user";
import { fetchPublicCollection, fetchPublicCollectionPage } from "@/lib/interaction/collections";
import { parseLibrarySort, parsePage } from "@/lib/library/params";
import { publicUrl } from "@/lib/public-url";

interface CollectionPageProps {
  params: Promise<{ id: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}

/// 公开收藏夹页（简报 §3.14）：头部与第一页条目同时取，访客已登录就带会话（决策 H）。不存在、私有、所有者
/// 主页对访客不可见一律 404，在流式之前判（真的 404，所以不建 loading.tsx）；interaction 不可用是错误块，
/// 不是 404。页面信息（标题、描述、canonical）由 PAP-115 加
export default async function CollectionPage({ params, searchParams }: CollectionPageProps) {
  const [{ id }, search] = await Promise.all([params, searchParams]);
  const sort = parseLibrarySort(search.sort);
  const page = parsePage(search.page);
  const [collection, items, session, link] = await Promise.all([
    fetchPublicCollection(id),
    fetchPublicCollectionPage(id, sort, page),
    lookupSession(),
    publicUrl(`/collections/${id}`),
  ]);
  if (collection.kind === "not-found") {
    notFound();
  }
  return (
    <>
      <TopNav />
      <main>
        {collection.kind === "ok" ? (
          <PublicCollectionView
            collection={collection.data}
            items={items}
            viewer={session.status === "authenticated" ? session.user : null}
            sort={sort}
            page={page}
            link={link}
          />
        ) : (
          <div className="mx-auto max-w-page px-gutter py-12">
            <InteractionUnavailable title="收藏夹暂时打不开" retryHref={`/collections/${id}`} />
          </div>
        )}
      </main>
      <Footer />
    </>
  );
}
