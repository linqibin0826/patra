/// kernel 子域的领域层。
///
/// 底座：只放被两个以上子域共用的东西：提醒类别（回复我的、@ 我的、有帮助）、按用户 ID 取用户名的端口、
/// 在站信号（release spec 决策 H）、注销清理扩展点。随第一个用到它的 Issue 落地（预计 PAP-143）。
///
/// 对同服务的其他子域完全公开，自己不依赖任何其他子域。
package dev.patra.notification.domain.kernel;
