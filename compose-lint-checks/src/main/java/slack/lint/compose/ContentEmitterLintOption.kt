// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package slack.lint.compose

import com.android.tools.lint.detector.api.StringOption
import slack.lint.compose.util.StringSetLintOption

class ContentEmitterLintOption(option: StringOption) : StringSetLintOption(option) {
  companion object {
    /**
     * We reuse the content-emitters option in lint but it has this annoying behavior where options
     * can _not_ be reused across lints. This includes not only declarations, but also even `Option`
     * instances themselves because they're mutable and associated with specific `Issue` instances!
     */
    fun newOption(): StringOption {
      return StringOption(
        "content-emitters",
        "A comma-separated list of known content-emitting composables",
        null,
        "This property should define a comma-separated list of known content-emitting composables."
      )
    }
  }
}
