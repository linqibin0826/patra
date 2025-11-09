package com.patra.spring.boot.starter.test.base;

import com.patra.spring.boot.starter.test.config.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * 集成测试基类
 *
 * <p>自动配置 TestContainers (MySQL, Redis, Nacos),支持 Spring Context 完整加载。
 * 所有集成测试类都应该继承此基类。</p>
 *
 * <h3>功能特性</h3>
 * <ul>
 *   <li>自动启动 TestContainers: MySQL, Redis, Nacos</li>
 *   <li>事务回滚: 默认回滚,保持数据隔离</li>
 *   <li>数据库自动配置: 使用 TestContainers 提供的数据源</li>
 *   <li>日志配置: 支持可配置的日志级别</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * class RegistryRepositoryIT extends BaseIntegrationTest {
 *
 *     @Autowired
 *     private ProvenanceRepository provenanceRepository;
 *
 *     @Test
 *     void testSaveAndFindProvenance() {
 *         // given
 *         Provenance provenance = new Provenance("PubMed", "https://pubmed.ncbi.nlm.nih.gov");
 *
 *         // when
 *         provenanceRepository.save(provenance);
 *
 *         // then
 *         Optional<Provenance> found = provenanceRepository.findById(provenance.getId());
 *         assertThat(found).isPresent();
 *         assertThat(found.get().getName()).isEqualTo("PubMed");
 *     }
 *
 *     @AfterEach
 *     void tearDown() {
 *         cleanRedis();  // 清理 Redis 数据
 *     }
 * }
 * }</pre>
 *
 * @author Patra 架构团队
 * @since 1.0.0
 */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
    "logging.level.com.patra.test=DEBUG",
    "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class BaseIntegrationTest {

    @Autowired
    protected DataSource dataSource;

    @Autowired(required = false)
    protected JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    protected RedisTemplate<String, String> redisTemplate;

    /**
     * 清理 Redis 数据
     *
     * <p>在测试方法执行后调用,清理 Redis 中的测试数据。</p>
     */
    protected void cleanRedis() {
        if (redisTemplate != null) {
            Objects.requireNonNull(redisTemplate.getConnectionFactory())
                .getConnection()
                .serverCommands()
                .flushDb();
        }
    }
}
