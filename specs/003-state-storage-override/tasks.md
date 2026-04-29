# Tasks: State, Storage, And Dependency Override

## Phase 1: State Registry

- [x] T001 Define state descriptor, state value, snapshot, diff, and restore token models.
- [x] T002 Implement manual `AiDebug.state` registration API.
- [x] T003 Implement manual `AiDebug.action` registration API.
- [x] T004 Implement `state.list`, `state.get`, `state.set`, `state.reset`, `state.snapshot`, `state.restore`, and `state.diff`.

## Phase 2: Storage Adapters

- [x] T005 Implement SharedPreferences adapter.
- [x] T006 Implement SQLite/Room query and exec adapter.
- [x] T007 Implement snapshot/restore for prefs and SQLite/Room.
- [x] T008 Implement DataStore Preferences adapter if dependency constraints are acceptable.

## Phase 3: Dependency Override

- [x] T009 Implement override store with session scope and TTL.
- [x] T010 Add wrapper helpers for feature flags, clock, remote config, payment result, permission, and network status.
- [x] T011 Add Hilt helper module examples.
- [x] T012 Add Koin helper module examples.

## Phase 4: Validation

- [x] T013 Add sample app VIP state and feature flag branch.
- [x] T014 Add storage mutation tests for SharedPreferences and Room.
- [x] T015 Add snapshot/restore tests.
