// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.google.devtools.ksp.gradle.KspAATask
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.mavenPublish) apply false
  alias(libs.plugins.dokka)
  alias(libs.plugins.detekt)
  alias(libs.plugins.lint) apply false
  alias(libs.plugins.ksp) apply false
}

dokka {
  dokkaPublications.html {
    outputDirectory.set(rootDir.resolve("docs/api/1.x"))
    includes.from(project.layout.projectDirectory.file("docs/index.md"))
  }
}

dependencies { dokka(project(":compose-lint-checks")) }

subprojects {
  pluginManager.withPlugin("java") {
    configure<JavaPluginExtension> {
      toolchain { languageVersion.set(libs.versions.jdk.map(JavaLanguageVersion::of)) }
    }

    tasks.withType<JavaCompile>().configureEach {
      options.release.set(libs.versions.jvmTarget.map(String::toInt))
    }
  }

  pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    tasks.withType<KotlinCompile>().configureEach {
      compilerOptions {
        jvmTarget.set(libs.versions.jvmTarget.map(JvmTarget::fromTarget))
        // TODO re-enable once lint uses Kotlin 1.5
        //  allWarningsAsErrors = true
        //  freeCompilerArgs = freeCompilerArgs + listOf("-progressive")
      }
    }
  }

  tasks.withType<Detekt>().configureEach { jvmTarget = libs.versions.jvmTarget.get() }

  pluginManager.withPlugin("com.vanniktech.maven.publish") {
    apply(plugin = "org.jetbrains.dokka")

    configure<DokkaExtension> {
      basePublicationsDirectory.set(layout.buildDirectory.dir("dokkaDir"))
      dokkaSourceSets.configureEach { skipDeprecated.set(true) }
    }

    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(automaticRelease = true)
      signAllPublications()
    }
  }

  // TODO workaround for https://issuetracker.google.com/issues/269089135
  pluginManager.withPlugin("com.google.devtools.ksp") {
    tasks.withType<AndroidLintAnalysisTask>().configureEach {
      mustRunAfter(tasks.withType<KspAATask>())
    }
    tasks.withType<LintModelWriterTask>().configureEach {
      mustRunAfter(tasks.withType<KspAATask>())
    }
  }
}
