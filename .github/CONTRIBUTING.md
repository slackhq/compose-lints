# Contributors Guide

## Development

Check out this repo with Android Studio or IntelliJ. It's a standard gradle project and
conventional to check out.

The primary project is `compose-lint-checks`.

Kotlin should be used for more idiomatic use with lint APIs.

## Formatting

This project uses [Kempt](https://github.com/ZacSweers/kempt) for Kotlin formatting, whitespace cleanup, and license headers. After checking out the repository, run `kempt install-hook` to install the pre-commit hook for your local checkout.

Run `kempt format` to format the repository and `kempt check` to verify formatting without changing files.

Kempt applies the standard Salesforce license header from `config/license-header.txt`. Kotlin files adapted from external projects that need to preserve their existing headers must be listed in `config/license-excludes-kt.txt`.

## Lint Documentation

[The Android Lint API Guide](https://googlesamples.github.io/android-custom-lint-rules/book.html) provides an excellent overview of lint's purpose, how it works, and how to author custom checks.

## Lint Guidelines
- Limited scopes. Remember this will run in a slow build step or during the IDE, performance matters!
    - If your check only matters for java or kotlin, only run on appropriate files
    - Use the smallest necessary scope. Avoid tree walking through the AST if it can be avoided, there
      are usually more appropriate hooks.
- Use `UElementHandler` (via overriding `createUastHandler()`) rather than overriding `Detector`
  callback methods. `Detector` callback methods tend only to be useful for tricky scenarios, like
  annotated elements. For basic `UElement` types it's best to just use `UElementHandler` as it affords
  a standard API and is easy to conditionally avoid nested parsing.
- For testing, prefer writing source stubs directly in the test rather than extract individual files
  in `resources` for stubs. Stubs in resources add friction for source glancing and tedious to
  maintain, and should only be used for extremely complex source files.
- Use our `implementation<*Detector>()` helper functions for wiring your `Issue` information. This
  is important because it will help ensure your check works in both command line and in the IDE.
