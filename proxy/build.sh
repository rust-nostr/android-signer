#!/bin/bash

set -exuo pipefail

# Check if ANDROID_NDK_HOME env is set
if [ ! -d "${ANDROID_NDK_HOME}" ] ; then \
  echo "Error: Please, set the ANDROID_NDK_HOME env variable to point to your NDK folder" ; \
  exit 1 ; \
fi

# Check if ANDROID_SDK_ROOT env is set
if [ ! -d "${ANDROID_SDK_ROOT}" ] ; then \
  echo "Error: Please, set the ANDROID_SDK_ROOT env variable to point to your SDK folder" ; \
  exit 1 ; \
fi

# Install deps
cargo ndk --version || cargo install cargo-ndk

# Build targets
cargo ndk --platform 21 -t aarch64-linux-android -t armv7-linux-androideabi -t x86_64-linux-android -t i686-linux-android build -p nostr_android_signer_proxy --release
