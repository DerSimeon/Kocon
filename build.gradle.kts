import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    alias(libs.plugins.mavenPublish)
}

group = "lol.simeon"
// Release version comes from the publish tag (v1.2.3 -> 1.2.3); the Central Portal rejects SNAPSHOTs.
version = providers.gradleProperty("kocon.version").orElse("0.1.0").get()

repositories {
    mavenCentral()
}

dependencies {
    // api: @Serializable types and KSerializer appear in Kocon's public signatures.
    api(libs.kotlinx.serialization.core)
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    detektPlugins(project(":detekt-rules"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

// Stable module name for consumers on the module path.
tasks.withType<Jar>().configureEach {
    manifest {
        attributes("Automatic-Module-Name" to "lol.simeon.kocon")
    }
}

// Detekt's default rules plus the one-class-per-file rule set from :detekt-rules.
detekt {
    buildUponDefaultConfig = true
}

kover {
    reports {
        verify {
            rule("Line coverage") {
                minBound(80)
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.koverVerify)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
    signAllPublications()
    coordinates("lol.simeon", "kocon", version.toString())
    // Empty javadoc jar: Dokka's pipeline does not yet run on the JDK 25 toolchain.
    configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = true))

    pom {
        name.set("Kocon")
        description.set(
            "Kocon is a small Kotlin library that maps @Serializable data classes to HOCON config files, " +
                "creating them from defaults, filling in missing keys, and writing comments from annotations.",
        )
        url.set("https://github.com/DerSimeon/Kocon")
        licenses {
            license {
                name.set("BSD 3-Clause License")
                url.set("https://opensource.org/licenses/BSD-3-Clause")
            }
        }
        developers {
            developer {
                id.set("DerSimeon")
                name.set("Simeon L")
            }
        }
        scm {
            url.set("https://github.com/DerSimeon/Kocon")
            connection.set("scm:git:https://github.com/DerSimeon/Kocon.git")
            developerConnection.set("scm:git:ssh://git@github.com/DerSimeon/Kocon.git")
        }
    }
}
