#!/usr/bin/env just --justfile

fmt:
    cargo +nightly fmt --all -- --config format_code_in_doc_comments=true

check:
    cargo check --all
    cargo check --all-features

clippy:
    cargo clippy --all
    cargo clippy --all-features

test:
    cargo test --all
    cargo test --all-features

precommit: fmt check clippy test
