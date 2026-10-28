import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { DiscussionSection } from "@/components/discussion/DiscussionSection";
import { PostDeleted } from "@/components/experience/PostDeleted";
import { PostJournalCard, readPostVenue } from "@/components/experience/PostJournalCard";
import { PostView } from "@/components/experience/PostView";
import { InteractionUnavailable } from "@/components/library/InteractionUnavailable";
import { DetailCrumbs } from "@/components/portal/DetailCrumbs";
import { Footer } from "@/components/portal/Footer";
import { TopNav } from "@/components/portal/TopNav";
import { POST_REPLIES_COPY } from "@/lib/discussion/copy";
import { parseDiscussionView } from "@/lib/discussion/server";
import { journalHref } from "@/lib/experience/links";
import { fetchPost } from "@/lib/interaction/experience";
import type { PostDetail } from "@/lib/interaction/experience-schema";
import { postMetadata } from "@/lib/page-metadata";
import { publicUrl } from "@/lib/public-url";

interface PostPageProps {
  params: Promise<{ id: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}

/// 标题、描述、canonical（spec 8.5）；不存在时交给 `not-found.tsx` 的元数据，取不到时不设
export async function generateMetadata({ params }: PostPageProps): Promise<Metadata> {
  const { id } = await params;
  const read = await fetchPost(id);
  return read.kind === "ok" ? postMetadata(read.data, await publicUrl(`/posts/${id}`)) : {};
}

/// 面包屑：期刊还在时带刊名一节（链期刊页的经验区）；期刊已失效时省掉这一节
function Crumbs({ detail }: { detail: PostDetail | null }) {
  const href = detail ? journalHref(detail) : null;
  const journal = detail?.journal;
  return (
    <DetailCrumbs
      trail={[
        { label: "Patra", href: "/" },
        { label: "期刊", href: "/journals" },
        ...(href && journal ? [{ label: journal.abbreviation ?? journal.title, href }] : []),
      ]}
      current="投稿经验"
    />
  );
}

/// 经验页 `/posts/[id]`（简报 §3.16、spec 8）：不存在 404；已删除显示「该经验已删除」、不挂回复区；interaction
/// 取不到时整块错误（不是 404）。正常时主栏是经验（客户端，可原地编辑），侧栏期刊卡，下面是回复区（复用讨论区，
/// `?comment=` 是单楼视图）。≤ 980px 侧栏落在正文与回复区之间
export default async function PostPage({ params, searchParams }: PostPageProps) {
  const [{ id }, search] = await Promise.all([params, searchParams]);
  const read = await fetchPost(id);
  if (read.kind === "not-found") {
    notFound();
  }
  const detail = read.kind === "ok" ? read.data : null;
  const [venue, shareUrl] = await Promise.all([
    detail ? readPostVenue(detail.journal) : null,
    publicUrl(`/posts/${id}`),
  ]);
  return (
    <>
      <TopNav />
      <main className="pb-30">
        <Crumbs detail={detail} />
        {!detail ? (
          <div className="mx-auto max-w-page px-gutter pt-8">
            <InteractionUnavailable
              title="投稿经验暂时打不开"
              body="请稍后再试。"
              retryHref={`/posts/${id}`}
            />
          </div>
        ) : (
          <div className="mx-auto grid max-w-page grid-cols-[minmax(0,1fr)_340px] items-start gap-x-14 gap-y-12 px-gutter pt-8 [grid-template-areas:'main_rail'_'replies_rail'] max-[1100px]:gap-x-10 max-[980px]:grid-cols-1 max-[980px]:gap-y-8 max-[980px]:pt-6 max-[980px]:[grid-template-areas:'main'_'rail'_'replies']">
            <div className="min-w-0 [grid-area:main]">
              {detail.deleted || !detail.experience ? (
                <PostDeleted journal={detail.journal} />
              ) : (
                <PostView detail={detail} shareUrl={shareUrl} />
              )}
            </div>
            {/* sticky top = TopNav 57px + 面包屑 45px + 网格 pt-8 32px，与文献、期刊详情一致 */}
            <aside className="sticky top-[134px] flex flex-col gap-4 self-start [grid-area:rail] max-[980px]:static">
              <PostJournalCard journal={detail.journal} venue={venue} />
            </aside>
            {!detail.deleted && detail.experience && (
              <div className="min-w-0 [grid-area:replies]">
                <DiscussionSection
                  subject={{ type: "post", key: id }}
                  view={parseDiscussionView(search)}
                  copy={POST_REPLIES_COPY}
                  basePath={`/posts/${id}`}
                />
              </div>
            )}
          </div>
        )}
      </main>
      <Footer />
    </>
  );
}
