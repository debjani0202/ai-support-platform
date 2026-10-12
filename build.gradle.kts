
plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.supportai"
version = "0.0.1-SNAPSHOT"
description = "ai-support-platform"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    implementation("org.flywaydb:flyway-database-postgresql")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("user.timezone", "Asia/Kolkata")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    jvmArgs("-Duser.timezone=UTC")
}

// Generates the auto-managed section of README.md.
tasks.register("updateReadme") {
    group = "documentation"
    description = "Updates the auto-generated sections of README.md."

    doLast {
        val projectDir = layout.projectDirectory.asFile
        val readme = projectDir.resolve("README.md")
        val sourceDir = projectDir.resolve("src/main/java")
        val buildFile = projectDir.resolve("build.gradle.kts")

        val startMarker = "<!-- AUTO:START -->"
        val endMarker = "<!-- AUTO:END -->"

        require(readme.exists()) {
            "README.md does not exist in the project root."
        }

        require(sourceDir.exists()) {
            "Java source directory was not found."
        }

        // Find Java files and display their project-relative paths.
        val sourceFiles = sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .map {
                it.relativeTo(projectDir)
                    .path.replace("\\", "/")
            }
            .sorted()
            .toList()

        // Extract simple dependency declarations from Gradle.
        val dependencyLines = buildFile.readLines()
            .filter { line ->
                val declaration = line.trim()

                listOf(
                    "implementation(",
                    "runtimeOnly(",
                    "compileOnly(",
                    "annotationProcessor(",
                    "testImplementation(",
                    "testRuntimeOnly("
                ).any { declaration.startsWith(it) }
            }
            .map { "- `${it.trim()}`" }
            .ifEmpty {
                listOf("- No dependencies detected.")
            }

        // Construct the automatically generated README content.
        val generated = buildString {
            appendLine()
            appendLine("### Project Structure")
            appendLine()
            sourceFiles.forEach {
                appendLine("- `$it`")
            }

            appendLine()
            appendLine("### Gradle Dependencies")
            appendLine()
            dependencyLines.forEach {
                appendLine(it)
            }
        }.trimEnd()

        val original = readme.readText()

        val startIndex = original.indexOf(startMarker)
        val endIndex = original.indexOf(endMarker)

        // Avoid accidentally overwriting a README without valid markers.
        require(
            startIndex >= 0 &&
            endIndex > startIndex
        ) {
            "README.md must contain AUTO:START followed by AUTO:END."
        }

        // Preserve the content before and after the generated section.
        val contentStart = startIndex + startMarker.length

        val updated = original.substring(0, contentStart) +
            "\n" + generated + "\n" +
            original.substring(endIndex)

        if (updated != original) {
            readme.writeText(updated)
            println("README.md updated successfully.")
        } else {
            println("README.md is already up to date.")
        }
    }
}
