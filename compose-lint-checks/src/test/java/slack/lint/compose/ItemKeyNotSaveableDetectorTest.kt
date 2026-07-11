// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test

class ItemKeyNotSaveableDetectorTest : BaseComposeLintTest() {
  private val lazyStub =
    kotlin(
      """
      package androidx.compose.foundation.lazy

      import androidx.compose.runtime.Composable

      interface LazyItemScope

      interface LazyListScope {
        fun item(key: Any? = null, content: @Composable LazyItemScope.() -> Unit)
        fun items(
          count: Int,
          key: ((index: Int) -> Any)? = null,
          itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
        )
      }

      fun <T> LazyListScope.items(
        items: List<T>,
        key: ((item: T) -> Any)? = null,
        itemContent: @Composable LazyItemScope.(item: T) -> Unit,
      ) {}

      fun <T> LazyListScope.itemsIndexed(
        items: List<T>,
        key: ((index: Int, item: T) -> Any)? = null,
        itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
      ) {}

      @Composable fun LazyColumn(content: LazyListScope.() -> Unit) {}
      """
        .trimIndent()
    )

  private val pagerStub =
    kotlin(
      """
      package androidx.compose.foundation.pager

      import androidx.compose.runtime.Composable

      interface PagerScope

      @Composable
      fun HorizontalPager(
        pageCount: Int,
        key: ((index: Int) -> Any)? = null,
        pageContent: @Composable PagerScope.(page: Int) -> Unit,
      ) {}
      """
        .trimIndent()
    )

  private val gridStub =
    kotlin(
      """
      package androidx.compose.foundation.lazy.grid

      import androidx.compose.runtime.Composable

      interface LazyGridItemScope

      interface LazyGridScope {
        fun item(key: Any? = null, content: @Composable LazyGridItemScope.() -> Unit)
      }

      fun <T> LazyGridScope.items(
        items: List<T>,
        key: ((item: T) -> Any)? = null,
        itemContent: @Composable LazyGridItemScope.(item: T) -> Unit,
      ) {}

      @Composable fun LazyVerticalGrid(content: LazyGridScope.() -> Unit) {}
      """
        .trimIndent()
    )

  private val staggeredGridStub =
    kotlin(
      """
      package androidx.compose.foundation.lazy.staggeredgrid

      import androidx.compose.runtime.Composable

      interface LazyStaggeredGridItemScope

      interface LazyStaggeredGridScope {
        fun item(key: Any? = null, content: @Composable LazyStaggeredGridItemScope.() -> Unit)
      }

      fun <T> LazyStaggeredGridScope.items(
        items: List<T>,
        key: ((item: T) -> Any)? = null,
        itemContent: @Composable LazyStaggeredGridItemScope.(item: T) -> Unit,
      ) {}

      @Composable fun LazyVerticalStaggeredGrid(content: LazyStaggeredGridScope.() -> Unit) {}
      """
        .trimIndent()
    )

  private val parcelableStub =
    kotlin(
      """
      package android.os

      interface Parcelable
      """
        .trimIndent()
    )

  override fun getDetector(): Detector = ItemKeyNotSaveableDetector()

  override fun getIssues(): List<Issue> = listOf(ItemKeyNotSaveableDetector.ISSUE)

