// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlin.jvm)
  // Run lint on the lints! https://groups.google.com/g/lint-dev/c/q_TVEe85dgc
  alias(libs.plugins.lint)
  alias(libs.plugins.ksp)
  alias(libs.plugins.mavenPublish)
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

dependencies {
  compileOnly(libs.lint.api)
  ksp(libs.autoService.ksp)
  implementation(libs.autoService.annotations)
  testImplementation(libs.bundles.lintTest)
  testImplementation(libs.junit)
}

val kgpKotlinVersion = KotlinVersion.KOTLIN_1_9

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    // Lint forces Kotlin (regardless of what version the project uses), so this
    // forces a matching language level for now. Similar to `targetCompatibility` for Java.
    // This should match the value in LintKotlinVersionCheckTest.kt
    apiVersion.set(kgpKotlinVersion)
    languageVersion.set(kgpKotlinVersion)
  }
}
