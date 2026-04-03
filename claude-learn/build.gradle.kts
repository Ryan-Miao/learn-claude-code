plugins {
    java
    id("org.springframework.boot") version "4.0.4"
    id("io.spring.dependency-management") version "1.1.7"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.0-M4")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// 多入口学习项目，禁用 bootJar（使用 ./gradlew :claude-learn:run -PmainClass=... 运行）
tasks.named("bootJar") { enabled = false }
tasks.named("resolveMainClassName") { enabled = false }
tasks.named("jar") { enabled = true }

// 支持 mvn exec:java 风格: ./gradlew :claude-learn:run -PmainClass=com.demo.learn.s01.S01AgentLoop
tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run a specific main class (use -PmainClass=...)"
    mainClass.set(provider {
        project.findProperty("mainClass")?.toString()
            ?: throw GradleException("Please specify -PmainClass=...")
    })
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}
