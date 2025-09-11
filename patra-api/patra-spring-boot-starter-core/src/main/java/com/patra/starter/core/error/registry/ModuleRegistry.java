package com.patra.starter.core.error.registry;

import cn.hutool.core.util.StrUtil;
import com.patra.common.error.core.ErrorSpec;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 模块前缀注册表，管理系统模块的元数据信息。
 * 
 * <p>主要功能：
 * <ul>
 *   <li>管理模块前缀（如 REG、VAU、QRY 等）与其元数据的映射关系</li>
 *   <li>支持从类路径资源自动加载模块定义</li>
 *   <li>提供模块前缀的有效性验证功能</li>
 *   <li>支持运行时动态注册模块信息</li>
 * </ul>
 * 
 * <p>数据来源：
 * <ol>
 *   <li>内存直接注册（register() 方法）</li>
 *   <li>类路径资源：META-INF/patra/module-*.properties（支持多份文件自动合并）</li>
 *   <li>可选支持：META-INF/patra/module-*.json（需要 Jackson 运行时支持）</li>
 * </ol>
 * 
 * <p>配置文件格式示例：
 * <pre>{@code
 * # module-registry.properties
 * REG.owner=registry-team
 * REG.description=User registration module
 * REG.doc=https://docs.example.com/modules/registration
 * 
 * VAU.owner=validation-team
 * VAU.description=Validation module
 * VAU.doc=https://docs.example.com/modules/validation
 * }</pre>
 * 
 * <p>设计目标：
 * <ul>
 *   <li>在开发/测试/CI 环境中对模块前缀进行轻量级校验</li>
 *   <li>提供模块与团队责任人的关联关系</li>
 *   <li>支持模块文档和描述信息的集中管理</li>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 * @see ErrorSpec
 */
@Slf4j
public final class ModuleRegistry {

    /**
     * 模块信息记录，包含模块的基本元数据。
     * 
     * @param module 模块前缀，必须符合 ErrorSpec.MODULE_PATTERN 规范
     * @param owner 模块负责人或团队
     * @param description 模块描述信息
     * @param doc 模块文档链接
     */
    public record ModuleInfo(String module, String owner, String description, String doc) {
        
        /**
         * 创建 ModuleInfo 实例并验证模块前缀格式。
         * 
         * @param module 模块前缀
         * @param owner 负责人
         * @param description 描述
         * @param doc 文档链接
         * @throws IllegalArgumentException 如果模块前缀格式不符合规范
         */
        public ModuleInfo {
            if (StrUtil.isBlank(module) || !ErrorSpec.MODULE_PATTERN.matcher(module).matches()) {
                throw new IllegalArgumentException("Invalid module prefix: " + module);
            }
        }
    }

    /**
     * 模块信息存储映射，键为模块前缀，值为模块信息。
     * 使用 TreeMap 保证输出顺序的一致性。
     */
    private final Map<String, ModuleInfo> modules = new TreeMap<>();

    /**
     * 默认构造器，创建空的模块注册表。
     */
    public ModuleRegistry() {
    }

    /**
     * 检查指定的字符串是否为已知的模块前缀。
     * 
     * @param module 模块前缀字符串，区分大小写
     * @return 如果是已知模块返回 true，否则返回 false
     */
    public boolean isKnownModule(String module) {
        return StrUtil.isNotBlank(module) && modules.containsKey(module);
    }

    /**
     * 注册一个模块信息，如果模块前缀已存在则覆盖。
     * 
     * @param moduleInfo 模块信息对象
     * @throws NullPointerException 如果 moduleInfo 为 null
     */
    public void register(ModuleInfo moduleInfo) {
        Objects.requireNonNull(moduleInfo, "ModuleInfo must not be null");
        modules.put(moduleInfo.module(), moduleInfo);
        log.debug("Registered module: {}", moduleInfo.module());
    }

