Changelog
=========

**Unreleased**
--------------

- **Enhancement**: Better handle name shadowing in `SlotReused` lint and reduce false positives.
- Test against lint `31.8.0-alpha07`.
- Various doc fixes.
- Build against lint `31.7.1`.
- Build against Kotlin `2.0.21`. Still targeting Kotlin 1.9 language version (lint 31.7.x's language version).

Special thanks to [@SimonMarquis](https://github.com/SimonMarquis) and [@AlexVanyo](https://github.com/AlexVanyo) for contributing to this release!

1.4.1
-----

_2024-10-02_

- **Fix**: Fix false positives reported by `ComposeContentEmitterReturningValues`.
- **Fix**: Fix `content-emitters` configuration in docs.
- **Fix**: Fix link to multipreview annotations in docs.

Special thanks to [@erikghonyan](https://github.com/erikghonyan) for contributing to this release!

1.4.0
-----

_2024-10-01_

- **New**: Implement `SlotReused` lint.  See https://slackhq.github.io/compose-lints/rules/#do-not-invoke-slots-in-more-than-once-place for more information.
- **Enhancement**: Report the function name for readability in `ComposeContentEmitterReturningValues`.
- **Enhancement**: Check for inherited `@Preview` annotations up to four levels.
- **Enhancement**: Allow `@VisibleForTesting`/`@TestOnly`-annotated preview composables to be public.
- **Fix**: Don't report duplicate errors about multiple content emitters.
- **Fix**: Normalize lint option loading to match with individual issues.
- **Fix**: Use name of parameter if text is not available.
- **Removed**: Delete obsolete `ComposeComposableModifier` lint check.
- Various docs fixes.
- Build against Lint `8.7.0`.
- Update `api` and `minApi` to `16` (i.e. lint 8.7.0+). It's possible this may work with API 15 but we have not tested it.
- Test against Lint `8.8.0-alpha04`.
- Test against K2 UAST.
- Build against Kotlin `2.0.20`.

Special thanks to [@alexvanyo](https://github.com/alexvanyo), [@seve-andre](https://github.com/seve-andre), [@svenjacobs](https://github.com/svenjacobs), [@ychescale9](https://github.com/ychescale9), [@shahzadansari](https://github.com/shahzadansari), and [@kozaxinan](https://github.com/kozaxinan) for contributing to this release!

1.3.1
-----

_2024-01-25_

- Lower the lint API back to `14`, not `15`.

1.3.0
-----

_2024-01-25_

- **New**: Implement `ModifierComposed` check to lint against use of `Modifier.composed`, which is no longer recommended in favor of the new `Modifier.Node` API.
- **New**: Implement `ComposeUnstableReceiver` check to warn when composable extension functions or composables instance functions have unstable receivers/containing classes.
- **New**: Check for property accessors with composition locals.
- **Enhancement**: The `ComposeComposableModifier` message now recommends the new `Modifier.Node` API.
- **Enhancement**: Make lints **significantly** more robust to edge cases like typealiases, import aliases, parentheses, fully-qualified references, and whitespace. Our tests now cover all these cases.
- **Enhancement**: Update `@Preview` detection to also detect Compose Desktop's own `@Preview` annotation.
- **Enhancement**: Improve the `ComposeParameterOrder` check to only lint the parameter list and add a quickfix.
- **Enhancement**: Add support for checking for loops in multiple content emitters.
- **Fix**: Fix allowed names config for Unit-returning functions.
- **Fix**: Ignore context receivers in multiple content emissions lint.
- **Fix**: Allow nullable types for trailing lambdas in `ComposeParameterOrder`.
- **Fix**: Best-effort work around name mangling when comparing name in M2ApiDetector's allow list.
- **Fix**: Fix `ComposePreviewPublic` to always just require private, remove preview parameter configuration.
- **Docs**: Improve docs for `ComposeContentEmitterReturningValues`
- Build against lint-api `31.2.2`.
- Test against lint-api `31.4.0-alpha06`.
- Raise Kotlin apiVersion/languageVersion to `1.9.0`.

Special thanks to [@jzbrooks](https://github.com/jzbrooks), [@joeMalebe](https://github.com/joeMalebe), [@dellisd](https://github.com/dellisd) for contributing to this release!

1.2.0
-----

_2023-04-19_

- **Fix**: Only run `ComposeM2Api` checks on Kotlin files.
- Update lint current and min API to 14, aka AGP 8.0.0+.

1.1.1
-----

_2023-03-08_

* **Fix**: Use `setEnabledByDefault(false)` instead of `IGNORE` in `ComposeM2Api`. This is what we intended before, too, but didn't realize there was a dedicated API for it. Note that this changes configuration slightly as you must now explicitly enable the rule too and not just the severity. See the docs: https://slackhq.github.io/compose-lints/rules/#use-material-3.

1.1.0
-----

_2023-03-07_

* **New**: Add `ComposeM2Api` rule. This rule can be used to lint against using "Material 2" (`androidx.compose.material`) APIs in codebases that have migrated to Material 3 (M3). This rule is disabled by default, see the docs for more information: https://slackhq.github.io/compose-lints/rules/#use-material-3.
* **Enhancement**: Add `viewmodel-factories` lint option to `ComposeViewModelInjection`. This allows you to define your own known ViewModel factories. Thanks to [@WhosNickDoglio](https://github.com/WhosNickDoglio) for contributing this!
* Build against lint-api to `30.4.2`.
* Test against lint `31.1.0-alpha08`.

1.0.1
-----

_2023-02-15_

### Changes

* Add installation instructions to index.md by @ZacSweers in https://github.com/slackhq/compose-lints/pull/44
* Fix possible typo in README by @WhosNickDoglio in https://github.com/slackhq/compose-lints/pull/45
* Hopefully fix publish-docs actions by @chrisbanes in https://github.com/slackhq/compose-lints/pull/47
* Update lint-latest to v31.1.0-alpha04 by @slack-oss-bot in https://github.com/slackhq/compose-lints/pull/51
* Update dependency mkdocs-material to v9.0.12 by @slack-oss-bot in https://github.com/slackhq/compose-lints/pull/50
* Downgrade ComposeCompositionLocalUsage to warning by @chrisbanes in https://github.com/slackhq/compose-lints/pull/52
* Misc mutable parameter fixes by @ZacSweers in https://github.com/slackhq/compose-lints/pull/49
* Update plugin spotless to v6.15.0 by @slack-oss-bot in https://github.com/slackhq/compose-lints/pull/54
* Update dependency gradle to v8 by @slack-oss-bot in https://github.com/slackhq/compose-lints/pull/55
* Update Lint baseline by @chrisbanes in https://github.com/slackhq/compose-lints/pull/58

### New Contributors
* @WhosNickDoglio made their first contribution in https://github.com/slackhq/compose-lints/pull/45

**Full Changelog**: https://github.com/slackhq/compose-lints/compare/1.0.0...1.0.1

1.0.0
-----

_2023-02-09_

Initial release!

This is a near-full port of the original rule set to lint. It should be mostly at parity with the original rules as well.

The lints target lint-api `30.4.0`/lint API `13` and target Java 11.

See the docs for full usage and information: https://slackhq.github.io/compose-lints.

**Notes**
- `ComposeViewModelInjection` does not offer a quickfix yet. PRs welcome!
- `ComposeUnstableCollections` is a warning by default rather than an error.
- `CompositionLocalNaming` is not ported because this is offered in compose's bundled lint rules now.
