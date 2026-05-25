// Copyright (C) 2026 Salesforce, Inc.
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.intellij.lang.annotations.Language
import org.junit.Test

class ItemKeyHashCodeDetectorTest : BaseComposeLintTest() {

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

  override fun getDetector(): Detector = ItemKeyHashCodeDetector()

  override fun getIssues(): List<Issue> = listOf(ItemKeyHashCodeDetector.ISSUE)

  @Test
  fun `errors when hashCode is used in a key`() {
    @Language("kotlin")
    val code =
      """
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.lazy.LazyColumn
      import androidx.compose.foundation.lazy.items
      import androidx.compose.foundation.lazy.itemsIndexed
      import androidx.compose.foundation.pager.HorizontalPager

      data class Item(val id: String)

      @Composable
      fun Content(list: List<Item>) {
        LazyColumn {
          items(list, key = { it.hashCode() }) {}
          itemsIndexed(list, key = { _, item -> item.id.hashCode() }) { _, _ -> }
          item(key = list.hashCode()) {}
        }
        HorizontalPager(pageCount = list.size, key = { list[it].hashCode() }) {}
      }
      """
        .trimIndent()
    lint()
      .files(*commonStubs, lazyStub, pagerStub, kotlin(code))
      .run()
      .expect(
        """
        src/Item.kt:12: Warning: Item keys in Lazy*/Pager/etc. layouts must be unique, but hashCode() is never guaranteed to be unique. Lazy* layouts throw at runtime when they encounter duplicate keys, so a hashCode-based key can crash as soon as data with a colliding hash comes along. Use a genuinely unique, stable identifier instead (e.g. a server-provided id). See https://slackhq.github.io/compose-lints/rules/#dont-use-hashcode-as-a-key for more information. [ComposeItemKeyHashCode]
            items(list, key = { it.hashCode() }) {}
                                ~~~~~~~~~~~~~
        src/Item.kt:13: Warning: Item keys in Lazy*/Pager/etc. layouts must be unique, but hashCode() is never guaranteed to be unique. Lazy* layouts throw at runtime when they encounter duplicate keys, so a hashCode-based key can crash as soon as data with a colliding hash comes along. Use a genuinely unique, stable identifier instead (e.g. a server-provided id). See https://slackhq.github.io/compose-lints/rules/#dont-use-hashcode-as-a-key for more information. [ComposeItemKeyHashCode]
            itemsIndexed(list, key = { _, item -> item.id.hashCode() }) { _, _ -> }
                                                  ~~~~~~~~~~~~~~~~~~
        src/Item.kt:14: Warning: Item keys in Lazy*/Pager/etc. layouts must be unique, but hashCode() is never guaranteed to be unique. Lazy* layouts throw at runtime when they encounter duplicate keys, so a hashCode-based key can crash as soon as data with a colliding hash comes along. Use a genuinely unique, stable identifier instead (e.g. a server-provided id). See https://slackhq.github.io/compose-lints/rules/#dont-use-hashcode-as-a-key for more information. [ComposeItemKeyHashCode]
            item(key = list.hashCode()) {}
                       ~~~~~~~~~~~~~~~
        src/Item.kt:16: Warning: Item keys in Lazy*/Pager/etc. layouts must be unique, but hashCode() is never guaranteed to be unique. Lazy* layouts throw at runtime when they encounter duplicate keys, so a hashCode-based key can crash as soon as data with a colliding hash comes along. Use a genuinely unique, stable identifier instead (e.g. a server-provided id). See https://slackhq.github.io/compose-lints/rules/#dont-use-hashcode-as-a-key for more information. [ComposeItemKeyHashCode]
          HorizontalPager(pageCount = list.size, key = { list[it].hashCode() }) {}
                                                         ~~~~~~~~~~~~~~~~~~~
        0 errors, 4 warnings
        """
          .trimIndent()
      )
  }

  @Test
  fun `no errors when keys are unique and stable`() {
    @Language("kotlin")
    val code =
      $$"""
      import androidx.compose.runtime.Composable
      import androidx.compose.foundation.lazy.LazyColumn
      import androidx.compose.foundation.lazy.items
      import androidx.compose.foundation.pager.HorizontalPager

      data class Item(val id: String)

      @Composable
      fun Content(list: List<Item>) {
        LazyColumn {
          items(list, key = { it.id }) {}
          items(list, key = { "${it.id}-row" }) {}
          items(list, key = { java.lang.System.identityHashCode(it) }) {}
          items(list) {}
        }
        HorizontalPager(pageCount = list.size, key = { list[it].id }) {}
      }
      """
        .trimIndent()
    lint().files(*commonStubs, lazyStub, pagerStub, kotlin(code)).run().expectClean()
  }

  @Test
  fun `no errors outside a composable context`() {
    @Language("kotlin")
    val code =
      """
      // A non-composable function with a `key` parameter shouldn't be flagged.
      fun helper(key: Any?) {}

      fun notComposable() {
        helper(key = "test".hashCode())
      }
      """
        .trimIndent()
    lint().files(*commonStubs, kotlin(code)).run().expectClean()
  }
}
