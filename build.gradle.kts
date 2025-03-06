val kotlin_version: String by project
val logback_version: String by project
val ktor_version: String by project
val koin_version: String by project
val flexmark_version: String by project

plugins {
    kotlin("jvm") version "2.1.0"
    id("io.ktor.plugin") version "3.0.1"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "com"
version = "0.0.1"

application {
    mainClass.set("com.flow2.ApplicationKt")
}

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    // KTOR
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-config-yaml:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-sessions:$ktor_version")
    implementation("io.ktor:ktor-server-thymeleaf:$ktor_version")
    implementation("io.ktor:ktor-server-cors:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-resources:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlin_version")

    // MONGODB
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:5.1.2")
    implementation("org.mongodb:bson-kotlinx:5.1.2")

    // KOIN
    implementation("io.insert-koin:koin-core:$koin_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_version")
    implementation("io.insert-koin:koin-ktor:$koin_version")

    // FLEXMARK
    implementation("com.vladsch.flexmark:flexmark-all:$flexmark_version")
    implementation("com.vladsch.flexmark:flexmark-ext-wikilink:$flexmark_version")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:$flexmark_version")
    implementation("com.vladsch.flexmark:flexmark-ext-attributes:$flexmark_version")
    implementation("com.vladsch.flexmark:flexmark-ext-yaml-front-matter:$flexmark_version")
    testImplementation("com.vladsch.flexmark:flexmark-html2md-converter:$flexmark_version")

    //RSS
    implementation("com.rometools:rome:2.1.0")
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_22)
    }
}
