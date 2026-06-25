package com.shaqsid.smart.feature.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shaqsid.smart.util.Countries
import com.shaqsid.smart.util.Country

/**
 * A searchable country picker. Shows the country name (+ dial code) and lets the user type to
 * filter. The selected [Country]'s dial code is what callers pass to the Tuya SDK.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryDropdown(
    selected: Country,
    onSelected: (Country) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    // Re-syncs to the selected label whenever selection changes (e.g. auto-detection).
    var query by remember(selected) { mutableStateOf(selected.label) }

    val options = remember(query, expanded) {
        if (!expanded || query == selected.label) {
            Countries.ALL
        } else {
            val q = query.trim().removePrefix("+")
            Countries.ALL.filter { it.name.contains(q, ignoreCase = true) || it.dialCode.startsWith(q) }
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = true
            },
            label = { Text("Country") },
            singleLine = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                query = selected.label
            },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            options.forEach { country ->
                DropdownMenuItem(
                    text = { Text(country.label) },
                    onClick = {
                        onSelected(country)
                        query = country.label
                        expanded = false
                    }
                )
            }
        }
    }
}
