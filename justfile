#!/usr/bin/env just --justfile

set windows-shell := ["powershell.exe", "-NoLogo", "-Command"]

[private]
default:
    @just --list

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

# Execute pre-commit tasks
precommit: fmt check clippy test
