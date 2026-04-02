#!/usr/bin/env bash
set -e

VERSION=$(sed -n 's/(def version "\(.*\)")/\1/p' build.clj)

echo "Building uberjar..."
clojure -T:build uber

echo "Running jpackage..."
jpackage \
  --input target \
  --main-jar kniziathon.jar \
  --name Kniziathon \
  --app-version "$VERSION" \
  --description "Kniziathon gaming event score tracker" \
  --vendor "Kniziathon"

echo "Done. Installer created in current directory."
