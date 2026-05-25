// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlin.jvm)
  // Run lint on the lints! https://groups.google.com/g/lint-dev/c/q_TVEe85dgc
  alias(libs.plugins.lint)
  alias(libs.plugins.ksp)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.shadow)
}

lint {
  htmlReport = true
  xmlReport = true
  textReport = true
  absolutePaths = false
  checkTestSources = true
  baseline = file("lint-baseline.xml")
  disable += setOf("GradleDependency")
  fatal += setOf("LintDocExample", "LintImplPsiEquals", "UastImplementation")
}

tasks.test {
  // Disable noisy java applications launching during tests
  jvmArgs("-Djava.awt.headless=true")
  maxParallelForks = Runtime.getRuntime().availableProcessors() * 2
}

// Dependencies in this configuration are shaded (relocated) into the final artifact. This is needed
// for libraries like kotlin-metadata that aren't available on lint's runtime classpath.
val shade: Configuration = configurations.maybeCreate("compileShaded")

configurations.getByName("compileOnly").extendsFrom(shade)

dependencies {
  compileOnly(libs.lint.api)
  compileOnly(libs.lint.checks)
  ksp(libs.autoService.ksp)
  implementation(libs.autoService.annotations)
  shade(libs.kotlin.metadata) { exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib") }

  // Dupe the dep because the shaded version is compileOnly in the eyes of the gradle configurations
  testImplementation(libs.kotlin.metadata) {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
  }
  testImplementation(libs.bundles.lintTest)
  testImplementation(libs.junit)
}

val kgpKotlinVersion = KotlinVersion.KOTLIN_2_2

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    // Lint forces Kotlin (regardless of what version the project uses), so this
    // forces a matching language level for now. Similar to `targetCompatibility` for Java.
    // Check with kotlin-for-lint.sh
    apiVersion.set(kgpKotlinVersion)
    languageVersion.set(kgpKotlinVersion)
  }
}

val shadowJar =
  tasks.shadowJar.apply {
    configure {
      archiveClassifier.set("")
      configurations = listOf(shade)
      relocate("kotlin.metadata", "slack.lint.compose.shaded.kotlin.metadata")
      transformers.add(ServiceFileTransformer())
    }
  }

artifacts {
  runtimeOnly(shadowJar)
  archives(shadowJar)
}

// shadowJar uses an empty classifier so it replaces the default jar that lint's analysis tasks
// consume. Order those tasks after shadowJar so Gradle's task-dependency validation is satisfied.
tasks.withType<AndroidLintAnalysisTask>().configureEach { mustRunAfter(shadowJar) }

tasks.withType<LintModelWriterTask>().configureEach { mustRunAfter(shadowJar) }
