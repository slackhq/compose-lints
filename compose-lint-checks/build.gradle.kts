// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
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
}

dependencies {
  compileOnly(libs.lint.api)
  ksp(libs.autoService.ksp)
  implementation(libs.autoService.annotations)
  testImplementation(libs.bundles.lintTest)
  testImplementation(libs.junit)
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    // Lint forces Kotlin (regardless of what version the project uses), so this
    // forces a lower language level for now. Similar to `targetCompatibility` for Java.
    apiVersion = "1.7"
    languageVersion = "1.7"
  }
}
