#!/bin/bash

# The website is built using MkDocs with the Material theme.
# https://squidfunk.github.io/mkdocs-material/
# It requires Python to run.
# Install the packages with the following command:
# python3 -m pip install -r .github/workflows/mkdocs-requirements.txt

if [[ "$1" = "--local" ]]; then
  local=true
elif [[ "$1" = "--ci" ]]; then
  ci=true
fi

if ! [[ ${local} || ${ci} ]]; then
  set -ex
  REPO="git@github.com:slackhq/compose-lints.git"
  DIR=temp-clone
  # Delete any existing temporary website clone
  rm -rf ${DIR}
  # Clone the current repo into temp folder
  git clone ${REPO} ${DIR}
  # Move working directory into temp folder
  cd ${DIR}
  # Generate the API docs
  ./gradlew dokkaHtml --no-configuration-cache
fi

# Copy in special files that GitHub wants in the project root.
cp CHANGELOG.md docs/changelog.md
cp .github/CONTRIBUTING.md docs/contributing.md
cp .github/CODE_OF_CONDUCT.md docs/code-of-conduct.md

# Build the site and push the new files up to GitHub
if [[ ${local} ]]; then
  # For local dev, just serve to localhost
  mkdocs serve
elif [[ ${ci} ]]; then
  # For CI we just build the site. It deploys using a GitHub Action
  mkdocs build -d site
else
  # Otherwise we deploy using mkdocs
  mkdocs gh-deploy
fi

# Delete our temp folder
if ! [[ ${local} || ${ci} ]]; then
  cd ..
  rm -rf ${DIR}
fi