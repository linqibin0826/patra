import { notFound } from "next/navigation";
import { Suspense } from "react";
import { Footer } from "@/components/portal/Footer";
import { FullTextCrumbStatus } from "@/components/portal/fulltext/FullTextCrumbStatus";
import { FullTextTocButton, FullTextTocCard } from "@/components/portal/fulltext/FullTextNav";
import { FullTextSection } from "@/components/portal/fulltext/FullTextSection";
import { FullTextSkeleton, TocCardSkeleton } from "@/components/portal/fulltext/FullTextSkeleton";
import { CrumbStatus } from "@/components/portal/paper-detail/CrumbStatus";
import { FactCellsSkeleton, FullTextFacts } from "@/components/portal/paper-detail/PaperFacts";
import { PublicationDetailView } from "@/components/portal/paper-detail/PublicationDetailView";
import { RecordSection } from "@/components/portal/paper-detail/RecordSection";
import { TopNav } from "@/components/portal/TopNav";
import { fetchPublicationDetail } from "@/lib/portal-api/publications";
import { shortTitle } from "@/lib/portal-api/title-derive";

export default async function PaperDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const paper = await fetchPublicationDetail(id); // 5xx/超时会 throw → 全局 error.tsx
  if (!paper) {
    notFound(); // 非数字 id 或 BE 404 → [id]/not-found.tsx
  }
  // 全文走独立接口：详情先出，事实条的全文三格、全文区块、目录卡、目录按钮各自在 Suspense 里后到（共用一次请求）
  return (
    <>
      <TopNav />
      <main>
        <PublicationDetailView
          paper={paper}
          crumbStatus={
            <Suspense
              fallback={<CrumbStatus shortTitle={shortTitle(paper.title)} positions={[]} />}
            >
              <FullTextCrumbStatus paper={paper} />
            </Suspense>
          }
          fullTextFacts={
            paper.hasFullText && (
              <Suspense fallback={<FactCellsSkeleton />}>
                <FullTextFacts paper={paper} />
              </Suspense>
            )
          }
          fullText={
            <Suspense fallback={<FullTextSkeleton />}>
              <FullTextSection paper={paper} record={<RecordSection paper={paper} />} />
            </Suspense>
          }
          tocCard={
            <Suspense fallback={<TocCardSkeleton />}>
              <FullTextTocCard paper={paper} />
            </Suspense>
          }
          tocButton={
            paper.hasFullText && (
              <Suspense fallback={null}>
                <FullTextTocButton paper={paper} />
              </Suspense>
            )
          }
        />
      </main>
      <Footer />
    </>
  );
}
