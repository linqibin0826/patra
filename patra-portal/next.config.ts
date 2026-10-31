import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // 产出自包含 Node server 到 .next/standalone（容器化运行，镜像精简）
  output: "standalone",
  // 开发时 Next 默认在终端打印每次 Server Action 调用的参数，登录、注册的参数里有明文密码；也默认打印每个进来的
  // 请求（带查询串），一键退订地址的查询串里有退订令牌
  logging: { serverFunctions: false, incomingRequests: { ignore: [/\/unsubscribe\/one-click/] } },
};

export default nextConfig;
