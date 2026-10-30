import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ExperienceSection } from "@/components/experience/ExperienceSection";
import { Footer } from "@/components/portal/Footer";
import { JournalDetailView } from "@/components/portal/journal-detail/JournalDetailView";
import { TopNav } from "@/components/portal/TopNav";
import { parseExperienceSort } from "@/lib/experience/sort";
import { journalMetadata } from "@/lib/page-metadata";
import { fetchVenueDetail } from "@/lib/portal-api/venues";
import { publicUrl } from "@/lib/public-url";

/// 标题、描述、canonical（简报 §3.18）；不存在时交给 `not-found.tsx` 的元数据
export async function generateMetadata({
  params,
}: {
  params: Promise<{ id: string }>;
}): Promise<Metadata> {
  const { id } = await params;
  const venue = await fetchVenueDetail(id);
  return venue ? journalMetadata(venue, await publicUrl(`/journals/${id}`)) : {};
}

export default async function JournalDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const [{ id }, search] = await Promise.all([params, searchParams]);
  const venue = await fetchVenueDetail(id); // 5xx/超时会 throw → 全局 error.tsx
  if (!venue) {
    notFound(); // 非数字 id 或 BE 404 → [id]/not-found.tsx
  }
  return (
    <>
      <TopNav />
      <main>
        <JournalDetailView
          venue={venue}
          experiences={
            <ExperienceSection
              journalId={id}
              journalTitle={venue.title}
              sort={parseExperienceSort(search.sort)}
            />
          }
        />
      </main>
      <Footer />
    </>
  );
}
