#!/usr/bin/env bash

# Target project to update, required
TARGET=$1
# Version to increment to, optional. If blank or not specified, will auto-increment.
REQUESTED_VERSION=$2
source .github/workflows/scriptUtil.sh
# Parse the current version, strip leading zeros, increment
CURRENT_VERSION=$(getProperty 'VERSION_NAME' "$TARGET"/gradle.properties)

# Export the coordinates while we're at it for later use in publish.yml
# Group is always in our root dir
GROUP=$(getProperty 'GROUP' gradle.properties)
ARTIFACT=$(getProperty 'POM_ARTIFACT_ID' "$TARGET"/gradle.properties)

STRIPPED_CURRENT=$(echo "$CURRENT_VERSION" | sed 's/^0*//')
if [[ "$REQUESTED_VERSION" != "" ]]; then
  NEW_VERSION=$REQUESTED_VERSION
else
  ((STRIPPED_CURRENT++))
  NEW_VERSION=$(printf "%05d\n" $STRIPPED_CURRENT)
fi
sed -i -e "s/${CURRENT_VERSION}/${NEW_VERSION}/g" "$TARGET"/gradle.properties
echo "current: $CURRENT_VERSION"
echo "CURRENT_VERSION=$CURRENT_VERSION" >> $GITHUB_ENV
echo "new: $NEW_VERSION"
echo "NEW_VERSION=$NEW_VERSION" >> $GITHUB_ENV
# We just use the artifact ID in the android repo for the coordinate
echo "COORDINATES=$ARTIFACT" >> $GITHUB_ENV
