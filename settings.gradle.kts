// Copyright (C) 2023 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
import java.util.Locale

pluginManagement {
  repositories {
    mavenCentral()
    google()
    // Last because this proxies jcenter!
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  versionCatalogs {
    if (System.getenv("DEP_OVERRIDES") == "true") {
      val overrides = System.getenv().filterKeys { it.startsWith("DEP_OVERRIDE_") }
      maybeCreate("libs").apply {
        for ((key, value) in overrides) {
          val catalogKey = key.removePrefix("DEP_OVERRIDE_").lowercase(Locale.US)
          println("Overriding $catalogKey with $value")
          version(catalogKey, value)
        }
      }
    }
  }
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "compose-lints"

include(":compose-lint-checks")
