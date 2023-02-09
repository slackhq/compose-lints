Changelog
=========

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
