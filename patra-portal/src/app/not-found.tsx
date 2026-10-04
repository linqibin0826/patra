import { Footer } from "@/components/portal/Footer";
import { NotFoundState } from "@/components/portal/status/NotFoundState";
import { TopNav } from "@/components/portal/TopNav";

export default function NotFound() {
  return (
    <>
      <TopNav />
      <main>
        <NotFoundState kind="page" />
      </main>
      <Footer />
    </>
  );
}
