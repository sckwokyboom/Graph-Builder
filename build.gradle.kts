plugins {
    java
    application
}

group = "com.graphbuilder"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "com.graphbuilder.cli.Main"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.eclipse.jdt:org.eclipse.jdt.core:3.40.0")
    implementation("org.eclipse.platform:org.eclipse.core.runtime:3.31.100")
    implementation("org.eclipse.platform:org.eclipse.core.resources:3.21.0")
    implementation("org.eclipse.platform:org.eclipse.core.contenttype:3.9.400")
    implementation("org.eclipse.platform:org.eclipse.equinox.common:3.19.100")
    implementation("org.eclipse.platform:org.eclipse.equinox.preferences:3.11.100")
    implementation("org.eclipse.platform:org.eclipse.core.jobs:3.15.300")
    implementation("org.eclipse.platform:org.eclipse.text:3.14.100")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.graphbuilder.cli.Main"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
