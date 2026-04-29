# Feature Specification: State, Storage, And Dependency Override

**Feature Branch**: `003-state-storage-override`  
**Created**: 2026-04-24  
**Status**: Draft  
**Input**: Let AI agents discover, read, mutate, snapshot, restore, and override Android app business state.

## User Scenarios & Testing

### User Story 1 - Mutate Typed Business State (Priority: P1)

As an AI agent, I want to set a typed state such as `user.isVip` so I can test branches without changing source code or manually editing app storage.

**Acceptance Scenarios**:

1. **Given** an app registers `user.isVip`, **When** the agent calls `state.set`, **Then** subsequent app reads observe the new value.
2. **Given** a snapshot exists, **When** the agent restores it, **Then** the state returns to the previous value.

### User Story 2 - Edit App Storage (Priority: P1)

As an AI agent, I want to query and modify SharedPreferences, Room/SQLite, and DataStore values to force persisted branches.

**Acceptance Scenarios**:

1. **Given** a Room database table, **When** the agent calls `storage.sql.exec`, **Then** the table is updated and the audit log includes the statement summary.
2. **Given** a DataStore Preferences key, **When** the agent sets it, **Then** the value is visible to app code.

### User Story 3 - Override Dependencies (Priority: P1)

As an AI agent, I want to override feature flags, clock, payment result, permission, and remote config values to cover hard-to-reach cases.

## Requirements

- **FR-001**: The runtime MUST provide manual APIs for registering typed state and actions.
- **FR-002**: State descriptors MUST include schema, mutability, reset behavior, description, and tags.
- **FR-003**: State tools MUST support list, get, set, reset, snapshot, restore, and diff.
- **FR-004**: Storage adapters MUST support SharedPreferences and Room/SQLite in MVP.
- **FR-005**: DataStore Preferences support SHOULD be included in MVP if dependency integration is straightforward.
- **FR-006**: DI overrides MUST support Hilt, Koin, or manual wrapper integration.
- **FR-007**: Mutations MUST emit audit events and restore tokens where possible.

## Key Entities

- **DebugStateDescriptor**: Public schema for an app state path.
- **StateSnapshot**: A named set of restorable values.
- **StorageAdapter**: Adapter for prefs, SQLite/Room, or DataStore.
- **OverrideEntry**: Session-scoped dependency override.

## Success Criteria

- **SC-001**: AI can discover and change `user.isVip`.
- **SC-002**: AI can snapshot, mutate, and restore SharedPreferences and a Room table.
- **SC-003**: AI can force a feature flag or clock value through an override store.
