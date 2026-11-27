/*
 * Yumeiro — Copyright (C) 2026 siamform
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE and TRADEMARKS.md.
 * Do not remove or alter this notice.
 */
package dev.asfakul.yumeiro.ui.screens.online

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.asfakul.yumeiro.online.ExtensionLabels
import dev.asfakul.yumeiro.ui.theme.yuRound

/**
 * F-21: the shared two-line extension label. Line 1: name (ellipsis) + language chip, which never
 * shrinks away. Line 2: "3 sources • CloudStream • Repo", one line with ellipsis. [status] (e.g.
 * "120 titles", "Loading…") goes in front of line 2 when given.
 */
@Composable
internal fun ExtensionLabelText(
    label: ExtensionLabels.Label,
    titleColor: Color,
    lineColor: Color,
    modifier: Modifier = Modifier,
    status: String? = null,
    statusColor: Color = lineColor,
    titleSize: TextUnit = 15.sp,
    titleWeight: FontWeight = FontWeight.Bold,
    trailing: @Composable () -> Unit = {},
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.title, color = titleColor, fontWeight = titleWeight, fontSize = titleSize, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            label.lang?.let {
                Spacer(Modifier.width(6.dp))
                Text(
                    it, color = lineColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                    modifier = Modifier.border(1.dp, lineColor.copy(alpha = 0.5f), yuRound(6)).padding(horizontal = 5.dp, vertical = 1.dp).testTag("ext_lang_chip"),
                )
            }
            trailing()
        }
        val line = listOfNotNull(status?.takeIf { it.isNotBlank() }, label.line.takeIf { it.isNotBlank() }).joinToString(" • ")
        if (line.isNotEmpty()) Text(
            line, color = if (status != null && label.line.isBlank()) statusColor else lineColor, fontSize = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("ext_label_line"),
        )
    }
}
