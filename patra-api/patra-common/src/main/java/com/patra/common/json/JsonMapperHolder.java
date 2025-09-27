package com.patra.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 全局（非 Spring）{@link ObjectMapper} 持有与获取器。
 * <p>
 * 设计目标：
 * <ul>
 *   <li>为 <b>不依赖 Spring</b> 的基础设施/工具类提供统一的 JSON 序列化配置来源；</li>
 *   <li>避免在每个调用点反复 new {@code ObjectMapper} 带来的性能与配置漂移；</li>
 *   <li>保持与 Spring 环境的配置一致性——当存在 Spring 时，通过桥接在启动期注入应用内的{@code ObjectMapper}。</li>
 * </ul>
 *
 * <h3>与 Spring 中的注入方式的区别</h3>
 * <p>
 * 在 Spring 环境中，推荐的做法是 <b>依赖注入（DI）</b>：
 * <pre>{@code
 * @Autowired
 * private ObjectMapper objectMapper;
 * }</pre>
 * 这种方式由 Spring 管理生命周期、配置与范围，具备天然的可测试性与可替换性。
 * </p>
 * <p>
 * 本类则是 <b>静态全局持有者</b>（不依赖 Spring）。关键差异：
 * <ul>
 *   <li><b>来源</b>：DI 由容器提供；Holder 来自 <em>显式注册</em> 或 <em>懒加载默认值</em>。</li>
 *   <li><b>生命周期</b>：DI 由容器管理；Holder 的实例在首次获取时初始化或被注册时覆盖。</li>
 *   <li><b>一致性</b>：DI 与应用配置完全一致；Holder 的默认实例仅通过 {@link JsonMapper#builder()} + {@link JsonMapper.Builder#findAndAddModules()}，可能与应用自定义配置有差异。</li>
 *   <li><b>使用场景</b>：
 *     <ul>
 *       <li>推荐：在 <b>非 Spring</b> 代码路径（例如通用工具、SDK、驱动层）需要 JSON 能力时使用。</li>
 *       <li>不推荐：在业务服务/组件里替代 DI 使用，避免破坏可测试性与配置隔离。</li>
 *     </ul>
 *   </li>
 * </ul>
 * </p>
 *
 * <h3>与 Spring 的桥接</h3>
 * <p>
 * 当项目启用了 {@code patra-spring-boot-starter-core}，其 {@code JacksonProvider}
 * 会在 {@code ApplicationContext} 就绪后调用 {@link #register(ObjectMapper)}，
 * 将 Spring 管理的 {@code ObjectMapper} 注入到本 Holder 中，实现两套世界的配置统一。
 * 没有 Spring 时，{@link #getObjectMapper()} 会按需创建一个默认的 {@code ObjectMapper}。
 * </p>
 *
 * <h3>线程安全</h3>
 * <p>
 * 通过 {@link AtomicReference} 实现 <b>安全发布</b> 与 <b>懒加载</b>。首次获取时使用 CAS
 *（Compare-And-Set）写入默认实例；随后所有线程可见同一实例。显式调用 {@link #register(ObjectMapper)}
 * 会用提供的实例替换当前实例。
 * </p>
 *
 * <h3>使用建议</h3>
 * <ul>
 *   <li>Spring 环境优先使用 <b>依赖注入</b> 的 {@code ObjectMapper}；仅在无法注入的静态/基础设施代码中使用本 Holder。</li>
 *   <li>务必在应用启动早期完成 {@link #register(ObjectMapper)}，以避免先取到默认实例、后又被覆盖导致行为不一致。</li>
 *   <li>本类 <b>不提供 reset</b> 接口，避免运行期被随意切换实例；测试中如需隔离，可在套件级别注册测试专用实例并在结束后再注册回原实例。</li>
 * </ul>
 *
 * <h3>示例</h3>
 * <pre>{@code
 * // 非 Spring 环境
 * ObjectMapper om = JsonMapperHolder.getObjectMapper();
 * String json = om.writeValueAsString(somePojo);
 *
 * // Spring 环境（在 JacksonProvider 中会自动 register）
 * @Service
 * public class FooService {
 *   private final ObjectMapper om; // 仍推荐注入
 *   public FooService(ObjectMapper om) { this.om = om; }
 * }
 * }</pre>
 */
public final class JsonMapperHolder {

    /**
     * 全局唯一的 {@link ObjectMapper} 持有者。
     * <p>
     * 为空表示尚未注册；{@link #getObjectMapper()} 将在首次访问时用默认配置创建并 CAS 设置。
     */
    private static final AtomicReference<ObjectMapper> HOLDER = new AtomicReference<>();

    private JsonMapperHolder() { }

    /**
     * 获取全局 {@link ObjectMapper}。
     * <p>
     * 优先返回已注册实例；若尚未注册，则创建一个 <b>默认配置</b> 的 {@link JsonMapper}：
     * <ul>
     *   <li>启用 {@link JsonMapper.Builder#findAndAddModules()}，自动发现并注册模块（如 jsr310 等）。</li>
     * </ul>
     * 注意：默认实例可能与应用在 Spring 中的自定义配置不一致，<b>如需一致性请在启动期调用 {@link #register(ObjectMapper)}</b>。
     */
    public static ObjectMapper getObjectMapper() {
        ObjectMapper mapper = HOLDER.get();
        if (mapper != null) {
            return mapper;
        }
        ObjectMapper created = createDefault();
        if (HOLDER.compareAndSet(null, created)) {
            return created;
        }
        return HOLDER.get();
    }

    /**
     * 注册（或覆盖）全局 {@link ObjectMapper}。
     * <p>
     * 典型调用方：
     * <ul>
     *   <li>Spring 启动完成后，由 {@code JacksonProvider} 将容器内的 {@code ObjectMapper} 注册进来；</li>
     *   <li>非 Spring 环境的引导代码，在应用入口处注册自定义配置的 {@code ObjectMapper}。</li>
     * </ul>
     * <b>警告：</b>不建议在业务运行期频繁变更注册的实例，以免造成行为漂移。
     */
    public static void register(ObjectMapper objectMapper) {
        HOLDER.set(Objects.requireNonNull(objectMapper, "objectMapper 不能为空"));
    }

    /**
     * 创建一个默认的 {@link ObjectMapper}，仅在未注册时作为回退使用。
     */
    private static ObjectMapper createDefault() {
        return JsonMapper.builder()
                .findAndAddModules()
                .build();
    }
}
