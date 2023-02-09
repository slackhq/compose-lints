// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
import com.diffplug.gradle.spotless.KotlinExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.spotless) apply false
  alias(libs.plugins.mavenPublish) apply false
  alias(libs.plugins.dokka) apply false
  alias(libs.plugins.detekt)
  alias(libs.plugins.lint) apply false
  alias(libs.plugins.ksp) apply false
}

val ktfmtVersion = libs.versions.ktfmt.get()

val pathPrefix = "compose-lint-checks/src/main/java/slack/lint/compose"
val utilPathPrefix = "$pathPrefix/util"
val testPathPrefix = "compose-lint-checks/src/test/java/slack/lint/compose"
val externalFiles =
  arrayOf(
    "$utilPathPrefix/ASTNodes.kt",
    "$utilPathPrefix/BooleanLintOption",
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
      target("**/*.kt")
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
        "(import|plugins|buildscript|dependencies|pluginManagement)"
      )
    }
  }
}

subprojects {
  pluginManager.withPlugin("java") {
    configure<JavaPluginExtension> { toolchain { languageVersion.set(JavaLanguageVersion.of(19)) } }

    tasks.withType<JavaCompile>().configureEach { options.release.set(11) }
  }

  pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    tasks.withType<KotlinCompile>().configureEach {
      kotlinOptions {
        jvmTarget = "11"
        // TODO re-enable once lint uses Kotlin 1.5
        //  allWarningsAsErrors = true
        //  freeCompilerArgs = freeCompilerArgs + listOf("-progressive")
      }
    }
  }

  tasks.withType<Detekt>().configureEach { jvmTarget = "11" }

  pluginManager.withPlugin("com.vanniktech.maven.publish") {
    apply(plugin = "org.jetbrains.dokka")

    tasks.withType<DokkaTask>().configureEach {
      outputDirectory.set(rootDir.resolve("docs/api/0.x"))
      dokkaSourceSets.configureEach { skipDeprecated.set(true) }
    }

    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(automaticRelease = true)
      signAllPublications()
    }
  }
}
