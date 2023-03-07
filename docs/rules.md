## State

### Hoist all the things

Compose is built upon the idea of a [unidirectional data flow](https://developer.android.com/jetpack/compose/state#state-hoisting), which can be summarised as: data/state flows down, and events fire up. To implement that, Compose advocates for the pattern of [hoisting state](https://developer.android.com/jetpack/compose/state#state-hoisting) upwards, enabling the majority of your composable functions to be stateless. This has many benefits, including far easier testing.

In practice, there are a few common things to look out for:

- Do not pass ViewModels (or objects from DI) down.
- Do not pass `State<Foo>` or `MutableState<Bar>` instances down.

Instead, pass down the relevant data to the function, and optional lambdas for callbacks.

More information: [State and Jetpack Compose](https://developer.android.com/jetpack/compose/state)

Related rule: [`ComposeViewModelForwarding`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/ViewModelForwardingDetector.kt)

### State should be remembered in composables

Be careful when using `mutableStateOf` (or any of the other state builders) to make sure that you `remember` the instance. If you don't `remember` the state instance, a new state instance will be created when the function is recomposed.

Related rule: [`ComposeRememberMissing`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/RememberMissingDetector.kt)

### Avoid using unstable collections

Collections are defined as interfaces (e.g. `List<T>`, `Map<T>`, `Set<T>`) in Kotlin, which can't guarantee that they are actually immutable. For example, you could write:

```kotlin
val list: List<String> = mutableListOf<String>()
```

The variable is constant, its declared type is not mutable but its implementation is still mutable. The Compose compiler cannot be sure of the immutability of this class as it just sees the declared type and as such declares it as unstable.

To force the compiler to see a collection as truly 'immutable' you have a couple of options.

You can use [Kotlinx Immutable Collections](https://github.com/Kotlin/kotlinx.collections.immutable):

```kotlin
val list: ImmutableList<String> = persistentListOf<String>()
```

Alternatively, you can wrap your collection in an annotated stable class to mark it as immutable for the Compose compiler.

```kotlin
@Immutable
data class StringList(val items: List<String>)
// ...
val list: StringList = StringList(yourList)
```
> **Note**: It is preferred to use Kotlinx Immutable Collections for this. As you can see, the wrapped case only includes the immutability promise with the annotation, but the underlying List is still mutable.
More info: [Jetpack Compose Stability Explained](https://medium.com/androiddevelopers/jetpack-compose-stability-explained-79c10db270c8), [Kotlinx Immutable Collections](https://github.com/Kotlin/kotlinx.collections.immutable)

Related rule: [`ComposeUnstableCollections`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/UnstableCollectionsDetector.kt)

## Composables

### Do not use inherently mutable types as parameters

This practice follows on from the 'Hoist all the things' item above, where we said that state flows down. It might be tempting to pass mutable state down to a function to mutate the value.

This is an anti-pattern though as it breaks the pattern of state flowing down, and events firing up. The mutation of the value is an event which should be modelled within the function API (a lambda callback).

There are a few reasons for this, but the main one is that it is very easy to use a mutable object which does not trigger recomposition. Without triggering recomposition, your composables will not automatically update to reflect the updated value.

Passing `ArrayList<T>`, `MutableState<T>`, `ViewModel` are common examples of this (but not limited to those types).

Related rule: [`ComposeMutableParameters`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/MutableParametersDetector.kt)

### Do not emit content and return a result

Composable functions should either emit layout content, or return a value, but not both.

If a composable should offer additional control surfaces to its caller, those control surfaces or callbacks should be provided as parameters to the composable function by the caller.

More info: [Compose API guidelines](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md#emit-xor-return-a-value)

Related rule: [`ComposeMultipleContentEmitters`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/MultipleContentEmittersDetector.kt)

!!! note "Configuration"
    To add your custom composables so they are used in this rule (things like your design system composables), you can configure a `content-emitters` option in `lint.xml`.

    ```xml
    <issue id="ComposeMultipleContentEmitters">
       <option name="allowed-composition-locals" value="CustomEmitter,AnotherEmitter" />
    </issue>
    ```

### Do not emit multiple pieces of content

A composable function should emit either 0 or 1 pieces of layout, but no more. A composable function should be cohesive, and not rely on what function it is called from.

You can see an example of what not to do below. `InnerContent()` emits a number of layout nodes and assumes that it will be called from a Column:

```kotlin
Column {
    InnerContent()
}
@Composable
private fun InnerContent() {
    Text(...)
    Image(...)
    Button(...)
}
```

However InnerContent could just as easily be called from a Row which would break all assumptions. Instead, InnerContent should be cohesive and emit a single layout node itself:

```kotlin
@Composable
private fun InnerContent() {
    Column {
        Text(...)
        Image(...)
        Button(...)
    }
}
```
Nesting of layouts has a drastically lower cost vs the view system, so developers should not try to minimize UI layers at the cost of correctness.

There is a slight exception to this rule, which is when the function is defined as an extension function of an appropriate scope, like so:
```kotlin
@Composable
private fun ColumnScope.InnerContent() {
    Text(...)
    Image(...)
    Button(...)
}
```
This effectively ties the function to be called from a Column, but is still not recommended (although permitted).

Related rule: [`ComposeMultipleContentEmitters`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/MultipleContentEmittersDetector.kt)

!!! note "Configuration"
    To add your custom composables so they are used in this rule (things like your design system composables), you can configure a `content-emitters` option in `lint.xml`.

    ```xml
    <issue id="ComposeMultipleContentEmitters">
       <option name="allowed-composition-locals" value="CustomEmitter,AnotherEmitter" />
    </issue>
    ```

### Naming multipreview annotations properly

Multipreview annotations should be named by using `Previews` as suffix (or `Preview` if just one). These annotations have to be explicitly named to make sure that they are clearly identifiable as a `@Preview` alternative on its usages.

More information: [Multipreview annotations](https://developer.android.com/jetpack/compose/tooling#preview-multipreview)

Related rule: [`ComposePreviewNaming`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/PreviewNamingDetector.kt)

### Naming @Composable functions properly

Composable functions that return `Unit` should start with an uppercase letter. They are considered declarative entities that can be either present or absent in a composition and therefore follow the naming rules for classes.

However, Composable functions that return a value should start with a lowercase letter instead. They should follow the standard [Kotlin Coding Conventions](https://kotlinlang.org/docs/reference/coding-conventions.html#function-names) for the naming of functions for any function annotated `@Composable` that returns a value other than `Unit`

More information: [Naming Unit @Composable functions as entities](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md#naming-unit-composable-functions-as-entities) and [Naming @Composable functions that return values](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md#naming-composable-functions-that-return-values)

Related rules: [`ComposeNamingUppercase`,`ComposeNamingLowercase`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/ComposableFunctionNamingDetector.kt)

!!! note "Configuration"
    To allow certain regex patterns of names, you can configure the `allowed-composable-function-names` option in `lint.xml`.
     
    ```xml
    <issue id="ComposeNamingUppercase,ComposeNamingLowercase">
       <option name="allowed-composable-function-names" value=".*Presenter" />
    </issue>
    ```

### Ordering @Composable parameters properly

When writing Kotlin, it's a good practice to write the parameters for your methods by putting the mandatory parameters first, followed by the optional ones (aka the ones with default values). By doing so, [we minimize the number times we will need to write the name for arguments explicitly](https://kotlinlang.org/docs/functions.html#default-arguments).

Modifiers occupy the first optional parameter slot to set a consistent expectation for developers that they can always provide a modifier as the final positional parameter to an element call for any given element's common case.

More information: [Kotlin default arguments](https://kotlinlang.org/docs/functions.html#default-arguments), [Modifier docs](https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier) and [Elements accept and respect a Modifier parameter](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md#why-8).

Related rule: [`ComposeParameterOrder`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/ParameterOrderDetector.kt)

### Make dependencies explicit

#### ViewModels

When designing composables, try to be explicit about the dependencies they take in. If you acquire a `ViewModel` or an instance from DI in the body of the composable, you are making this dependency implicit, which has the downsides of making it hard to test and harder to reuse.

To solve this problem, you should inject these dependencies as default values in the composable function.

Let's see it with an example.

```kotlin
@Composable
private fun MyComposable() {
    val viewModel = viewModel<MyViewModel>()
    // ...
}
```
In this composable, the dependencies are implicit. When testing it you would need to fake the internals of viewModel somehow to be able to acquire your intended ViewModel.

But, if you change it to pass these instances via the composable function parameters, you could provide the instance you want directly in your tests without any extra effort. It would also have the upside of the function being explicit about its external dependencies in its signature.

```kotlin
@Composable
private fun MyComposable(
    viewModel: MyViewModel = viewModel(),
) {
    // ...
}
```

Related rule: [`ComposeViewModelInjection`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/ViewModelInjectionDetector.kt)

#### `CompositionLocal`s

`CompositionLocal` makes a composable's behavior harder to reason about. As they create implicit dependencies, callers of composables that use them need to make sure that a value for every CompositionLocal is satisfied.

Although uncommon, there are [legit use cases](https://developer.android.com/jetpack/compose/compositionlocal#deciding) for them, so this rule provides an allowlist so that you can add your `CompositionLocal` names to it so that they are not flagged by the rule.

Related rule: [`ComposeCompositionLocalUsage`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/CompositionLocalUsageDetector.kt)

!!! note "Configuration"
    To add your custom `CompositionLocal` to your allowlist, you can configure a `allowed-composition-locals` option in `lint.xml`.

    ```xml
    <issue id="ComposeCompositionLocalUsage">
       <option name="allowed-composition-locals" value="LocalEnabled,LocalThing" />
    </issue>
    ```

### Preview composables should not be public

When a composable function exists solely because it's a `@Preview`, it doesn't need to have public visibility because it won't be used in actual UI. To prevent folks from using it unknowingly, we should restrict its visibility to `private`.

Related rule: [`ComposePreviewPublic`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/PreviewPublicDetector.kt)

> **Note**: If you are using Detekt, this may conflict with Detekt's [UnusedPrivateMember rule](https://detekt.dev/docs/rules/style/#unusedprivatemember).
Be sure to set Detekt's [ignoreAnnotated configuration](https://detekt.dev/docs/introduction/compose/#unusedprivatemember) to ['Preview'] for compatibility with this rule.

## Modifiers

### When should I expose modifier parameters?

Modifiers are the beating heart of Compose UI. They encapsulate the idea of composition over inheritance, by allowing developers to attach logic and behavior to layouts.

They are especially important for your public components, as they allow callers to customize the component to their wishes.

More info: [Always provide a Modifier parameter](https://chris.banes.dev/posts/always-provide-a-modifier/)

Related rule: [`ComposeModifierMissing`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/ModifierMissingDetector.kt)

### Don't re-use modifiers

Modifiers which are passed in are designed so that they should be used by a single layout node in the composable function. If the provided modifier is used by multiple composables at different levels, unwanted behaviour can happen.

In the following example we've exposed a public modifier parameter, and then passed it to the root Column, but we've also passed it to each of the descendant calls, with some extra modifiers on top:

```kotlin
@Composable
private fun InnerContent(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(modifier.clickable(), ...)
        Image(modifier.size(), ...)
        Button(modifier, ...)
    }
}
```
This is not recommended. Instead, the provided modifier should only be used on the Column. The descendant calls should use newly built modifiers, by using the empty Modifier object:

```kotlin
@Composable
private fun InnerContent(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(Modifier.clickable(), ...)
        Image(Modifier.size(), ...)
        Button(Modifier, ...)
    }
}
```

Related rule: [`ComposeModifierReused`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/ModifierReusedDetector.kt)

### Modifiers should have default parameters

Composables that accept a Modifier as a parameter to be applied to the whole component represented by the composable function should name the parameter modifier and assign the parameter a default value of `Modifier`. It should appear as the first optional parameter in the parameter list; after all required parameters (except for trailing lambda parameters) but before any other parameters with default values. Any default modifiers desired by a composable function should come after the modifier parameter's value in the composable function's implementation, keeping Modifier as the default parameter value.

More info: [Modifier documentation](https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier)

Related rule: [`ComposeModifierWithoutDefault`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/ModifierWithoutDefaultDetector.kt)

### Avoid Modifier extension factory functions

Using `@Composable` builder functions for modifiers is not recommended, as they cause unnecessary recompositions. To avoid this, you should use `Modifier.composed` instead, as it limits recomposition to just the modifier instance, rather than the whole function tree.

Composed modifiers may be created outside of composition, shared across elements, and declared as top-level constants, making them more flexible than modifiers that can only be created via a `@Composable` function call, and easier to avoid accidentally sharing state across elements.

More info: [Modifier extensions](https://developer.android.com/reference/kotlin/androidx/compose/ui/package-summary#extension-functions), [Composed modifiers in Jetpack Compose by Jorge Castillo](https://jorgecastillo.dev/composed-modifiers-in-jetpack-compose) and [Composed modifiers in API guidelines](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md#composed-modifiers)

Related rule: [`ComposeComposableModifier`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/ModifierComposableDetector.kt)

## Use Material 3

Rule: [`ComposeM2Api`](https://github.com/slackhq/compose-lints/blob/main/compose-lint-checks/src/main/java/slack/lint/compose/M2ApiDetector.kt)

Material 3 (M3) reached stable in October 2022. In apps that have migrated to M3, there may be `androidx.compose.material` (M2) APIs still remaining on the classpath from libraries or dependencies that can cause confusing imports due to the many similar or colliding Composable names in the two libraries. The `ComposeM2Api` rule can prevent these from being used.

!!! warning "Lint Configuration"
    This rule is set to `IGNORE` by default and is **opt-in**.
    
    You can make it an error via the `lint` DSL in Gradle:
    ```kotlin
    android {
      lint {
        error += "ComposeM2Api"
      }
    }
    ```
    Or in `lint.xml`:
    ```xml
    <lint>
      <issue id="ComposeM2Api" severity="error" />
    </lint>
    ```
    More lint configuration docs can be found [here](https://developer.android.com/studio/write/lint#gradle).

!!! note "Allow-list Configuration"
    To allow certain APIs (i.e. for incremental migration), you can configure a `allowed-m2-apis` option in `lint.xml`.
    ```xml
    <issue id="ComposeM2Api">
       <option name="allowed-m2-apis" value="Text,Surface" />
    </issue>
    ```

**Related docs links**

- Announcement post: https://material.io/blog/material-3-compose-stable
- Docs: https://m3.material.io/develop/android/jetpack-compose
- Migration guide: https://developer.android.com/jetpack/compose/themes/material2-material3
- Guidance: https://developer.android.com/jetpack/compose/themes/material3
- Reply (primary sample app): https://github.com/android/compose-samples/tree/main/Reply
- More samples: https://github.com/android/compose-samples

