package com.patra.starter.core.error.registry;


import com.patra.common.error.core.ErrorSpec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 模块前缀注册表（REG/VAU/QRY/...）。
 * - 数据来源：
 * 1) 内存注册（register()）
 * 2) 类路径资源：META-INF/patra/module-registry.properties（可多份，自动合并）
 * 3) 可选：META-INF/patra/module-registry.json（若运行时存在 Jackson）
 * <p>
 * - 目的：在运行/测试/CI 时对模块前缀进行轻量校验与说明关联。
 */
public final class ModuleRegistry {

    /**
     * 描述一个模块前缀的基本信息
     */
    public record ModuleInfo(String module, String owner, String description, String doc) {
    }

    private final Map<String, ModuleInfo> modules = new TreeMap<>();

    public ModuleRegistry() {
    }

    /**
     * 是否为已知模块前缀（严格大小写）
     */
    public boolean isKnownModule(String module) {
        return modules.containsKey(module);
    }

    /**
     * 注册一个模块记录（重复则覆盖）
     */
    public void register(ModuleInfo info) {
        Objects.requireNonNull(info, "info");
        if (!ErrorSpec.MODULE_PATTERN.matcher(info.module()).matches()) {
            throw new IllegalArgumentException("Invalid module prefix: " + info.module());
        }
        modules.put(info.module(), info);
    }

    /**
     * 批量注册
     */
    public void registerAll(Collection<ModuleInfo> list) {
        if (list == null) return;
        list.forEach(this::register);
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
