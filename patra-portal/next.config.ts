import type { NextConfig } from "next";

/// 显示人机验证的三个页面：不放行 TRUSTTURN 的脚本，它只能以 iframe 出现。`'unsafe-inline'` 是因为 Next 的水合脚本是内联的，
/// 用 nonce 要把这些页面全部变成动态渲染并改 proxy，这里的目的也不是防 XSS。只限制这两项，其他资源不动。
/// `static.cloudflareinsights.com` 是 Cloudflare 隧道自动注入的 Web Analytics 脚本，不放行就统计不到这三个页面。
function humanVerificationCsp(): string {
  const dev = process.env.NODE_ENV === "development" ? " 'unsafe-eval'" : "";
  return [
    `script-src 'self' 'unsafe-inline'${dev} https://challenges.cloudflare.com https://static.cloudflareinsights.com`,
    "frame-src https://challenges.cloudflare.com https://api.trustturn.cc",
  ].join("; ");
}

const nextConfig: NextConfig = {
  reactStrictMode: true,
  async headers() {
    return ["/login", "/register", "/forgot-password"].map((source) => ({
      source,
      headers: [{ key: "Content-Security-Policy", value: humanVerificationCsp() }],
    }));
  },
  // 产出自包含 Node server 到 .next/standalone（容器化运行，镜像精简）
  output: "standalone",
  // 开发时 Next 默认在终端打印每次 Server Action 调用的参数，登录、注册的参数里有明文密码；也默认打印每个进来的
  // 请求（带查询串），一键退订地址的查询串里有退订令牌
  logging: {
    serverFunctions: false,
    incomingRequests: { ignore: [/\/unsubscribe\/one-click/, /\/api\/auth\/oauth\/callback\//] },
  },
};

export default nextConfig;
