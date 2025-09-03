#!/usr/bin/env just --justfile

set windows-shell := ["powershell.exe", "-NoLogo", "-Command"]

[private]
default:
    @just --list

# Execute pre-commit tasks
precommit:
  cd signer && just precommit

# Regenerate protobuf for the Android Proxy
proto:
    protoc --proto_path=signer/src/proto --java_out=proxy/lib/src/main/java --kotlin_out=proxy/lib/src/main/java signer/src/proto/android_signer.proto
