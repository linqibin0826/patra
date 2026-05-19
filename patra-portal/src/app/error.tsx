"use client";

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main className="mx-auto flex min-h-screen max-w-3xl flex-col items-center justify-center gap-3 px-6">
      <h1 className="text-2xl font-bold">出错了</h1>
      <p className="text-sm text-gray-500">{error.message}</p>
      <button
        type="button"
        onClick={reset}
        className="rounded-md border border-gray-300 px-4 py-2 text-sm hover:bg-gray-100"
      >
        重试
      </button>
    </main>
  );
}
