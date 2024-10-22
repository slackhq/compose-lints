// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.LintModelWriterTask
import com.diffplug.gradle.spotless.KotlinExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import com.google.devtools.ksp.gradle.KspTask
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.spotless) apply false
  alias(libs.plugins.mavenPublish) apply false
  alias(libs.plugins.dokka)
  alias(libs.plugins.detekt)
  alias(libs.plugins.lint) apply false
  alias(libs.plugins.ksp) apply false
}

val ktfmtVersion = libs.versions.ktfmt.get()

val pathPrefix = "src/main/java/slack/lint/compose"
val utilPathPrefix = "$pathPrefix/util"
val testPathPrefix = "src/test/java/slack/lint/compose"
val externalFiles =
  arrayOf(
    "$utilPathPrefix/ASTNodes.kt",
    "$utilPathPrefix/Composables.kt",
    "$utilPathPrefix/KotlinUtils.kt",
    "$utilPathPrefix/KtAnnotateds.kt",
    "$utilPathPrefix/KtCallableDeclarations.kt",
    "$utilPathPrefix/KtFunctions.kt",
    "$utilPathPrefix/Previews.kt",
    "$utilPathPrefix/Priorities",
    "$utilPathPrefix/PsiElements.kt",
    "$pathPrefix/ComposableFunctionDetector.kt",
    "$pathPrefix/ComposableFunctionNamingDetector.kt",
    "$pathPrefix/CompositionLocalUsageDetector.kt",
    "$pathPrefix/ContentEmitterReturningValuesDetector.kt",
    "$pathPrefix/ModifierComposableDetector.kt",
    "$pathPrefix/ModifierMissingDetector.kt",
    "$pathPrefix/ModifierReusedDetector.kt",
    "$pathPrefix/ModifierWithoutDefaultDetector.kt",
    "$pathPrefix/MultipleContentEmittersDetector.kt",
    "$pathPrefix/MutableParametersDetector.kt",
    "$pathPrefix/ParameterOrderDetector.kt",
    "$pathPrefix/PreviewNamingDetector.kt",
    "$pathPrefix/PreviewPublicDetector.kt",
    "$pathPrefix/RememberMissingDetector.kt",
    "$pathPrefix/UnstableCollectionsDetector.kt",
    "$pathPrefix/ViewModelForwardingDetector.kt",
    "$pathPrefix/ViewModelInjectionDetector.kt",
    "$testPathPrefix/ComposableFunctionNamingDetectorTest.kt",
    "$testPathPrefix/CompositionLocalUsageDetectorTest.kt",
    "$testPathPrefix/ContentEmitterReturningValuesDetectorTest.kt",
    "$testPathPrefix/ModifierComposableDetectorTest.kt",
    "$testPathPrefix/ModifierMissingDetectorTest.kt",
    "$testPathPrefix/ModifierReusedDetectorTest.kt",
    "$testPathPrefix/ModifierWithoutDefaultDetectorTest.kt",
    "$testPathPrefix/MultipleContentEmittersDetectorTest.kt",
    "$testPathPrefix/MutableParametersDetectorTest.kt",
    "$testPathPrefix/ParameterOrderDetectorTest.kt",
    "$testPathPrefix/PreviewNamingDetectorTest.kt",
    "$testPathPrefix/PreviewPublicDetectorTest.kt",
    "$testPathPrefix/RememberMissingDetectorTest.kt",
    "$testPathPrefix/UnstableCollectionsDetectorTest.kt",
    "$testPathPrefix/ViewModelForwardingDetectorTest.kt",
    "$testPathPrefix/ViewModelInjectionDetectorTest.kt",
  )

allprojects {
  apply(plugin = "com.diffplug.spotless")
  configure<SpotlessExtension> {
    format("misc") {
      target("*.md", ".gitignore")
      trimTrailingWhitespace()
      endWithNewline()
    }
    kotlin {
      target("src/**/*.kt")
      ktfmt(ktfmtVersion).googleStyle()
      trimTrailingWhitespace()
      endWithNewline()
      licenseHeaderFile(rootProject.file("spotless/spotless.kt"))
      targetExclude("spotless/*.kt", "**/spotless/*.kt", *externalFiles)
    }
    // Externally adapted sources that should preserve their license header
    format("kotlinExternal", KotlinExtension::class.java) {
      target(*externalFiles)
      ktfmt(ktfmtVersion).googleStyle()
      trimTrailingWhitespace()
      endWithNewline()
      licenseHeaderFile(rootProject.file("spotless/spotless-external.kt"))
    }
    kotlinGradle {
      ktfmt(ktfmtVersion).googleStyle()
      trimTrailingWhitespace()
      endWithNewline()
      licenseHeaderFile(
        rootProject.file("spotless/spotless.kt"),
        "(import|plugins|buildscript|dependencies|pluginManagement)",
      )
    }
  }
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
      dokkaPublicationDirectory.set(layout.buildDirectory.dir("dokkaDir"))
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
      mustRunAfter(tasks.withType<KspTask>())
    }
    tasks.withType<LintModelWriterTask>().configureEach { mustRunAfter(tasks.withType<KspTask>()) }
  }
}
