package com.patra.registry;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapperProbe {

  @Bean
  public static BeanFactoryPostProcessor probe() {
    return (ConfigurableListableBeanFactory bf) -> {
      try {
        Class<?> api = Class.forName(
            "com.patra.registry.infra.mapstruct.PlatformFieldDictQueryConverter");
        String[] names = bf.getBeanNamesForType(api, true, true);
        System.out.println("[Probe] bean count = " + names.length);
        for (String n : names) {
          System.out.println("[Probe] candidate bean name = " + n);
        }
        try {
          Class<?> impl = Class.forName(
              "com.patra.registry.infra.mapstruct.PlatformFieldDictQueryConverterImpl");
          System.out.println("[Probe] Impl loader = " + impl.getClassLoader());
          System.out.println("[Probe] Impl resource = " +
              impl.getResource("PlatformFieldDictQueryConverterImpl.class"));
        } catch (ClassNotFoundException e) {
          System.out.println("[Probe] Impl NOT FOUND on classpath");
        }
        System.out.println("[Probe] API loader = " + api.getClassLoader());
        System.out.println("[Probe] API resource = " +
            api.getResource("PlatformFieldDictQueryConverter.class"));
      } catch (ClassNotFoundException e) {
        System.out.println("[Probe] API class NOT FOUND");
      }
    };
  }
}
