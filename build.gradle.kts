plugins {
    java
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.node-gradle.node") version "7.1.0"
}

group = "com.tsadmin"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

node {
    nodeProjectDir.set(file("frontend"))
}

repositories {
    maven {
        url = uri("https://maven.aliyun.com/repository/public")
    }
    mavenCentral()
}

dependencies {
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<com.github.gradle.node.yarn.task.YarnTask>("yarnInstall") {
    args.set(listOf("install"))
    workingDir.set(file("frontend"))
}

tasks.register<com.github.gradle.node.yarn.task.YarnTask>("yarnBuild") {
    args.set(listOf("run", "build"))
    workingDir.set(file("frontend"))
    dependsOn("yarnInstall")
}

tasks.register<Copy>("copyFrontend") {
    dependsOn("yarnBuild")
    from("frontend/dist")
    into("src/main/resources/static")
    doFirst {
        delete("src/main/resources/static")
    }
}

tasks.processResources {
    dependsOn("copyFrontend")
}

tasks.clean {
    delete("frontend/dist", "src/main/resources/static")
}
