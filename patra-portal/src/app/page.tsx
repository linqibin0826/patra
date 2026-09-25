import { Suspense } from "react";
import { ExploreFeed } from "@/components/portal/explore-feed";
import { ExploreFeedSkeleton } from "@/components/portal/explore-feed/skeleton";
import { Footer } from "@/components/portal/Footer";
import { HeroWithSearch } from "@/components/portal/HeroWithSearch";
import { Journals } from "@/components/portal/Journals";
import { TopicCloud } from "@/components/portal/TopicCloud";
import { TopNav } from "@/components/portal/TopNav";
import type { FeedTab } from "@/types/portal";

export default async function HomePage({
  searchParams,
}: {
  searchParams: Promise<{ tab?: string }>;
}) {
  const { tab } = await searchParams;
  const safeTab: FeedTab = tab === "cited" ? "cited" : "recent";
  return (
    <>
      <TopNav />
      <main>
        <HeroWithSearch />
        <TopicCloud />
        <Journals />
        <Suspense fallback={<ExploreFeedSkeleton />}>
          <ExploreFeed tab={safeTab} />
        </Suspense>
      </main>
      <Footer />
    </>
  );
}
