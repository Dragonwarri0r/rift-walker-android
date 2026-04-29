# Implementation Plan: State, Storage, And Dependency Override

**Branch**: `003-state-storage-override`  
**Date**: 2026-04-24  
**Spec**: `specs/003-state-storage-override/spec.md`

## Summary

Build the semantic control layer that lets AI agents change business state and persisted state in a reversible way. This feature should come immediately after network control because it creates differentiation beyond API mocking.

## Technical Context

| Area | Decision |
| --- | --- |
| API style | Manual registration first, annotations/codegen later |
| Snapshot | In-memory plus optional file export |
| Storage | SharedPreferences, SQLite/Room, DataStore Preferences |
| DI | Manual wrapper API first; Hilt/Koin helpers next |
| Testing | Runtime unit tests, sample app branch tests |

## Implementation Notes

- Keep typed state API small and ergonomic so AI-generated app code can add capabilities quickly.
- Distinguish state values from actions. Some branches are better represented as actions like `auth.expireToken`.
- Storage mutation should support dry-run or diff preview where feasible.
- DI overrides should be session-scoped and cleared on cleanup.

## Risks

- Room invalidation may not fire for all raw SQL mutations unless updates go through expected database paths.
- Proto DataStore needs app-provided serializers; do not fake generic support.
- Overriding business state can violate invariants; descriptors should include warnings and reset behavior.
