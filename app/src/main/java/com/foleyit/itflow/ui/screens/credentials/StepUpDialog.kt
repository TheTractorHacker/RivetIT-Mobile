package com.foleyit.itflow.ui.screens.credentials

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Asks for the account password (and, when [needsCode], a TOTP or recovery code) so the server will accept a new
 * credential-reveal key. The typed values live only in this composable's state: they are handed to [onSubmit] and
 * dropped when the prompt closes, never stored or logged.
 */
@Composable
fun StepUpDialog(
    needsCode: Boolean,
    busy: Boolean,
    error: String?,
    onSubmit: (password: String, code: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) { password = ""; code = ""; onDismiss() } },
        title = { Text("Confirm it's you") },
        text = {
            Column {
                Text("To register this device for viewing credentials, enter your account password" +
                    if (needsCode) " and a code." else ".")
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Current password") }, singleLine = true, enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.padding(top = 12.dp)
                )
                if (needsCode) {
                    OutlinedTextField(
                        value = code, onValueChange = { code = it },
                        label = { Text("Authenticator or recovery code") }, singleLine = true, enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && password.isNotEmpty() && (!needsCode || code.isNotBlank()),
                onClick = {
                    // Kept while the prompt stays open so a "code needed" answer does not force retyping;
                    // the composable leaves composition on success or cancel and the state goes with it.
                    onSubmit(password, if (needsCode) code.trim().ifEmpty { null } else null)
                }
            ) { if (busy) CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp) else Text("Continue") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } }
    )
}
