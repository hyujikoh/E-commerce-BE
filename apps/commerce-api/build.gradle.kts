plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
}

dependencies {
    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")

    // security (BCryptPasswordEncoder)
    implementation("org.springframework.security:spring-security-crypto")

    // external call (PG) — feign + resilience4j
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("io.github.resilience4j:resilience4j-spring-boot3:${project.properties["resilience4jVersion"]}")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // querydsl
    kapt("com.querydsl:querydsl-apt::jakarta")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
}
