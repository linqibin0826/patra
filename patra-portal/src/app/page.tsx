import { ExploreFeed } from "@/components/portal/ExploreFeed";
import { Footer } from "@/components/portal/Footer";
import { HeroWithToast } from "@/components/portal/HeroWithToast";
import { Journals } from "@/components/portal/Journals";
import { TopicCloud } from "@/components/portal/TopicCloud";
import { TopNav } from "@/components/portal/TopNav";

export default function HomePage() {
  return (
    <>
      <TopNav />
      <main>
        <HeroWithToast />
        <TopicCloud />
        <Journals />
        <ExploreFeed />
      </main>
      <Footer />
    </>
  );
}
