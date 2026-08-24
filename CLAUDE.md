# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## Overview

This repo is a collection of independent open-source Ntrip tools/clients, organized one
subproject per top-level folder rather than a single unified codebase:

- `node/` — dependency-free Node.js Ntrip client + source-table tool, cross-platform
  (Windows/macOS/Linux). See `node/CLAUDE.md` for its architecture.
- `android/` — Android app connecting to an Ntrip caster and a BLE RTK receiver. Early stage: only
  the project scaffold + basic caster connection are implemented so far. See `android/readme.md`
  for the full planned feature set and `android/CLAUDE.md` for what's actually built.

There is no root-level build system, shared dependency graph, or cross-project code — each
subproject is self-contained with its own README and (where non-trivial) its own CLAUDE.md.

## Working in this repo

- Scope changes to the relevant subproject's folder. Don't introduce shared/root-level tooling
  (build scripts, lint configs, package.json) unless a project actually needs it — and then it
  belongs in that project's own folder, not the root.
- When starting work on a specific subproject, read its own CLAUDE.md (if present) for that
  project's conventions and architecture — this file only covers the repo layout.
- If you add a new subproject folder, give it its own README.md (usage) and, once there's real
  code to explain, its own CLAUDE.md (architecture) — then add a one-line pointer to it here.
