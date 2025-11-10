package com.patra.ingest.architecture.rules;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Domain 层纯净性规则
 *
 * <p>Domain 层是六边形架构的核心，必须保持纯净：
 * <ul>
 *   <li>零 Spring 框架依赖（完全禁止 org.springframework.*）
 *   <li>允许 Jackson 依赖（com.fasterxml.jackson.*）
 *   <li>不依赖其他业务层（-app/-infra/-adapter/-boot）
 *   <li>仅允许依赖：JDK、Lombok、Hutool、patra-common
 * </ul>
 *
 * <p><b>架构约束检查点：</b>
 * <ul>
 *   <li>【CHK-ARCH-001】Domain 层必须是纯 Java（无框架依赖）
 * </ul>
 *
 * @author linqibin
 * @since 2025-01-10
 */
public final class DomainLayerRules {

    private DomainLayerRules() {
        // 工具类，禁止实例化
    }

    /**
     * 规则 2: Domain 层完全禁止 Spring 框架依赖
     *
     * <p>Domain 层不能依赖 Spring 的任何包，包括但不限于：
     * <ul>
     *   <li>org.springframework.stereotype.*（@Component、@Service 等）
     *   <li>org.springframework.transaction.*（@Transactional）
     *   <li>org.springframework.beans.*（@Autowired）
     *   <li>org.springframework.context.*（ApplicationContext）
     *   <li>org.springframework.data.*（Spring Data）
     * </ul>
     *
     * <p><b>原因：</b>
     * <ol>
     *   <li>Domain 层是业务逻辑的核心，必须独立于框架
     *   <li>便于单元测试（无需 Spring 容器）
     *   <li>便于移植到其他框架
     *   <li>符合六边形架构的依赖倒置原则
     * </ol>
     *
     * <p><b>错误示例：</b>
     * <pre>
     * // ❌ 错误：Domain 层使用 Spring 注解
     * {@literal @}Service
     * public class PlanService { ... }
     *
     * // ❌ 错误：Domain 层使用 @Transactional
     * {@literal @}Transactional
     * public void createPlan() { ... }
     * </pre>
     *
     * <p><b>正确示例：</b>
     * <pre>
     * // ✅ 正确：纯 Java 类
     * public class PlanAggregate {
     *     private final PlanId id;
     *     private PlanStatus status;
     *
     *     public void start() {
     *         // 业务逻辑
     *     }
     * }
     * </pre>
     */
    public static final ArchRule domain_should_not_depend_on_spring =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
            .as("Domain 层完全禁止 Spring 框架依赖")
            .because("Domain 层必须是纯 Java，保持框架无关性（六边形架构铁律 CHK-ARCH-001）");

    /**
     * 规则 3: Domain 层允许的依赖白名单
     *
     * <p>Domain 层仅允许依赖以下包：
     * <ul>
     *   <li>java.* - JDK 标准库
     *   <li>lombok.* - Lombok 注解（@Getter、@Builder 等）
     *   <li>cn.hutool.* - Hutool 工具类
     *   <li>com.fasterxml.jackson.* - Jackson 序列化（允许）
     *   <li>com.patra.common.* - 项目通用库
     *   <li>com.patra.ingest.domain.* - Domain 层内部
     * </ul>
     *
     * <p><b>Jackson 依赖说明：</b>
     * Domain 层允许使用 Jackson 注解（如 @JsonProperty、@JsonIgnore）用于：
     * <ul>
     *   <li>领域事件序列化
     *   <li>值对象 JSON 映射
     *   <li>快照持久化
     * </ul>
     *
     * <p><b>不允许的依赖：</b>
     * <ul>
     *   <li>org.springframework.* - Spring 框架
     *   <li>com.baomidou.mybatisplus.* - MyBatis-Plus
     *   <li>javax.persistence.* / jakarta.persistence.* - JPA
     *   <li>com.patra.ingest.infra.* - 基础设施层
     *   <li>com.patra.ingest.app.* - 应用层
     * </ul>
     */
    public static final ArchRule domain_allowed_dependencies =
        classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "java..",                        // JDK 标准库
                "lombok..",                      // Lombok 注解
                "cn.hutool..",                   // Hutool 工具类
                "com.fasterxml.jackson..",       // Jackson 序列化（允许）
                "com.patra.common..",            // 项目通用库
                "com.patra.ingest.domain.."      // Domain 层内部
            )
            .as("Domain 层仅允许依赖：JDK、Lombok、Hutool、Jackson、patra-common")
            .because("限制依赖范围确保 Domain 层纯净（CHK-ARCH-001）");

    /**
     * 规则 1: Domain 层不依赖其他业务层
     *
     * <p>Domain 层不能依赖以下业务层：
     * <ul>
     *   <li>com.patra.ingest.app.* - 应用层
     *   <li>com.patra.ingest.infra.* - 基础设施层
     *   <li>com.patra.ingest.adapter.* - 适配器层
     *   <li>com.patra.ingest.boot.* - 启动层
     * </ul>
     *
     * <p><b>原因：</b>
     * <ol>
     *   <li>Domain 层是依赖图的最底层
     *   <li>其他层通过依赖倒置原则（DIP）依赖 Domain 层的接口
     *   <li>如果 Domain 依赖其他层，会形成循环依赖
     * </ol>
     *
     * <p><b>错误示例：</b>
     * <pre>
     * // ❌ 错误：Domain 层依赖 Infra 层
     * package com.patra.ingest.domain.service;
     *
     * import com.patra.ingest.infra.persistence.entity.PlanDO;  // ❌ 不允许
     *
     * public class PlanService {
     *     private PlanDO plan;  // ❌ 直接依赖 DO
     * }
     * </pre>
     *
     * <p><b>正确示例：</b>
     * <pre>
     * // ✅ 正确：Domain 层定义接口，Infra 层实现
     * package com.patra.ingest.domain.port;
     *
     * public interface PlanRepository {
     *     void save(Plan plan);
     * }
     * </pre>
     */
    public static final ArchRule domain_should_not_depend_on_other_layers =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..app..",
                "..infra..",
                "..adapter..",
                "..boot.."
            )
            .as("Domain 层不依赖其他业务层（app/infra/adapter/boot）")
            .because("Domain 层是依赖图的最底层，其他层通过 DIP 依赖 Domain（CHK-ARCH-002）");
}
