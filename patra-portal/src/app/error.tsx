"use client";

import { Footer } from "@/components/portal/Footer";
import { ErrorState } from "@/components/portal/status/ErrorState";
import { TopNav } from "@/components/portal/TopNav";

export default function RootError({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <>
      <TopNav />
      <main>
        <ErrorState onRetry={reset} />
      </main>
      <Footer />
    </>
  );
}
