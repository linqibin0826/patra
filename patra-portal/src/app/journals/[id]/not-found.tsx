import { Footer } from "@/components/portal/Footer";
import { NotFoundState } from "@/components/portal/status/NotFoundState";
import { TopNav } from "@/components/portal/TopNav";

export default function JournalNotFound() {
  return (
    <>
      <TopNav />
      <main>
        <NotFoundState kind="journal" />
      </main>
      <Footer />
    </>
  );
}
