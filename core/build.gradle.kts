// 纯 Java 核心：零第三方依赖，跨加载器（NeoForge/Forge/Fabric）与跨版本复用
plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