  @Test
  fun `errors when key type is not saveable`() {
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.lazy.LazyColumn
      import androidx.compose.foundation.lazy.items

      class NotSaveable(val int: Int)

      @Composable
      fun Content(list: List<NotSaveable>) {
        LazyColumn {
          items(list, key = { it }) {}
          items(list, key = { NotSaveable(it.int) }) {}
          item(key = NotSaveable(0)) {}
        }
      }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, lazyStub, kotlin(code))
      .run()
      .expect(
        """
        src/NotSaveable.kt:10: Warning: Item keys in Lazy* layouts can be persisted across configuration changes and process death, so on Android the type of the key should be saveable via Bundle: a primitive, String/CharSequence, Serializable, or Parcelable. A key whose type is none of these can crash at runtime when the key is saved and restored (e.g. when item state is stored via rememberSaveable).

        Use a saveable identifier instead (e.g. a String or Int id), or make the key type Parcelable/Serializable.

        See https://slackhq.github.io/compose-lints/rules/#item-key-types-must-be-saveable for more information. [ComposeItemKeyNotSaveable]
            items(list, key = { it }) {}
                                ~~
        src/NotSaveable.kt:11: Warning: Item keys in Lazy* layouts can be persisted across configuration changes and process death, so on Android the type of the key should be saveable via Bundle: a primitive, String/CharSequence, Serializable, or Parcelable. A key whose type is none of these can crash at runtime when the key is saved and restored (e.g. when item state is stored via rememberSaveable).

        Use a saveable identifier instead (e.g. a String or Int id), or make the key type Parcelable/Serializable.

        See https://slackhq.github.io/compose-lints/rules/#item-key-types-must-be-saveable for more information. [ComposeItemKeyNotSaveable]
            items(list, key = { NotSaveable(it.int) }) {}
                                ~~~~~~~~~~~~~~~~~~~
        src/NotSaveable.kt:12: Warning: Item keys in Lazy* layouts can be persisted across configuration changes and process death, so on Android the type of the key should be saveable via Bundle: a primitive, String/CharSequence, Serializable, or Parcelable. A key whose type is none of these can crash at runtime when the key is saved and restored (e.g. when item state is stored via rememberSaveable).

        Use a saveable identifier instead (e.g. a String or Int id), or make the key type Parcelable/Serializable.

        See https://slackhq.github.io/compose-lints/rules/#item-key-types-must-be-saveable for more information. [ComposeItemKeyNotSaveable]
            item(key = NotSaveable(0)) {}
                       ~~~~~~~~~~~~~~
        0 errors, 3 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `no errors for key params outside LazyListScope`() {
    // The rule is scoped to LazyListScope receivers. Other APIs that happen to take a `key`
    // (e.g. Pager composables, or unrelated functions) are left alone for now.
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.pager.HorizontalPager

      class NotSaveable(val int: Int)

      // An unrelated function that takes a `key` parameter.
      fun helper(key: Any?) {}

      @Composable
      fun Content(list: List<NotSaveable>) {
        HorizontalPager(pageCount = list.size, key = { list[it] }) {}
        helper(key = NotSaveable(0))
      }
      """
        .trimIndent()
    lint().files(*commonStubs, pagerStub, kotlin(code)).run().expectClean()
  }

