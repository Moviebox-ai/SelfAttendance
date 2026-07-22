#!/bin/bash
# Run this script from the root of your local Git repository checkout
# (the folder that contains app/, gradle/, etc.)
# It removes the two original files that were split into flavor source sets.

set -e

echo "Removing stale src/main files that are now in play/amazon source sets..."

git rm --cached --ignore-unmatch \
  app/src/main/java/com/aaryo/selfattendance/update/InAppUpdateManager.kt \
  app/src/main/java/com/aaryo/selfattendance/security/IntegrityCheck.kt

rm -f \
  app/src/main/java/com/aaryo/selfattendance/update/InAppUpdateManager.kt \
  app/src/main/java/com/aaryo/selfattendance/security/IntegrityCheck.kt

echo "Done. Now copy all files from the ZIP into this folder, then:"
echo "  git add -A && git commit -m 'Amazon Appstore: flavor split + stability'"
echo "  git push"
