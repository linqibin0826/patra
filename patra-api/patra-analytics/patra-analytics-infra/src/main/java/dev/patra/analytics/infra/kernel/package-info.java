/// kernel 子域的基础设施层。
///
/// 底座：只放被两个以上子域共用的东西（事件名、来源、schema 版本）。对同服务的其他子域完全公开，自己不依赖任何其他子域。
package dev.patra.analytics.infra.kernel;
