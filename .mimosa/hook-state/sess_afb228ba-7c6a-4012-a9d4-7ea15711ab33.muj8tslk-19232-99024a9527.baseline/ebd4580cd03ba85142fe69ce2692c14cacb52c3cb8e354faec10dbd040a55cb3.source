// 本地测试后端：不起 Minecraft，验证协议/鉴权/控制台全链路
plugins {
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass = "com.osskn4w7.mcmonitor.harness.Harness"
}
