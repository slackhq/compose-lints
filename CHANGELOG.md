Changelog
=========

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
