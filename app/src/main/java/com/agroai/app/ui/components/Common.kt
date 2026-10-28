package com.agroai.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agroai.app.R
import com.agroai.app.ui.theme.AgroSoft

data class LocalizedOption(val value: String, @StringRes val label: Int)

@Composable
fun BrandHeader(language: String, onLanguage: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_agro_logo), null, Modifier.size(48.dp), tint = androidx.compose.ui.graphics.Color.Unspecified)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.brand_tagline), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LanguageButtons(language, onLanguage)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun LanguageButtons(language: String, onLanguage: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("en" to R.string.language_en_short, "hi" to R.string.language_hi_short).forEach { (code, label) ->
            FilterChip(selected = language == code, onClick = { onLanguage(code) }, label = { Text(stringResource(label)) })
        }
    }
}

@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun StepHeader(step: Int, title: String, subtitle: String) {
    Text(stringResource(R.string.step_count, step + 1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    LinearProgressIndicator(progress = { (step + 1) / 5f }, modifier = Modifier.fillMaxWidth())
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionDropdown(label: String, value: String, options: List<LocalizedOption>, onSelect: (String) -> Unit, isError: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.value == value }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { stringResource(it.label) }.orEmpty(), onValueChange = {}, readOnly = true,
            label = { Text(label) }, isError = isError, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(), shape = RoundedCornerShape(11.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(stringResource(option.label)) }, onClick = { onSelect(option.value); expanded = false }) }
        }
    }
}

@Composable
fun MultiChoice(label: String, options: List<LocalizedOption>, selected: Set<String>, onToggle: (String) -> Unit, isError: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        options.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    FilterChip(selected = option.value in selected, onClick = { onToggle(option.value) }, label = { Text(stringResource(option.label)) }, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun SummaryRow(label: String, value: String, onEdit: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value.ifBlank { stringResource(R.string.not_provided) }, fontWeight = FontWeight.SemiBold) }
        if (onEdit != null) TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
    }
}

@Composable
fun ErrorText(show: Boolean) {
    if (show) Text(stringResource(R.string.required_fields), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
