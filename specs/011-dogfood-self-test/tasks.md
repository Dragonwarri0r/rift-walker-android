# Tasks: Dogfood Self-Test Flow

## Phase 1: Spec

- [x] T001 Create spec, plan, contract, quickstart, and tasks docs.

## Phase 2: Daemon App Tools

- [x] T002 Add app/ADB protocol models.
- [x] T003 Register `device.list`, `adb.forward`, `app.forceStop`, and `app.launch`.
- [x] T004 Register app tools in MCP mode.

## Phase 3: Waitable Assertions

- [x] T005 Add `runtime.waitForPing`.
- [x] T006 Add timeout support to `network.assertCalled`.
- [x] T007 Add unit tests for waitable network assertion.

## Phase 4: Dogfood Scenario

- [x] T008 Add built-in sample dogfood scenario builder.
- [x] T009 Add `dogfood-sample` daemon CLI command.
- [x] T010 Add daemon unit tests for dogfood scenario shape.
- [x] T011 Add `scripts/spec011-dogfood.sh`.

## Phase 5: Validation

- [x] T012 Run daemon/runtime/sample/release verification commands.
- [x] T013 Run dogfood script on an available connected device.
