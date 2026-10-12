import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // 产出自包含 Node server 到 .next/standalone（容器化运行，镜像精简）
  output: "standalone",
  // 开发时 Next 默认在终端打印每次 Server Action 调用的参数，登录、注册的参数里有明文密码
  logging: { serverFunctions: false },
};

export default nextConfig;
