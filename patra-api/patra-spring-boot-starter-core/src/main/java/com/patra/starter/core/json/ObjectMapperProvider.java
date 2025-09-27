package com.patra.starter.core.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.json.JsonMapperHolder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;

/**
 * 全局 {@link ObjectMapper} 提供者（Spring ↔ 非 Spring 的桥接层）。
 * <p>
 * 作用与定位：
 * <ul>
 *   <li><b>在 Spring 环境中</b>，Starter 内部需要 JSON 能力时，优先通过依赖注入（DI）拿到
 *   容器管理的 {@code ObjectMapper}。但对于 <em>初始化早期的静态代码</em> 或 <em>非 Spring 代码路径</em>
 *   （如工具模块、SDK、公用库）无法直接注入的场景，本类在容器就绪时，将 Spring 管理的
 *   {@code ObjectMapper} 注册到 {@link JsonMapperHolder}，实现两侧配置的一致性。</li>
 *   <li><b>在非 Spring 环境中</b>，{@link JsonMapperHolder} 会懒加载一个默认配置的 {@code ObjectMapper}；
 *   一旦运行在 Spring 中，本类会将容器内实例覆盖注册，确保 Starter 与 Common 层的 JSON 行为统一。</li>
 * </ul>
 *
 * <h3>与 Spring 中 <i>依赖注入（DI）</i> 的区别</h3>
 * <p>
 * <b>推荐做法</b>（业务代码）：
 * <pre>{@code
 * @Service
 * class FooService {
 *   private final ObjectMapper om;
 *   FooService(ObjectMapper om) { this.om = om; }
 * }
 * }</pre>
 * DI 由容器管理生命周期、配置与替换性（如测试桩），可测试性更好。
 * </p>
 * <p>
 * <b>本类用途</b>：桥接器 + 兜底提供者。它并不鼓励在业务组件里以“服务定位器”方式替代 DI 使用；
 * 它的职责是：在容器启动后把 <em>容器中的</em> {@code ObjectMapper} 注册进 {@link JsonMapperHolder}，
 * 让无法注入的地方（静态工具、非 Spring 代码路径）也能拿到与容器一致的实例。
 * </p>
 *
 * <h3>生命周期与线程安全</h3>
 * <ul>
 *   <li>Spring 创建本 Bean 后会回调 {@link #setApplicationContext(ApplicationContext)} 一次；
 *   这里会缓存容器内的 {@code ObjectMapper}，并调用 {@link JsonMapperHolder#register(ObjectMapper)}
 *   完成全局注册。</li>
 *   <li>注册操作是幂等覆盖：后注册会替换 Holder 中的实例。通常只在启动期发生一次。</li>
 *   <li>{@link #getObjectMapper()} 若本地静态字段尚未就绪，将回退到 {@link JsonMapperHolder} 的实例，
 *   从而保证在不同初始化时序下也能工作。</li>
 * </ul>
 *
 * <h3>使用建议</h3>
 * <ul>
 *   <li>业务/组件内 <b>优先使用 DI</b> 注入 {@code ObjectMapper}；</li>
 *   <li>仅当无法注入（静态工具、非 Spring 路径）时，使用 {@link JsonMapperHolder#getObjectMapper()}；</li>
 *   <li>不要在运行期手动重复注册不同实例，避免配置漂移。</li>
 * </ul>
 */
public class ObjectMapperProvider implements ApplicationContextAware {

    /**
     * 缓存容器中的 ObjectMapper；存在即视为容器已就绪。
     */
    private static ObjectMapper objectMapper;

    /**
     * Spring 容器就绪时回调：提取容器内的 {@link ObjectMapper} 并注册到 {@link JsonMapperHolder}。
     * <p>
     * 这样，非 Spring 代码路径也能拿到与容器一致的配置，避免默认实例与容器实例产生行为不一致。
     */
    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) {
        // Spring Boot 默认会注册一个全局 ObjectMapper Bean
        objectMapper = applicationContext.getBean(ObjectMapper.class);
        // 桥接到 Common 层的全局持有者，统一两端世界
        JsonMapperHolder.register(objectMapper);
    }

    /**
     * 获取 {@link ObjectMapper}：
     * <ul>
     *   <li>容器已就绪：返回容器中的实例；</li>
     *   <li>容器尚未就绪或未缓存：回退到 {@link JsonMapperHolder}（可能为懒加载默认实例）。</li>
     * </ul>
     * <b>注意</b>：在业务代码里仍应优先采用 DI 注入；本方法是桥接/兜底，不用于替代 DI。
     */
    public static ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            return JsonMapperHolder.getObjectMapper();
        }
        return objectMapper;
    }
}