    /**
     * 批量注册模块信息。
     * 
     * @param moduleInfoList 模块信息列表，可以为 null 或空
     */
    public void registerAll(Collection<ModuleInfo> moduleInfoList) {
        if (moduleInfoList != null && !moduleInfoList.isEmpty()) {
            moduleInfoList.forEach(this::register);
        }
    }

    /**
     * 查询
     */
    public Optional<ModuleInfo> find(String module) {
        return Optional.ofNullable(modules.get(module));
    }

    /**
     * 返回不可变视图
     */
    public Map<String, ModuleInfo> all() {
        return Collections.unmodifiableMap(modules);
    }

    // ---------- 资源加载（properties 与可选 json） ----------

    public void loadFromClasspath(ClassLoader cl) {
        Objects.requireNonNull(cl, "classLoader");
        loadFromPropertiesResources(cl, "META-INF/patra/module-registry.properties");
//        // JSON 为可选：仅在运行时存在 Jackson 时加载
//        if (jacksonPresent()) {
//            loadFromJsonResources(cl, "META-INF/patra/module-registry.json");
//        }
    }

    private void loadFromPropertiesResources(ClassLoader cl, String path) {
        try {
            Enumeration<URL> urls = cl.getResources(path);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                Properties p = new Properties();
                try (var in = url.openStream()) {
                    p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
                // 约定：每个模块以 module.<prefix>.owner/desc/doc 形式
                // 例如：module.REG.owner=registry-team
                var byPrefix = new HashMap<String, ModuleInfo>();
                p.stringPropertyNames().forEach(k -> {
                    if (k.startsWith("module.")) {
                        // module.REG.owner -> REG / owner
                        String[] parts = k.split("\\.");
                        if (parts.length == 3) {
                            String prefix = parts[1];
                            String field = parts[2];
                            ModuleInfo old = byPrefix.getOrDefault(prefix, new ModuleInfo(prefix, "", "", ""));
                            String owner = old.owner();
                            String desc = old.description();
                            String doc = old.doc();
                            switch (field) {
                                case "owner" -> owner = p.getProperty(k);
                                case "desc" -> desc = p.getProperty(k);
                                case "doc" -> doc = p.getProperty(k);
                                default -> {
                                }
                            }
                            byPrefix.put(prefix, new ModuleInfo(prefix, owner, desc, doc));
                        }
                    }
                });
                registerAll(byPrefix.values());
            }
        } catch (IOException e) {
            // 忽略加载错误，仅打印到 stderr（不干扰主流程）
            System.err.println("[ModuleRegistry] load properties failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFromJsonResources(ClassLoader cl, String path) {
        try {
            Enumeration<URL> urls = cl.getResources(path);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                String json;
                try (var in = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                    json = in.lines().reduce(new StringBuilder(), StringBuilder::append, StringBuilder::append).toString();
                }
                // 结构约定：[{ "module":"REG", "owner":"...", "description":"...", "doc":"..." }, ...]
                var list = (List<Map<String, Object>>) jacksonReadList(json);
                if (list != null) {
                    for (var map : list) {
                        String module = Objects.toString(map.get("module"), null);
                        String owner = Objects.toString(map.get("owner"), "");
                        String desc = Objects.toString(map.getOrDefault("description", map.get("desc")), "");
                        String doc = Objects.toString(map.get("doc"), "");
                        if (module != null) {
                            register(new ModuleInfo(module, owner, desc, doc));
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[ModuleRegistry] load json failed: " + e.getMessage());
        }
    }

    // ---------- 轻量的 Jackson 反射桥（可选） ----------

    private static boolean jacksonPresent() {
        try {
            Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 使用反射调用 Jackson：ObjectMapper().readValue(json, List.class)
     */
    private static Object jacksonReadList(String json) {
        try {
            Class<?> omClz = Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            Object om = omClz.getConstructor().newInstance();
            return omClz.getMethod("readValue", String.class, Class.class)
                    .invoke(om, json, List.class);
        } catch (Throwable t) {
            System.err.println("[ModuleRegistry] Jackson reflection failed: " + t.getMessage());
            return null;
        }
    }
}
