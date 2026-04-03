// Root project — only configures subprojects
// Each module (claude-learn, etc.) has its own build.gradle.kts

subprojects {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/spring") }
        maven { url = uri("https://maven.aliyun.com/repository/spring-plugin") }
        maven { url = uri("https://repo.spring.io/milestone") }
        mavenCentral()
    }
}
