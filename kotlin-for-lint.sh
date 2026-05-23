#!/usr/bin/env bash
#
# kotlin-for-lint.sh — print the Kotlin version that a given Android Lint version forces.
#
# Android Lint (com.android.tools.lint:lint-api) bundles a repackaged Kotlin compiler that
# typically lags a few versions behind the latest Kotlin release. The repackaged artifact
# (com.android.tools.external.com-intellij:kotlin-compiler) is versioned with the *lint* version,
# so it isn't informative on its own. The real Kotlin version is the org.jetbrains.kotlin
# dependency declared alongside it in the lint-api POM (kotlin-reflect / kotlin-stdlib).
#
# This script fetches that POM from Google's Maven repo for a given lint version and prints the
# Kotlin version. The Kotlin version is emitted on stdout; everything else goes to stderr so the
# output stays scriptable.
#
# Usage:
#   ./kotlin-for-lint.sh <lint-version>     # e.g. ./kotlin-for-lint.sh 32.2.1  ->  2.2.10
#   ./kotlin-for-lint.sh                    # falls back to the `lint` version in
#                                           # gradle/libs.versions.toml, if present
#
# Note: the lint-api version tracks AGP as (AGP_MAJOR + 23).MINOR.PATCH, e.g. AGP 9.2.1 == lint
# 32.2.1. Pass the lint-api version (the 3x.y.z one), not the AGP/plugin version.

set -euo pipefail

lint_version="${1:-}"

# Convenience: if no version is given, try the version catalog next to this script.
if [[ -z "$lint_version" ]]; then
  catalog="$(dirname "$0")/gradle/libs.versions.toml"
  if [[ -f "$catalog" ]]; then
    lint_version="$(sed -nE 's/^lint[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$catalog" | head -1)"
    [[ -n "$lint_version" ]] && echo "No version given; using lint=$lint_version from $catalog" >&2
  fi
fi

if [[ -z "$lint_version" ]]; then
  echo "Usage: $0 <lint-version>   (e.g. 32.2.1)" >&2
  exit 2
fi

pom_url="https://dl.google.com/dl/android/maven2/com/android/tools/lint/lint-api/${lint_version}/lint-api-${lint_version}.pom"

if ! pom="$(curl -fsSL "$pom_url")"; then
  echo "error: could not fetch lint-api POM for lint ${lint_version}" >&2
  echo "       ${pom_url}" >&2
  exit 1
fi

# Pull the <version> from the org.jetbrains.kotlin dependency, preferring kotlin-reflect, then
# kotlin-stdlib-jdk8, then kotlin-stdlib. RS splits the POM into <dependency> blocks so we read
# each artifact's version from within its own block rather than relying on line ordering.
kotlin_version="$(
  printf '%s' "$pom" | awk '
    BEGIN { RS = "</dependency>" }
    /<groupId>org\.jetbrains\.kotlin<\/groupId>/ {
      if      ($0 ~ /<artifactId>kotlin-reflect<\/artifactId>/)      prio = 1
      else if ($0 ~ /<artifactId>kotlin-stdlib-jdk8<\/artifactId>/)  prio = 2
      else if ($0 ~ /<artifactId>kotlin-stdlib<\/artifactId>/)       prio = 3
      else next
      if (match($0, /<version>[^<]+<\/version>/)) {
        v = substr($0, RSTART + 9, RLENGTH - 19)
        if (best == "" || prio < bestprio) { best = v; bestprio = prio }
      }
    }
    END { if (best != "") print best }
  '
)"

if [[ -z "$kotlin_version" ]]; then
  echo "error: no org.jetbrains.kotlin dependency found in lint ${lint_version} POM" >&2
  echo "       (POM format may have changed: ${pom_url})" >&2
  exit 1
fi

echo "$kotlin_version"
