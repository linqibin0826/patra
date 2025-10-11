package com.patra.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Global (non-Spring) holder for a shared {@link ObjectMapper}.
 * <p>Goals:</p>
 * <ul>
 *   <li>Provide infrastructure and utility code that runs outside Spring with a
 *       consistent JSON configuration.</li>
 *   <li>Avoid repeatedly instantiating {@code ObjectMapper}, which leads to
 *       divergent configuration and unnecessary cost.</li>
 *   <li>Stay aligned with the Spring-managed mapper by allowing the starter to
 *       bridge the container instance into this holder.</li>
 * </ul>
 *
 * <h3>Relationship to Spring dependency injection</h3>
 * <p>Within Spring, prefer constructor injection:</p>
 * <pre>{@code
 * @Autowired
 * private ObjectMapper objectMapper;
 * }</pre>
 * <p>The container manages lifecycle, configuration, and scope, keeping code
 * testable and replaceable.</p>
 * <p>This class is a static holder for contexts where DI is unavailable. Key
 * differences:</p>
 * <ul>
 *   <li><b>Source</b>: DI supplies the mapper; the holder relies on explicit
 *       registration or lazy fallback.</li>
 *   <li><b>Lifecycle</b>: DI is container-managed; the holder initializes on
 *       first access or when {@link #register(ObjectMapper)} is invoked.</li>
 *   <li><b>Consistency</b>: The fallback mapper is built with
 *       {@link JsonMapper#builder()} and may diverge from custom application
 *       settings; register early if you need parity.</li>
 *   <li><b>Use cases</b>: Suitable for static helpers, SDKs, or driver code that
 *       cannot rely on DI. Avoid using it inside business services to preserve
 *       testability.</li>
 * </ul>
 *
 * <h3>Bridging from Spring</h3>
 * <p>When {@code patra-spring-boot-starter-core} is on the classpath, its
 * {@code JacksonProvider} registers the container-managed mapper once the
 * {@code ApplicationContext} is ready. Outside Spring, {@link #getObjectMapper()}
 * lazily creates a default mapper.</p>
 *
 * <h3>Thread safety</h3>
 * <p>An {@link AtomicReference} provides safe publication and lazy
 * initialization. The first access creates the default mapper via CAS; explicit
 * registration replaces the current instance.</p>
 *
 * <h3>Usage guidelines</h3>
 * <ul>
 *   <li>Prefer DI in Spring-managed code; fall back to this holder only where
 *       injection is impossible.</li>
 *   <li>Register the container-managed mapper early in application startup to
 *       avoid switching from the fallback instance later.</li>
 *   <li>No reset method is provided to prevent accidental runtime switching. For
 *       tests, register a test mapper at suite setup and restore afterwards.</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * // Non-Spring usage
 * ObjectMapper om = JsonMapperHolder.getObjectMapper();
 * String json = om.writeValueAsString(payload);
 *
 * // Spring usage (registration happens in JacksonProvider)
 * @Service
 * public class FooService {
 *   private final ObjectMapper om; // still recommended via DI
 *   public FooService(ObjectMapper om) { this.om = om; }
 * }
 * }</pre>
 */
public final class JsonMapperHolder {

    /**
     * Global {@link ObjectMapper} reference. {@code null} indicates that no
     * instance has been registered yet.
     */
    private static final AtomicReference<ObjectMapper> HOLDER = new AtomicReference<>();

    private JsonMapperHolder() {
    }

    /**
     * Returns the shared {@link ObjectMapper}.
     * <p>Registered instances take precedence. If none is registered, a default
     * {@link JsonMapper} is lazily created with
     * {@link JsonMapper.Builder#findAndAddModules()}.</p>
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
     * Registers (or replaces) the global {@link ObjectMapper}.
     * <p>Typical callers:</p>
     * <ul>
     *   <li>Spring's {@code JacksonProvider} after the context is ready.</li>
     *   <li>Bootstrap code in non-Spring environments.</li>
     * </ul>
     * <p>Avoid frequent changes at runtime to prevent configuration drift.</p>
     */
    public static void register(ObjectMapper objectMapper) {
        HOLDER.set(Objects.requireNonNull(objectMapper, "objectMapper must not be null"));
    }

    /** Creates the fallback {@link ObjectMapper} used when none is registered. */
    private static ObjectMapper createDefault() {
        return JsonMapper.builder()
                .findAndAddModules()
                .build();
    }
}
