/// 测试夹具：子域根包的说明，子域根包里只允许它。
///
/// 带一个运行期保留的注解，javac 才会生成 `package-info.class`（各服务模块用 `-Xpkginfo:always` 达到同样效果）。
@Deprecated
package dev.patra.tool.test.archunit.fixtures.subdomain.templatecompliant.domain.collection;
