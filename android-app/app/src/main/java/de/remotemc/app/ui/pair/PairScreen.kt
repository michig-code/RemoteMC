package de.remotemc.app.ui.pair

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.remotemc.app.R
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.net.Tls
import de.remotemc.app.ui.components.AppLogo
import de.remotemc.app.ui.components.McCard
import de.remotemc.app.ui.theme.McColors
import de.remotemc.app.ui.theme.statusTextColor
import de.remotemc.app.ui.util.errorText
import de.remotemc.app.ui.util.rememberHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairScreen(
    vm: PairViewModel,
    onScan: () -> Unit,
    onClose: () -> Unit,
    onPaired: (ServerProfile) -> Unit,
) {
    val step = vm.step
    val haptics = rememberHaptics()
    LaunchedEffect(step) {
        if (step is PairStep.Done) {
            haptics.success()
            onPaired(step.profile)
        }
    }
    LaunchedEffect(vm.error) { if (vm.error != null) haptics.error() }
    BackHandler { if (!vm.back()) onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pair_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (!vm.back()) onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(
                targetState = step,
                contentKey = { it::class },
                transitionSpec = { (slideInHorizontally { it / 5 } + fadeIn()).togetherWith(fadeOut()) },
                label = "pairStep",
                modifier = Modifier.widthIn(max = 560.dp),
            ) { s ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (s) {
                        PairStep.Choose -> ChooseStep(vm, onScan)
                        is PairStep.Form -> FormStep(vm, s.method)
                        PairStep.Probing, PairStep.Pairing -> Busy(
                            stringResource(if (s == PairStep.Probing) R.string.pair_checking else R.string.pair_pairing),
                        )
                        is PairStep.Confirm -> ConfirmStep(vm, s)
                        is PairStep.Done -> Busy(stringResource(R.string.pair_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ChooseStep(vm: PairViewModel, onScan: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AppLogo(80.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.pair_intro_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.pair_intro_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    CommandHint("/remotemc pair")
    MethodCard(Icons.Outlined.QrCodeScanner, stringResource(R.string.pair_scan), stringResource(R.string.pair_scan_text), primary = true, onClick = onScan)
    MethodCard(Icons.Outlined.Dialpad, stringResource(R.string.pair_enter_code), stringResource(R.string.pair_enter_code_text)) {
        vm.choose(PairMethod.CODE)
    }
    TextButton(onClick = { vm.choose(PairMethod.TOKEN) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Icon(Icons.Outlined.Key, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.pair_use_token))
    }
}

@Composable
private fun CommandHint(command: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pair_command_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(command, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MethodCard(icon: ImageVector, title: String, text: String, primary: Boolean = false, onClick: () -> Unit) {
    McCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(
                    if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer, CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FormStep(vm: PairViewModel, method: PairMethod) {
    val portValid = vm.port.toIntOrNull()?.let { it in 1..65535 } == true
    val secretValid = if (method == PairMethod.CODE) vm.code.length == 6 else vm.token.isNotBlank()
    val valid = vm.host.isNotBlank() && portValid && secretValid

    Text(
        stringResource(if (method == PairMethod.CODE) R.string.pair_enter_code else R.string.pair_use_token),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        stringResource(if (method == PairMethod.CODE) R.string.pair_code_form_text else R.string.pair_token_form_text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ErrorBox(vm.error)
    OutlinedTextField(
        vm.host, { vm.host = it.trim() },
        label = { Text(stringResource(R.string.pair_address)) },
        placeholder = { Text("play.example.com / 192.168.1.20") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        vm.port, { vm.port = it.filter(Char::isDigit).take(5) },
        label = { Text(stringResource(R.string.pair_port)) },
        singleLine = true,
        isError = vm.port.isNotEmpty() && !portValid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    if (method == PairMethod.CODE) {
        OutlinedTextField(
            vm.code, { vm.code = it.filter(Char::isDigit).take(6) },
            label = { Text(stringResource(R.string.pair_code)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 8.sp, fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (valid) vm.submitForm(method) }),
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        OutlinedTextField(
            vm.token, { vm.token = it.trim() },
            label = { Text(stringResource(R.string.pair_token)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.pair_https), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.pair_https_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(vm.tls, { vm.tls = it })
    }
    Button(onClick = { vm.submitForm(method) }, enabled = valid, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(stringResource(R.string.action_continue))
    }
}

@Composable
private fun ConfirmStep(vm: PairViewModel, s: PairStep.Confirm) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AppLogo(64.dp)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.pair_confirm_title), style = MaterialTheme.typography.headlineSmall)
        Text("${s.host}:${s.port}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.pair_server_info, s.ping.platform, s.ping.version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ErrorBox(vm.error)
    SecurityBox(s)
    if (!s.mismatch) {
        OutlinedTextField(
            vm.serverName, { if (it.length <= 40) vm.serverName = it },
            label = { Text(stringResource(R.string.pair_server_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (s.code != null) {
            OutlinedTextField(
                vm.deviceName, { if (it.length <= 40) vm.deviceName = it },
                label = { Text(stringResource(R.string.pair_device_name)) },
                supportingText = { Text(stringResource(R.string.pair_device_name_text)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(onClick = vm::pair, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(if (s.code != null) R.string.pair_action else R.string.pair_action_token))
        }
    }
}

@Composable
private fun SecurityBox(s: PairStep.Confirm) {
    val (icon, color, title, text) = when {
        s.mismatch -> Quad(Icons.Outlined.ErrorOutline, McColors.Offline, R.string.pair_mismatch_title, R.string.pair_mismatch_text)
        !s.tls -> Quad(Icons.Outlined.LockOpen, McColors.Warning, R.string.pair_unencrypted_title, R.string.pair_unencrypted_text)
        s.verified -> Quad(Icons.Outlined.CheckCircle, McColors.Online, R.string.pair_verified_title, R.string.pair_verified_text)
        s.fingerprint != null -> Quad(Icons.Outlined.Shield, MaterialTheme.colorScheme.primary, R.string.pair_compare_title, R.string.pair_compare_text)
        else -> Quad(Icons.Outlined.Shield, McColors.Online, R.string.pair_ca_title, R.string.pair_ca_text)
    }
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = statusTextColor(color))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = statusTextColor(color))
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            if (s.fingerprint != null && !s.verified && !s.mismatch) {
                Spacer(Modifier.height(10.dp))
                Text(
                    Tls.shortFingerprint(s.fingerprint),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

private data class Quad(val icon: ImageVector, val color: Color, val title: Int, val text: Int)

@Composable
private fun ErrorBox(error: Throwable?) {
    if (error == null) return
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Text(context.errorText(error), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Busy(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
