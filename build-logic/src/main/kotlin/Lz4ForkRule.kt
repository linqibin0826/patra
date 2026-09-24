import javax.inject.Inject
import org.gradle.api.artifacts.CacheableRule
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule

/// 把任意构件元数据中对 `org.lz4:lz4-java` 的依赖改写为维护中的同包名 fork `at.yawk.lz4:lz4-java`
///
/// `org.lz4:lz4-java` 已停更且有未修复漏洞（rocketmq-common 传递引入）。在元数据层改写而非
/// `dependencySubstitution`：substitution 只替换解析结果，依赖边上仍保留旧坐标的请求，
/// GitHub 依赖图提交会把旧坐标一并记录并持续误报漏洞。
///
/// @param forkVersion `at.yawk.lz4:lz4-java` 的版本
@CacheableRule
abstract class Lz4ForkRule @Inject constructor(private val forkVersion: String) : ComponentMetadataRule {

    /// 移除对 `org.lz4:lz4-java` 的直接依赖，并以同一变体追加 fork 依赖
    ///
    /// @param context 当前构件的元数据上下文
    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                if (removeIf { it.group == "org.lz4" && it.name == "lz4-java" }) {
                    add("at.yawk.lz4:lz4-java:$forkVersion")
                }
            }
        }
    }
}