  @Test
  fun `errors on other restricted scopes via receiver matching`() {
    // Receiver-type matching generalizes beyond LazyListScope to every type in RESTRICTED_SCOPES,
    // e.g. LazyGridScope, without any per-API special casing.
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
      import androidx.compose.foundation.lazy.grid.items

      class NotSaveable(val int: Int)

      @Composable
      fun Content(list: List<NotSaveable>) {
        LazyVerticalGrid {
          items(list, key = { it }) {}
          item(key = NotSaveable(0)) {}
        }
      }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, gridStub, kotlin(code))
      .run()
      .expect(
        """
        src/NotSaveable.kt:10: Warning: Item keys in Lazy* layouts can be persisted across configuration changes and process death, so on Android the type of the key should be saveable via Bundle: a primitive, String/CharSequence, Serializable, or Parcelable. A key whose type is none of these can crash at runtime when the key is saved and restored (e.g. when item state is stored via rememberSaveable).

        Use a saveable identifier instead (e.g. a String or Int id), or make the key type Parcelable/Serializable.

        See https://slackhq.github.io/compose-lints/rules/#item-key-types-must-be-saveable for more information. [ComposeItemKeyNotSaveable]
            items(list, key = { it }) {}
                                ~~
        src/NotSaveable.kt:11: Warning: Item keys in Lazy* layouts can be persisted across configuration changes and process death, so on Android the type of the key should be saveable via Bundle: a primitive, String/CharSequence, Serializable, or Parcelable. A key whose type is none of these can crash at runtime when the key is saved and restored (e.g. when item state is stored via rememberSaveable).

        Use a saveable identifier instead (e.g. a String or Int id), or make the key type Parcelable/Serializable.

        See https://slackhq.github.io/compose-lints/rules/#item-key-types-must-be-saveable for more information. [ComposeItemKeyNotSaveable]
            item(key = NotSaveable(0)) {}
                       ~~~~~~~~~~~~~~
        0 errors, 2 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `errors on subtypes of a restricted scope`() {
    // Scope matching walks supertypes, so a custom interface extending a restricted scope
    // (LazyListScope here) is covered without being listed in RESTRICTED_SCOPES itself.
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.lazy.LazyListScope
      import androidx.compose.foundation.lazy.items

      class NotSaveable(val int: Int)

      interface MyLazyScope : LazyListScope

      @Composable fun MyLazyColumn(content: MyLazyScope.() -> Unit) {}

      @Composable
      fun Content(list: List<NotSaveable>) {
        MyLazyColumn {
          items(list, key = { it }) {}
          item(key = NotSaveable(0)) {}
        }
      }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, lazyStub, kotlin(code))
      .run()
      .expect(
        """
        src/NotSaveable.kt:14: Warning: Item keys in Lazy* layouts can be persisted across configuration changes and process death, so on Android the type of the key should be saveable via Bundle: a primitive, String/CharSequence, Serializable, or Parcelable. A key whose type is none of these can crash at runtime when the key is saved and restored (e.g. when item state is stored via rememberSaveable).

        Use a saveable identifier instead (e.g. a String or Int id), or make the key type Parcelable/Serializable.

        See https://slackhq.github.io/compose-lints/rules/#item-key-types-must-be-saveable for more information. [ComposeItemKeyNotSaveable]
            items(list, key = { it }) {}
                                ~~
        src/NotSaveable.kt:15: Warning: Item keys in Lazy* layouts can be persisted across configuration changes and process death, so on Android the type of the key should be saveable via Bundle: a primitive, String/CharSequence, Serializable, or Parcelable. A key whose type is none of these can crash at runtime when the key is saved and restored (e.g. when item state is stored via rememberSaveable).

        Use a saveable identifier instead (e.g. a String or Int id), or make the key type Parcelable/Serializable.

        See https://slackhq.github.io/compose-lints/rules/#item-key-types-must-be-saveable for more information. [ComposeItemKeyNotSaveable]
            item(key = NotSaveable(0)) {}
                       ~~~~~~~~~~~~~~
        0 errors, 2 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `no errors for an unrelated scope with a key parameter`() {
    // A custom DSL scope shaped exactly like LazyListScope (a `key` parameter on both a member and
    // an extension) but not in RESTRICTED_SCOPES. Receiver-type scoping must leave it alone even
    // though the key values aren't saveable.
    val code =
      """
      import androidx.compose.runtime.Composable

      class NotSaveable(val int: Int)

      interface SomeOtherListScope {
        fun item(key: Any? = null, content: () -> Unit)
      }

      fun <T> SomeOtherListScope.items(
        items: List<T>,
        key: ((item: T) -> Any)? = null,
        content: (T) -> Unit
      ) {}

      @Composable fun MyList(content: SomeOtherListScope.() -> Unit) {}

      @Composable
      fun Content(list: List<NotSaveable>) {
        MyList {
          item(key = NotSaveable(0)) {}
          items(list, key = { it }) {}
        }
      }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `arrays are saveable only when their component type is`() {
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.lazy.LazyColumn
      import androidx.compose.foundation.lazy.items

      class NotSaveable(val int: Int)
      class Row(val intArray: IntArray, val stringArray: Array<String>, val notSaveableArray: Array<NotSaveable>)

      @Composable
      fun Content(list: List<Row>) {
        LazyColumn {
          items(list, key = { it.intArray }) {}
          items(list, key = { it.stringArray }) {}
          items(list, key = { it.notSaveableArray }) {}
        }
      }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, lazyStub, kotlin(code))
      .run()
      .expect(
        """
        src/NotSaveable.kt:13: Warning: Item keys in Lazy* layouts can be persisted across configuration changes and process death, so on Android the type of the key should be saveable via Bundle: a primitive, String/CharSequence, Serializable, or Parcelable. A key whose type is none of these can crash at runtime when the key is saved and restored (e.g. when item state is stored via rememberSaveable).

        Use a saveable identifier instead (e.g. a String or Int id), or make the key type Parcelable/Serializable.

        See https://slackhq.github.io/compose-lints/rules/#item-key-types-must-be-saveable for more information. [ComposeItemKeyNotSaveable]
            items(list, key = { it.notSaveableArray }) {}
                                ~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warning
        """
          .trimIndent()
      )
  }

  @Test
  fun `no errors when key type is saveable`() {
    val code =
      $$"""
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.lazy.LazyColumn
      import androidx.compose.foundation.lazy.items
      import androidx.compose.foundation.lazy.itemsIndexed
      import androidx.compose.foundation.pager.HorizontalPager

      data class Item(val string: String)

      class ParcelKey(val int: Int) : android.os.Parcelable

      class SerKey(val int: Int) : java.io.Serializable

      @Composable
      fun Content(list: List<Item>) {
        LazyColumn {
          items(list, key = { it.string }) {}
          items(list, key = { it.string.length }) {}
          items(list, key = { "${it.string}-row" }) {}
          itemsIndexed(list, key = { index, _ -> index }) { _, _ -> }
          items(list, key = { ParcelKey(it.string.length) }) {}
          items(list, key = { SerKey(it.string.length) }) {}
          item(key = "header") {}
          // A statically `Any`-typed key isn't provably unsaveable, so it's left alone.
          items(list, key = { it.string as Any }) {}
          items(list) {}
        }
        HorizontalPager(pageCount = list.size, key = { list[it].string }) {}
      }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, lazyStub, pagerStub, parcelableStub, kotlin(code))
      .run()
      .expectClean()
  }

