# Tasks: Media Input Foundation

Task ordering is intentionally foundation-first. Do not start `013` or `014` implementation work until T003-T014 are stable enough for audio/camera specs to reuse the same target and fixture model.

## Phase 1: Spec

- [x] T001 Create spec, plan, contract, quickstart, and tasks docs.
- [x] T002 Update specs index with `012-media-input-foundation`.

## Phase 2: Protocol

- [x] T003 Add `MediaTarget`, `MediaFixture`, media capabilities, and shared history/assertion protocol models.
- [x] T004 Add fixture register/list/delete request and response models.
- [ ] T005 Add media release-safety report fields.
- [x] T006 Add protocol serialization tests for media foundation models.

## Phase 3: Runtime Foundation

- [x] T007 Add `MediaTargetRegistry` with stable `targetId` and `callSiteId` registration.
- [x] T008 Add `MediaFixtureStore` with sha256, device path, MIME, size, metadata, and cleanup support.
- [x] T009 Add `MediaController` and built-in media capability descriptors.
- [x] T010 Add shared media bridge registration APIs used by audio and camera bridges.
- [x] T011 Add runtime HTTP routes for `media.capabilities`, `media.targets.list`, and fixture tools.

## Phase 4: Daemon MCP And Fixture Staging

- [x] T012 Add daemon fixture staging with host-path input, sha256 calculation, and `adb push`.
- [x] T013 Register MCP tools for `media.capabilities`, `media.targets.list`, `media.fixture.register`, `media.fixture.list`, and `media.fixture.delete`.
- [ ] T014 Add daemon unit tests for fixture staging and media foundation tool registration.

## Phase 5: Gradle DSL And Release Safety

- [x] T015 Add `mediaInputControl` Gradle DSL root.
- [x] T016 Reserve nested DSL sections for audio and camera specs without enabling rewriting yet.
- [x] T017 Extend release safety defaults to forbid media foundation runtime classes and staged fixture logic.
- [ ] T018 Add negative release leak coverage for media foundation classes.

## Phase 6: Validation

- [x] T019 Run protocol, daemon, runtime, sample, and release checks.
- [ ] T020 Add and run `scripts/spec012-media-foundation-smoke.sh`.
