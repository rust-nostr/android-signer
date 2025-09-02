#!/usr/bin/env just --justfile

precommit:
  cd signer && just precommit

proto:
    protoc --proto_path=signer/src/proto --java_out=proxy/lib/src/main/java --kotlin_out=proxy/lib/src/main/java signer/src/proto/android_signer.proto
