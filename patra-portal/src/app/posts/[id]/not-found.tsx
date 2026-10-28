import { Footer } from "@/components/portal/Footer";
import { NotFoundState } from "@/components/portal/status/NotFoundState";
import { TopNav } from "@/components/portal/TopNav";

export default function PostNotFound() {
  return (
    <>
      <TopNav />
      <main>
        <NotFoundState kind="post" />
      </main>
      <Footer />
    </>
  );
}
