#!/bin/sh

# Xcode Cloud runs this script automatically right after cloning the repository.
# This project is Kotlin Multiplatform: the iOS build phase invokes Gradle
# (./gradlew :shared:embedAndSignAppleFrameworkForXcode), which requires a JDK.
# Xcode Cloud runners do NOT ship a JDK, so we install one here.
#
# IMPORTANT: After this script installs the JDK, you must declare JAVA_HOME as an
# environment variable in the Xcode Cloud workflow (Build > Environment) with the
# value printed at the end of this script. Variables exported here do not carry
# over to the Gradle build phase; workflow environment variables do.

set -e

JDK_FEATURE_VERSION=21
# Install inside DerivedData: it is the only writable workspace location that
# survives from the post-clone step into the xcodebuild/Gradle build phase.
# A bare /Volumes/workspace path is wiped between steps and Gradle would then
# report "JAVA_HOME is set to an invalid directory".
INSTALL_DIR="${CI_DERIVED_DATA_PATH:-/Volumes/workspace/DerivedData}/JDK"

case "$(uname -m)" in
  arm64) JDK_ARCH="aarch64" ;;
  x86_64) JDK_ARCH="x64" ;;
  *) echo "Unsupported architecture: $(uname -m)"; exit 1 ;;
esac

echo "Installing Temurin JDK ${JDK_FEATURE_VERSION} (${JDK_ARCH})..."
mkdir -p "$INSTALL_DIR"

# Adoptium API redirects to the latest GA build for the given feature version,
# so we never pin a patch URL that can disappear.
DOWNLOAD_URL="https://api.adoptium.net/v3/binary/latest/${JDK_FEATURE_VERSION}/ga/mac/${JDK_ARCH}/jdk/hotspot/normal/eclipse"

curl -fsSL "$DOWNLOAD_URL" -o /tmp/jdk.tar.gz
# Strip the top-level jdk-XX.Y.Z+B folder so $INSTALL_DIR contains Contents/Home directly.
tar -xzf /tmp/jdk.tar.gz -C "$INSTALL_DIR" --strip-components=1
rm -f /tmp/jdk.tar.gz

JAVA_HOME_PATH="${INSTALL_DIR}/Contents/Home"
echo "JDK installed."
"${JAVA_HOME_PATH}/bin/java" -version

echo "-----------------------------------------------------------------"
echo "Set this environment variable in the Xcode Cloud workflow:"
echo "  JAVA_HOME = ${JAVA_HOME_PATH}"
echo "-----------------------------------------------------------------"
