plugins {
    id("java")
    id("java-library")
    id("jacoco")
}

group = "org.example"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.apache.jmeter:ApacheJMeter_core:5.6.3")
    testImplementation("org.apache.jmeter:ApacheJMeter_components:5.6.3")
    testImplementation("org.apache.jmeter:ApacheJMeter_java:5.6.3")

    compileOnly("org.apache.jmeter:ApacheJMeter_core:5.6.3")
    compileOnly("org.apache.jmeter:ApacheJMeter_components:5.6.3")
    compileOnly("org.apache.jmeter:ApacheJMeter_java:5.6.3")
    compileOnly("org.apache.logging.log4j:log4j-api:2.20.0")
    compileOnly("org.apache.logging.log4j:log4j-core:2.20.0")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(sourceSets.main.get().output.asFileTree.matching {
            exclude("org/example/jmeter/**", "org/example/Main.class", "org/example/core/accumulator/**", "org/example/core/model/**")
        })
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(sourceSets.main.get().output.asFileTree.matching {
            exclude("org/example/jmeter/**", "org/example/Main.class", "org/example/core/accumulator/**", "org/example/core/model/**")
        })
    )
    violationRules {
        rule {
            limit {
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}