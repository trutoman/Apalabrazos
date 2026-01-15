#!/bin/bash
set -e

# Setup Intel JDK 11 and Maven x86_64
export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:/usr/local/bin:$PATH"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔨 Building dependencies with Maven..."
arch -x86_64 /usr/local/bin/mvn -q -DskipTests -Dmdep.outputFile=target/classpath.txt dependency:build-classpath

echo "🔨 Compiling sources..."
arch -x86_64 /usr/local/bin/mvn -q -DskipTests clean compile

echo "🚀 Running app with JavaFX 17 (x86_64)..."
FXLIB="$HOME/.javafx/17.0.10/sdk/lib"
CPFILE="target/classpath.txt"
CLASSES="target/classes"

if [ ! -d "$FXLIB" ]; then
  echo "📦 Downloading JavaFX 17 SDK..."
  mkdir -p "$HOME/.javafx/17.0.10"
  cd "$HOME/.javafx/17.0.10"
  curl -fL -o openjfx-17.0.10_osx-x64_bin-sdk.zip https://download2.gluonhq.com/openjfx/17.0.10/openjfx-17.0.10_osx-x64_bin-sdk.zip
  unzip -q openjfx-17.0.10_osx-x64_bin-sdk.zip
  mv javafx-sdk-17.0.10 sdk
  cd "$SCRIPT_DIR"
fi

arch -x86_64 java --module-path "$FXLIB" --add-modules javafx.controls,javafx.fxml -cp "$CLASSES:$(cat $CPFILE)" UE_Proyecto_Ingenieria.Apalabrazos.MainApp
