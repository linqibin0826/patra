/**
 * Build Logic Settings
 *
 * 配置 Convention Plugins 项目的依赖解析
 */

rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    // 从主项目继承 Version Catalog
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
