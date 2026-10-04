import { Footer } from "@/components/portal/Footer";
import { NotFoundState } from "@/components/portal/status/NotFoundState";
import { TopNav } from "@/components/portal/TopNav";

export default function PaperNotFound() {
  return (
    <>
      <TopNav />
      <main>
        <NotFoundState kind="paper" />
      </main>
      <Footer />
    </>
  );
}