  @Test
  fun `no errors when key type is lambda returning valid type`() {
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.lazy.LazyColumn
      import androidx.compose.foundation.lazy.items

      data class Item(val string: String)

      @Composable
      fun Content(list: List<Item>) {
        val getKey: (index: Int) -> Any = { index }

        LazyColumn {
          items(list, key = getKey) {}
        }
      }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, lazyStub, pagerStub, parcelableStub, kotlin(code))
      .run()
      .expectClean()
  }

  @Test
  fun `no errors for stdlib types that are serializable`() {
    // Common key types people reach for. All of enum, Pair/Triple, and List/Set are Serializable in
    // the stdlib, so resolving their supertypes must keep these clean. If any of these resolve
    // without their java.io.Serializable supertype the rule would fire on extremely common code.
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.lazy.LazyColumn
      import androidx.compose.foundation.lazy.items

      enum class Kind { A, B }

      data class Item(val string: String, val kind: Kind)

      @Composable
      fun Content(list: List<Item>) {
        LazyColumn {
          items(list, key = { it.kind }) {}
          items(list, key = { it.string to it.kind }) {}
          items(list, key = { Triple(it.string, it.kind, 0) }) {}
          items(list, key = { listOf(it.string) }) {}
          items(list, key = { setOf(it.string) }) {}
        }
      }
      """
        .trimIndent()
    lint().files(*commonStubs, lazyStub, kotlin(code)).run().expectClean()
  }

  @Test
  fun `errors when key type is a plain data class`() {
    // The most common real trigger: a composite key built from a data class, which is NOT
    // Serializable/Parcelable by default.
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.lazy.LazyColumn
      import androidx.compose.foundation.lazy.items

      data class Item(val int: Int, val string: String)
      data class CompositeKey(val int: Int, val string: String)

      @Composable
      fun Content(list: List<Item>) {
        LazyColumn {
          items(list, key = { CompositeKey(it.int, it.string) }) {}
        }
      }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, lazyStub, kotlin(code))
      .run()
      .expect(
        """
        src/Item.kt:11: Warning: Item keys in Lazy* layouts can be persisted across configuration changes and process death, so on Android the type of the key should be saveable via Bundle: a primitive, String/CharSequence, Serializable, or Parcelable. A key whose type is none of these can crash at runtime when the key is saved and restored (e.g. when item state is stored via rememberSaveable).

        Use a saveable identifier instead (e.g. a String or Int id), or make the key type Parcelable/Serializable.

        See https://slackhq.github.io/compose-lints/rules/#item-key-types-must-be-saveable for more information. [ComposeItemKeyNotSaveable]
            items(list, key = { CompositeKey(it.int, it.string) }) {}
                                ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warning
        """
          .trimIndent()
      )
  }

  @Test
  fun `positional key arguments are detected`() {
    // The key argument is resolved via parameter mapping, not by matching a literal `key =` name,
    // so
    // a key passed positionally is detected too.
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.lazy.LazyColumn

      class NotSaveable(val int: Int)

      @Composable
      fun Content() {
        LazyColumn {
          item(NotSaveable(0)) {}
        }
      }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, lazyStub, kotlin(code))
      .run()
      .expect(
        """
        src/NotSaveable.kt:9: Warning: Item keys in Lazy* layouts can be persisted across configuration changes and process death, so on Android the type of the key should be saveable via Bundle: a primitive, String/CharSequence, Serializable, or Parcelable. A key whose type is none of these can crash at runtime when the key is saved and restored (e.g. when item state is stored via rememberSaveable).

        Use a saveable identifier instead (e.g. a String or Int id), or make the key type Parcelable/Serializable.

        See https://slackhq.github.io/compose-lints/rules/#item-key-types-must-be-saveable for more information. [ComposeItemKeyNotSaveable]
            item(NotSaveable(0)) {}
                 ~~~~~~~~~~~~~~
        0 errors, 1 warning
        """
          .trimIndent()
      )
  }

  @Test
  fun `no errors outside a composable context`() {
    val code =
      """
      class NotSaveable(val int: Int)

      // A non-composable function with a `key` parameter shouldn't be flagged.
      fun helper(key: Any?) {}

      fun notComposable() {
        helper(key = NotSaveable(0))
      }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors when key type is a type parameter`() {
    // Generics are deliberately left alone: a key whose static type is a bare type parameter isn't
    // provably unsaveable, so the conservative contract is to stay quiet even when the eventual
    // runtime type might not be saveable.
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.lazy.LazyListScope
      import androidx.compose.foundation.lazy.items

      @Composable
      fun <T> Content(list: List<T>, keySelector: (T) -> T, content: LazyListScope.() -> Unit) {
        content {
          items(list, key = { keySelector(it) }) {}
        }
      }
      """
        .trimIndent()
    lint().files(*commonStubs, lazyStub, kotlin(code)).run().expectClean()
  }

  @Test
  fun `errors when a multi-statement key lambda returns an unsaveable type`() {
    // The key argument can be a block lambda with intermediate statements; the type is read off the
    // returned expression, not the whole block.
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.lazy.LazyColumn
      import androidx.compose.foundation.lazy.items

      data class Item(val int: Int)
      data class CompositeKey(val int: Int)

      @Composable
      fun Content(list: List<Item>) {
        LazyColumn {
          items(list, key = {
            val id = it.int
            CompositeKey(id)
          }) {}
        }
      }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, lazyStub, kotlin(code))
      .run()
      .expect(
        """
        src/Item.kt:13: Warning: Item keys in Lazy* layouts can be persisted across configuration changes and process death, so on Android the type of the key should be saveable via Bundle: a primitive, String/CharSequence, Serializable, or Parcelable. A key whose type is none of these can crash at runtime when the key is saved and restored (e.g. when item state is stored via rememberSaveable).

        Use a saveable identifier instead (e.g. a String or Int id), or make the key type Parcelable/Serializable.

        See https://slackhq.github.io/compose-lints/rules/#item-key-types-must-be-saveable for more information. [ComposeItemKeyNotSaveable]
              CompositeKey(id)
              ~~~~~~~~~~~~~~~~
        0 errors, 1 warning
        """
          .trimIndent()
      )
  }

  @Test
  fun `errors on itemsIndexed with an unsaveable key`() {
    // itemsIndexed has a two-parameter key lambda; the returned expression is still inspected.
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.lazy.LazyColumn
      import androidx.compose.foundation.lazy.itemsIndexed

      class NotSaveable(val int: Int)

      @Composable
      fun Content(list: List<NotSaveable>) {
        LazyColumn {
          itemsIndexed(list, key = { _, item -> item }) { _, _ -> }
        }
      }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, lazyStub, kotlin(code))
      .run()
      .expect(
        """
        src/NotSaveable.kt:10: Warning: Item keys in Lazy* layouts can be persisted across configuration changes and process death, so on Android the type of the key should be saveable via Bundle: a primitive, String/CharSequence, Serializable, or Parcelable. A key whose type is none of these can crash at runtime when the key is saved and restored (e.g. when item state is stored via rememberSaveable).

        Use a saveable identifier instead (e.g. a String or Int id), or make the key type Parcelable/Serializable.

        See https://slackhq.github.io/compose-lints/rules/#item-key-types-must-be-saveable for more information. [ComposeItemKeyNotSaveable]
            itemsIndexed(list, key = { _, item -> item }) { _, _ -> }
                                                  ~~~~
        0 errors, 1 warning
        """
          .trimIndent()
      )
  }

  @Test
  fun `errors on the staggered grid scope`() {
    // LazyStaggeredGridScope is in RESTRICTED_SCOPES; verify it directly rather than relying solely
    // on the receiver-matching mechanism proven for LazyListScope/LazyGridScope.
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
      import androidx.compose.foundation.lazy.staggeredgrid.items

      class NotSaveable(val int: Int)

      @Composable
      fun Content(list: List<NotSaveable>) {
        LazyVerticalStaggeredGrid {
          items(list, key = { it }) {}
          item(key = NotSaveable(0)) {}
        }
      }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, staggeredGridStub, kotlin(code))
      .run()
      .expect(
        """
        src/NotSaveable.kt:10: Warning: Item keys in Lazy* layouts can be persisted across configuration changes and process death, so on Android the type of the key should be saveable via Bundle: a primitive, String/CharSequence, Serializable, or Parcelable. A key whose type is none of these can crash at runtime when the key is saved and restored (e.g. when item state is stored via rememberSaveable).

        Use a saveable identifier instead (e.g. a String or Int id), or make the key type Parcelable/Serializable.

        See https://slackhq.github.io/compose-lints/rules/#item-key-types-must-be-saveable for more information. [ComposeItemKeyNotSaveable]
            items(list, key = { it }) {}
                                ~~
        src/NotSaveable.kt:11: Warning: Item keys in Lazy* layouts can be persisted across configuration changes and process death, so on Android the type of the key should be saveable via Bundle: a primitive, String/CharSequence, Serializable, or Parcelable. A key whose type is none of these can crash at runtime when the key is saved and restored (e.g. when item state is stored via rememberSaveable).

        Use a saveable identifier instead (e.g. a String or Int id), or make the key type Parcelable/Serializable.

        See https://slackhq.github.io/compose-lints/rules/#item-key-types-must-be-saveable for more information. [ComposeItemKeyNotSaveable]
            item(key = NotSaveable(0)) {}
                       ~~~~~~~~~~~~~~
        0 errors, 2 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `no errors when key type is a nullable saveable type`() {
    // Nullability shouldn't break supertype resolution: a nullable String key is still saveable.
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.lazy.LazyColumn
      import androidx.compose.foundation.lazy.items

      data class Item(val string: String?)

      @Composable
      fun Content(list: List<Item>) {
        LazyColumn {
          items(list, key = { it.string }) {}
        }
      }
      """
        .trimIndent()
    lint().files(*commonStubs, lazyStub, kotlin(code)).run().expectClean()
  }
}
