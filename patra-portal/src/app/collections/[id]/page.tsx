import type { Metadata } from "next";
import { notFound, unstable_rethrow } from "next/navigation";
import { InteractionUnavailable } from "@/components/library/InteractionUnavailable";
import { PublicCollectionView } from "@/components/library/PublicCollectionView";
import { Footer } from "@/components/portal/Footer";
import { TopNav } from "@/components/portal/TopNav";
import { lookupSession } from "@/lib/auth/current-user";
import { logRequestError } from "@/lib/auth/gateway-response";
import { fetchPublicProfile } from "@/lib/identity/public-profile";
import { fetchPublicCollection, fetchPublicCollectionPage } from "@/lib/interaction/collections";
import type { Owner } from "@/lib/interaction/schema";
import { parseLibrarySort, parsePage } from "@/lib/library/params";
import { collectionMetadata } from "@/lib/page-metadata";
import { publicUrl } from "@/lib/public-url";

interface CollectionPageProps {
  params: Promise<{ id: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}

/// 所有者的主页允不允许收录。收藏夹对这位访客可见，说明所有者主页对他可见，查到的就是访客视角。
/// 所有者已注销、取不到（本人视角 identity 不可用）或 identity 出错时一律不让收录（安全的一侧）；
/// 只用在页面信息里，identity 的故障不能把整页打成错误页
async function ownerIndexable(owner: Owner | null): Promise<boolean> {
  if (!owner || owner.deleted || !owner.username) {
    return false;
  }
  try {
    const lookup = await fetchPublicProfile(owner.username);
    return lookup.kind === "found" && lookup.profile.indexable;
  } catch (error) {
    // 静态渲染时 Next 用抛错切到动态渲染，这个信号必须原样抛出去
    unstable_rethrow(error);
    logRequestError(`/patra-identity/users/${owner.username}`, error);
    return false;
  }
}

/// 标题、描述、canonical（简报 §3.14）；`noindex` 跟随所有者主页的收录设置。不存在时交给 `not-found.tsx`；
/// interaction 不可用时只给最小的标题，不抛错（页面主体有自己的错误块）
export async function generateMetadata({
  params,
}: {
  params: Promise<{ id: string }>;
}): Promise<Metadata> {
  const { id } = await params;
  const collection = await fetchPublicCollection(id);
  if (collection.kind === "not-found") {
    return {};
  }
  if (collection.kind !== "ok") {
    return { title: "收藏夹暂时打不开 · Patra", robots: { index: false } };
  }
  const [canonical, indexable] = await Promise.all([
    publicUrl(`/collections/${id}`),
    ownerIndexable(collection.data.owner),
  ]);
  return collectionMetadata(collection.data, canonical, indexable);
}

/// 公开收藏夹页（简报 §3.14）：头部与第一页条目同时取，访客已登录就带会话（决策 H）。不存在、私有、所有者
/// 主页对访客不可见一律 404，在流式之前判（真的 404，所以不建 loading.tsx）；interaction 不可用是错误块，
/// 不是 404
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
