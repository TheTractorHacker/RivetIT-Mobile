package com.foleyit.itflow.ui.screens.requests

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.foleyit.itflow.R
import com.foleyit.itflow.data.model.*
import com.foleyit.itflow.ui.components.*
import com.foleyit.itflow.ui.navigation.Screen
import com.foleyit.itflow.ui.theme.Motion
import com.foleyit.itflow.ui.theme.statusColors
import com.foleyit.itflow.ui.util.userMessage
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun RequestsScreen(navController: NavController) {
    val vm: ServiceCatalogViewModel = viewModel(factory = ServiceCatalogViewModel.Factory)
    val ui by vm.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    // Back steps out of the form / result before leaving the screen.
    BackHandler(enabled = ui.stage !is RequestStage.Catalog) { vm.backToCatalog() }

    LaunchedEffect(ui.stage) {
        if (ui.stage is RequestStage.Done) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    AnimatedContent(
        targetState = ui.stage::class,
        transitionSpec = {
            (fadeIn(Motion.medium()) + slideInVertically(Motion.medium()) { it / 20 }) togetherWith fadeOut(Motion.fast())
        },
        label = "requestStage",
    ) { stage ->
        when (stage) {
            RequestStage.Catalog::class -> CatalogStage(ui, vm)
            RequestStage.Form::class -> (ui.stage as? RequestStage.Form)?.let { FormStage(it.item, ui, vm) }
            else -> (ui.stage as? RequestStage.Done)?.let { DoneStage(it, navController, vm) }
        }
    }
}

// ── Catalog ───────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogStage(ui: RequestUiState, vm: ServiceCatalogViewModel) {
    val load = ui.load
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.requests_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
        )
        when {
            load.isInitialLoading -> LoadingScreen()
            load.isInitialError -> ErrorScreen(userMessage(load.error!!), onRetry = vm::refresh)
            else -> {
                val result = load.data!!
                val shelves = remember(result, ui.query) { CatalogLogic.shelves(result, ui.query) }
                OutlinedTextField(
                    value = ui.query, onValueChange = vm::setQuery,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 48.dp),
                    placeholder = { Text(stringResource(R.string.requests_search), style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (ui.query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) {
                                Icon(Icons.Outlined.Clear, stringResource(R.string.requests_search_clear), Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true, shape = MaterialTheme.shapes.extraLarge,
                )
                PullToRefreshBox(isRefreshing = load.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                    if (result.items.isEmpty() || (shelves.searching && shelves.all.isEmpty())) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Box(Modifier.fillMaxWidth().heightIn(min = 320.dp)) {
                                EmptyScreen(
                                    stringResource(if (result.items.isEmpty()) R.string.requests_empty else R.string.requests_no_matches),
                                    Icons.Outlined.SearchOff,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (load.error != null) item(key = "status") { PagedListStatus(false, userMessage(load.error), vm::refresh) }
                            if (shelves.popular.isNotEmpty()) item(key = "popular") { Shelf(R.string.requests_popular, shelves.popular, vm::open) }
                            if (shelves.recent.isNotEmpty()) item(key = "recent") { Shelf(R.string.requests_recent, shelves.recent, vm::open) }
                            item(key = "all_label") { if (!shelves.searching) SectionLabel(stringResource(R.string.requests_all)) }
                            items(shelves.all, key = { "all_${it.id}" }) { item ->
                                CatalogRow(item, Modifier.animateItem()) { vm.open(item) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Shelf(titleRes: Int, items: List<CatalogItem>, onOpen: (CatalogItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { item -> CatalogTile(item) { onOpen(item) } }
        }
    }
}

private fun iconVector(name: String): ImageVector = when (catalogIconFor(name)) {
    CatalogIcon.LAPTOP -> Icons.Outlined.Laptop
    CatalogIcon.PERSON -> Icons.Outlined.PersonAdd
    CatalogIcon.LOCK -> Icons.Outlined.Key
    CatalogIcon.NETWORK -> Icons.Outlined.Wifi
    CatalogIcon.PRINTER -> Icons.Outlined.Print
    CatalogIcon.PHONE -> Icons.Outlined.PhoneAndroid
    CatalogIcon.MAIL -> Icons.Outlined.Email
    CatalogIcon.APPS -> Icons.Outlined.Apps
    CatalogIcon.SECURITY -> Icons.Outlined.Shield
    CatalogIcon.CLOUD -> Icons.Outlined.Cloud
    CatalogIcon.STORAGE -> Icons.Outlined.Storage
    CatalogIcon.DEFAULT -> Icons.Outlined.Description
}

@Composable
private fun CatalogTile(item: CatalogItem, onClick: () -> Unit) {
    val desc = stringResource(R.string.requests_tile_desc, item.name, if (item.requiresApproval) stringResource(R.string.requests_needs_approval) else "")
    Card(onClick = onClick, shape = MaterialTheme.shapes.large, modifier = Modifier.width(150.dp).semantics { contentDescription = desc }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(iconVector(item.icon), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (item.requiresApproval) StatusChip(stringResource(R.string.requests_needs_approval), MaterialTheme.statusColors.info)
        }
    }
}

@Composable
private fun CatalogRow(item: CatalogItem, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(iconVector(item.icon), null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                }
            },
            headlineContent = { Text(item.name, fontWeight = FontWeight.Medium) },
            supportingContent = {
                Column {
                    if (item.description.isNotBlank()) Text(item.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.requiresApproval) {
                        Spacer(Modifier.height(4.dp))
                        StatusChip(stringResource(R.string.requests_needs_approval), MaterialTheme.statusColors.info)
                    }
                }
            },
            trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
        )
    }
}

// ── Form ──────────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormStage(item: CatalogItem, ui: RequestUiState, vm: ServiceCatalogViewModel) {
    val evaluator = remember(item) { ShowIfEvaluator(item.fields) }
    val visible = remember(item, ui.answers) { evaluator.visibleKeys(ui.answers).toSet() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::backToCatalog, enabled = !ui.submitting) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back))
            }
            Text(item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).semantics { heading() })
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.requiresApproval) StatusChip(stringResource(R.string.requests_needs_approval_desc), MaterialTheme.statusColors.info, icon = Icons.Outlined.HowToReg)

            item.fields.forEach { f ->
                // Conditional fields slide in and out with the app's standard motion; hidden fields hold no answer.
                AnimatedVisibility(
                    visible = f.key in visible,
                    enter = fadeIn(Motion.medium()) + expandVertically(Motion.medium()),
                    exit = fadeOut(Motion.fast()) + shrinkVertically(Motion.fast()),
                ) {
                    FieldInput(f, ui.answers[f.key].orEmpty(), ui.errors[f.key], enabled = !ui.submitting) { vm.setAnswer(f.key, it) }
                }
            }

            if (ui.errors.isNotEmpty()) {
                Text(stringResource(R.string.requests_fix_errors), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            ui.submitError?.let { e ->
                val msg = userMessage(e)
                Text(
                    if (e is IOException) stringResource(R.string.requests_submit_uncertain, msg) else stringResource(R.string.requests_submit_failed, msg),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = vm::submit, enabled = !ui.submitting, modifier = Modifier.fillMaxWidth()) {
                if (ui.submitting) {
                    val d = stringResource(R.string.requests_submitting)
                    CircularProgressIndicator(Modifier.size(18.dp).semantics { contentDescription = d }, strokeWidth = 2.dp)
                } else Text(stringResource(R.string.requests_submit))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun errorText(e: FieldError): String = when (e) {
    FieldError.Required -> stringResource(R.string.requests_err_required)
    FieldError.MustBeTicked -> stringResource(R.string.requests_err_ticked)
    is FieldError.TooLong -> stringResource(R.string.requests_err_too_long, e.max)
    FieldError.NotANumber -> stringResource(R.string.requests_err_number)
    FieldError.BadDate -> stringResource(R.string.requests_err_date)
    FieldError.NotAChoice -> stringResource(R.string.requests_err_choice)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldInput(f: CatalogField, value: String, error: FieldError?, enabled: Boolean, onChange: (String) -> Unit) {
    val requiredWord = stringResource(R.string.requests_required_marker)
    val label = if (f.required) "${f.label} *" else f.label
    val labelDesc = if (f.required) "${f.label}, $requiredWord" else f.label
    val support: (@Composable () -> Unit)? = error?.let { { Text(errorText(it)) } }
    val placeholder: (@Composable () -> Unit)? = f.placeholder.takeIf { it.isNotBlank() }?.let { { Text(it) } }

    when (f.type) {
        FieldType.TEXT, FieldType.TEXTAREA, FieldType.NUMBER -> OutlinedTextField(
            value = value, onValueChange = onChange, enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = labelDesc },
            label = { Text(label) }, placeholder = placeholder, isError = error != null, supportingText = support,
            singleLine = f.type != FieldType.TEXTAREA,
            minLines = if (f.type == FieldType.TEXTAREA) 3 else 1,
            maxLines = if (f.type == FieldType.TEXTAREA) 8 else 1,
            keyboardOptions = KeyboardOptions(keyboardType = if (f.type == FieldType.NUMBER) KeyboardType.Decimal else KeyboardType.Text),
        )
        FieldType.SELECT -> {
            var open by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { if (enabled) open = it }) {
                OutlinedTextField(
                    value = value, onValueChange = {}, readOnly = true, enabled = enabled,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth().semantics { contentDescription = labelDesc },
                    label = { Text(label) },
                    placeholder = { Text(stringResource(R.string.requests_select_placeholder)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                    isError = error != null, supportingText = support,
                )
                ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    if (!f.required) DropdownMenuItem(text = { Text(stringResource(R.string.requests_select_placeholder)) }, onClick = { onChange(""); open = false })
                    f.options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onChange(o); open = false }) }
                }
            }
        }
        FieldType.CHECKBOX -> {
            val ticked = ShowIfEvaluator.effectiveAnswer(FieldType.CHECKBOX, value).isNotEmpty()
            Column {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(enabled = enabled, role = Role.Checkbox) { onChange(if (ticked) "" else "1") }
                        .semantics(mergeDescendants = true) { contentDescription = labelDesc },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = ticked, onCheckedChange = null, enabled = enabled)
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
                if (error != null) Text(errorText(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp))
            }
        }
        FieldType.DATE -> {
            var picking by remember { mutableStateOf(false) }
            val pickDesc = stringResource(R.string.requests_pick_date)
            OutlinedTextField(
                value = value, onValueChange = {}, readOnly = true, enabled = enabled,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = labelDesc },
                label = { Text(label) }, placeholder = placeholder ?: { Text("YYYY-MM-DD") },
                trailingIcon = { IconButton(onClick = { picking = true }, enabled = enabled) { Icon(Icons.Outlined.CalendarMonth, pickDesc) } },
                isError = error != null, supportingText = support,
            )
            if (picking) {
                val state = rememberDatePickerState(initialSelectedDateMillis = runCatching {
                    java.time.LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                }.getOrNull())
                DatePickerDialog(
                    onDismissRequest = { picking = false },
                    confirmButton = {
                        TextButton(onClick = {
                            state.selectedDateMillis?.let { ms -> onChange(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                            picking = false
                        }) { Text(stringResource(R.string.date_picker_ok)) }
                    },
                    dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
                ) { DatePicker(state = state) }
            }
        }
    }
}

// ── Result ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun DoneStage(done: RequestStage.Done, navController: NavController, vm: ServiceCatalogViewModel) {
    val pending = done.result.status == RequestStatus.PENDING_APPROVAL
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (pending) Icons.Outlined.HourglassTop else Icons.Outlined.CheckCircle, null,
            Modifier.size(72.dp),
            tint = if (pending) MaterialTheme.statusColors.warning else MaterialTheme.statusColors.success,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(if (pending) R.string.requests_pending else R.string.requests_created),
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (pending) R.string.requests_pending_body else R.string.requests_created_body),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { navController.navigate(Screen.TicketDetail.go(done.result.ticketId)) }) {
            Text(stringResource(R.string.requests_open_ticket))
        }
        TextButton(onClick = vm::backToCatalog) { Text(stringResource(R.string.requests_another)) }
    }
}
