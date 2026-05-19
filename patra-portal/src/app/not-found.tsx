import Link from "next/link";

export default function NotFound() {
  return (
    <main className="mx-auto flex min-h-screen max-w-3xl flex-col items-center justify-center gap-3 px-6">
      <h1 className="text-2xl font-bold">404</h1>
      <p className="text-sm text-gray-500">页面不存在</p>
      <Link
        href="/"
        className="rounded-md border border-gray-300 px-4 py-2 text-sm hover:bg-gray-100"
      >
        返回首页
      </Link>
    </main>
  );
}
