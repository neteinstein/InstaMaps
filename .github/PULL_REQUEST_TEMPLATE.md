## Description

<!--
What does this PR change and why? Link the module(s) touched (e.g. `feature:videoprocessing`,
`core:designsystem`) and any related issue. Prefer explaining the reasoning/trade-offs over
listing every file changed - reviewers can see the diff.
-->

## How to Test

<!--
Exact commands/steps a reviewer can run to verify this change. Be specific about which module(s)
to target so reviewers don't have to guess.

Example:
- `./gradlew :feature:geocoding:test`
- `./gradlew ktlintCheck`
- Manual: share an Instagram reel URL to the app, confirm the Maps deep link opens with the
  expected place.
-->

## Checklist

- [ ] `./gradlew ktlintCheck` passes (or `ktlintFormat` was run to fix style)
- [ ] `./gradlew test` passes for all affected modules
- [ ] New/changed logic has unit test coverage (domain and data layers especially)
- [ ] `agents.md` / `README.md` updated if this changes architecture, setup, or dev workflow
- [ ] No secrets, API keys, or credentials committed
