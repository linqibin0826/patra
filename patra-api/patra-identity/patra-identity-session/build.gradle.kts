/**
 * Patra Identity Session
 *
 * identity 与网关共用的会话存储契约：令牌、Redis 键、Lua 脚本、RedisSessionStore。
 * 只给 identity（infra）和网关引；其他服务只认网关签的断言。
 */

plugins {
    id("linqibin.module-patra")
    id("linqibin.java-library")
}

dependencies {
    // AccountType / ClientType / CurrentUser
    api(project(":patra-api:patra-common:patra-common-security"))

    // DomainException / StandardErrorTrait
    api(project(":linqibin-commons:linqibin-commons-core"))

    // StringRedisTemplate、RedisScript、Lettuce；版本由 Boot BOM 管理
    api("org.springframework.boot:spring-boot-starter-data-redis")

    // 集成测试的 Redis 容器（integrationTest 继承 test 的依赖）
    testImplementation(project(":linqibin-commons:linqibin-spring-boot-starter-test"))
}

// 覆盖率要求 75%
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}
