
plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
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

// JaCoCo code coverage configuration.
jacoco {
    toolVersion = "0.8.14"
}

// Generate coverage reports after running tests.
tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        html.required = true
        xml.required = true
        csv.required = false
    }
}

// Run the test suite before regenerating the README.
val testTask = tasks.named<Test>("test")

// Generates the auto-managed section of README.md.
tasks.register("updateReadme") {
    dependsOn(testTask)
    group = "documentation"
    description = "Updates project structure, dependencies, API mappings and test reports."

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

        // Find Java source files.
        val javaFiles = sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .sortedBy { it.path }
            .toList()

        val sourceFiles = javaFiles.map {
            it.relativeTo(projectDir).path.replace("\\", "/")
        }

        // Read simple dependency declarations from Gradle.
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
            .ifEmpty { listOf("- No dependencies detected.") }

        // Discover common Spring MVC endpoint mappings.
        // This scanner supports simple literal paths, not every Java annotation form.
        val endpointLines = javaFiles.flatMap { file ->
            val content = file.readText()

            if (!Regex("@(?:RestController|Controller)\\b")
                    .containsMatchIn(content)
            ) {
                return@flatMap emptyList<String>()
            }

            // Read a simple class-level base path.
            val classHeader = content.substringBefore("{")
            val basePath = Regex(
                "@RequestMapping\\s*\\(\\s*(?:value\\s*=\\s*|path\\s*=\\s*)?\"([^\"]*)\""
            ).find(classHeader)?.groupValues?.get(1) ?: ""

            // Find method-level HTTP mapping annotations.
            val mappingPattern = Regex(
                "@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)\\b(?:\\s*\\(\\s*(?:value\\s*=\\s*|path\\s*=\\s*)?\"([^\"]*)\"[^)]*\\))?"
            )

            mappingPattern.findAll(content).map { match ->
                val annotation = match.groupValues[1]
                val methodPath = match.groupValues[2]

                val method = when (annotation) {
                    "GetMapping" -> "GET"
                    "PostMapping" -> "POST"
                    "PutMapping" -> "PUT"
                    "PatchMapping" -> "PATCH"
                    "DeleteMapping" -> "DELETE"
                    else -> "UNKNOWN"
                }

                val fullPath = when {
                    basePath.isBlank() -> methodPath
                    methodPath.isBlank() -> basePath
                    else -> basePath.trimEnd('/') +
                        "/" + methodPath.trimStart('/')
                }

                "- `$method` `$fullPath` — `${file.nameWithoutExtension}`"
            }.toList()
        }.distinct().sorted()
            .ifEmpty {
                listOf("- No simple endpoint mappings detected.")
            }

        // Read available JUnit XML test reports.
        // These counts describe existing reports, not necessarily a fresh run.
        val testReportDir = projectDir.resolve("build/test-results/test")

        val testSummary = if (!testReportDir.exists()) {
            "No test reports available. Run `./gradlew test`."
        } else {
            val reportFiles = testReportDir
                .listFiles { file ->
                    file.isFile &&
                        file.name.startsWith("TEST-") &&
                        file.extension == "xml"
                }
                ?.toList()
                .orEmpty()

            var total = 0
            var failures = 0
            var errors = 0
            var skipped = 0

            reportFiles.forEach { report ->
                // Parse test XML using the JDK's XML parser.
                val factory =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance()

                // Prevent XML external-entity access.
                factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true
                )
                factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false
                )
                factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false
                )

                val root = factory.newDocumentBuilder()
                    .parse(report)
                    .documentElement

                total += root.getAttribute("tests").toIntOrNull() ?: 0
                failures += root.getAttribute("failures").toIntOrNull() ?: 0
                errors += root.getAttribute("errors").toIntOrNull() ?: 0
                skipped += root.getAttribute("skipped").toIntOrNull() ?: 0
            }

            if (reportFiles.isEmpty()) {
                "No test report files found. Run the Gradle test task."
            } else {
                "**Latest available report counts:** $total tests, " +
                    "$failures failures, $errors errors, $skipped skipped. " +
                    "These counts do not establish whether the latest test run passed."
            }
        }

        // Generate the managed README section.
        val generated = buildString {
            appendLine()
            appendLine("### Project Structure")
            appendLine()
            sourceFiles.forEach { appendLine("- `$it`") }

            appendLine()
            appendLine("### Gradle Dependencies")
            appendLine()
            dependencyLines.forEach { appendLine(it) }

            appendLine()
            appendLine("### Discovered API Endpoints")
            appendLine()
            endpointLines.forEach { appendLine(it) }

            appendLine()
            appendLine("### Test Results")
            appendLine()
            appendLine(testSummary)
        }.trimEnd()

        val original = readme.readText()
        val startIndex = original.indexOf(startMarker)
        val endIndex = original.indexOf(endMarker)

        require(startIndex >= 0 && endIndex > startIndex) {
            "README.md must contain AUTO:START followed by AUTO:END."
        }

        // Preserve every part of the README outside the managed markers.
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
