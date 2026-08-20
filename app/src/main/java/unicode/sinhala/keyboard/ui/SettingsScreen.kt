package unicode.sinhala.keyboard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import unicode.sinhala.com.BuildConfig
import unicode.sinhala.com.R
import unicode.sinhala.keyboard.CustomFontManager
import unicode.sinhala.keyboard.DonateActivity
import unicode.sinhala.keyboard.EmojiDownloader
import unicode.sinhala.keyboard.EmojiStyle
import unicode.sinhala.keyboard.Prefs
import unicode.sinhala.keyboard.ui.components.PreferenceItem
import unicode.sinhala.keyboard.ui.components.RadioOptionPreference
import unicode.sinhala.keyboard.ui.components.SettingsCategory
import unicode.sinhala.keyboard.ui.components.SliderPreference
import unicode.sinhala.keyboard.ui.components.SwitchPreference

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        SettingsCategory(title = "Languages")

        val layoutEnglish = rememberBooleanPreference(context, "layout_english", true)
        SwitchPreference(
            title = "English",
            checked = layoutEnglish.value,
            onCheckedChange = { layoutEnglish.value = it }
        )

        val layoutWijesekara = rememberBooleanPreference(context, "layout_wijesekara", true)
        SwitchPreference(
            title = stringResource(R.string.wijesekara),
            checked = layoutWijesekara.value,
            onCheckedChange = { layoutWijesekara.value = it }
        )

        val layoutSinglish = rememberBooleanPreference(context, "layout_singlish", true)
        SwitchPreference(
            title = stringResource(R.string.singlish),
            checked = layoutSinglish.value,
            onCheckedChange = { layoutSinglish.value = it }
        )

        SettingsCategory(title = "Appearance")

        val automaticTheme = rememberBooleanPreference(context, "automatic_theme", true)
        SwitchPreference(
            title = "ස්වයංක්‍රීය තේමාව",
            checked = automaticTheme.value,
            onCheckedChange = { automaticTheme.value = it }
        )

        val darkTheme = rememberBooleanPreference(context, "dark_theme", false)
        if (!automaticTheme.value) {
            SwitchPreference(
                title = "අඳුරු වර්ණ",
                checked = darkTheme.value,
                onCheckedChange = { darkTheme.value = it }
            )
        }

        val keyBorders = rememberBooleanPreference(context, "key_borders", true)
        SwitchPreference(
            title = "යතුරු මායිම්",
            checked = keyBorders.value,
            onCheckedChange = { keyBorders.value = it }
        )

        SettingsCategory(title = "Layout")

        val showNumberRow = rememberBooleanPreference(context, "show_number_row", true)
        SwitchPreference(
            title = "අංක පේළිය පෙන්වන්න",
            checked = showNumberRow.value,
            onCheckedChange = { showNumberRow.value = it }
        )

        val heightPercentage = rememberIntPreference(context, "height_percentage", 100)
        SliderPreference(
            title = "උස",
            value = heightPercentage.value,
            range = 70f..190f,
            onValueChange = { heightPercentage.value = it }
        )

        val textSize = rememberIntPreference(context, "text_size", 28)
        SliderPreference(
            title = "අකුරුවල ප්‍රමාණය",
            value = textSize.value,
            range = 20f..40f,
            onValueChange = { textSize.value = it }
        )

        SettingsCategory(title = "Emoji")

        val showRecentEmojiRow = rememberBooleanPreference(context, "show_recent_emoji_row", false)
        SwitchPreference(
            title = "පසුගිය ඉමෝජි පේළිය පෙන්වන්න",
            checked = showRecentEmojiRow.value,
            onCheckedChange = { showRecentEmojiRow.value = it }
        )

        EmojiStyleSection()

        SettingsCategory(title = "Support")

        PreferenceItem(
            title = "Buy Me a Coffee",
            summary = "Support development",
            onClick = {
                context.startActivity(Intent(context, DonateActivity::class.java))
            }
        )

        PreferenceItem(
            title = "Source Code",
            summary = "View on GitHub",
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/xzunk/Foxkeyboard"))
                context.startActivity(intent)
            }
        )

        PreferenceItem(
            title = "Version",
            summary = BuildConfig.VERSION_NAME
        )
    }
}

/**
 * Lets the user pick between the phone's built-in ("Mobile") emoji and an optional
 * downloadable, flat/colorful "iOS-style" pack (Twemoji - open-source, CC-BY 4.0).
 * This is NOT Apple's own emoji artwork, which is proprietary and can't legally be
 * bundled or redistributed; Twemoji is the closest legally-usable look-alike.
 */
@Composable
private fun EmojiStyleSection() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val scope = rememberCoroutineScope()

    var selectedStyle by remember { mutableStateOf(prefs.emojiStyle) }
    var downloaded by remember { mutableStateOf(prefs.twemojiDownloaded) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    var downloadFailed by remember { mutableStateOf(false) }
    var hasCustomFont by remember { mutableStateOf(CustomFontManager.hasCustomFont(context)) }
    var customFontImportFailed by remember { mutableStateOf(false) }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = CustomFontManager.importFont(context, uri)
        hasCustomFont = ok
        customFontImportFailed = !ok
        if (ok) {
            selectedStyle = EmojiStyle.CUSTOM
            prefs.emojiStyle = EmojiStyle.CUSTOM
        }
    }

    SettingsCategory(title = "Emoji Style")

    RadioOptionPreference(
        title = EmojiStyle.SYSTEM.displayName,
        summary = "Uses your phone's own emoji - always available, no download",
        selected = selectedStyle == EmojiStyle.SYSTEM,
        onClick = {
            selectedStyle = EmojiStyle.SYSTEM
            prefs.emojiStyle = EmojiStyle.SYSTEM
        }
    )

    RadioOptionPreference(
        title = EmojiStyle.TWEMOJI.displayName,
        summary = when {
            isDownloading -> "Downloading..."
            downloaded -> "Downloaded - flat, colorful look"
            else -> "Free, open-source pack (~3-5 MB, one-time download)"
        },
        selected = selectedStyle == EmojiStyle.TWEMOJI,
        onClick = {
            if (downloaded) {
                selectedStyle = EmojiStyle.TWEMOJI
                prefs.emojiStyle = EmojiStyle.TWEMOJI
            } else if (!isDownloading) {
                downloadFailed = false
                showConfirmDialog = true
            }
        }
    )

    if (isDownloading) {
        val (done, total) = progress
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            LinearProgressIndicator(
                progress = { if (total > 0) done.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "$done / $total",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    if (downloadFailed) {
        Text(
            text = "Download didn't finish - check your internet connection and try again.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }

    RadioOptionPreference(
        title = EmojiStyle.CUSTOM.displayName,
        summary = if (hasCustomFont) "Using: ${CustomFontManager.fontFile(context).name}" else "Pick a .ttf/.otf font already saved on your device",
        selected = selectedStyle == EmojiStyle.CUSTOM,
        onClick = {
            if (hasCustomFont) {
                selectedStyle = EmojiStyle.CUSTOM
                prefs.emojiStyle = EmojiStyle.CUSTOM
            } else {
                fontPickerLauncher.launch(arrayOf("font/ttf", "font/otf", "font/collection", "application/x-font-ttf", "*/*"))
            }
        }
    )

    if (selectedStyle == EmojiStyle.CUSTOM || hasCustomFont) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            OutlinedButton(onClick = {
                fontPickerLauncher.launch(arrayOf("font/ttf", "font/otf", "font/collection", "application/x-font-ttf", "*/*"))
            }) {
                Text(if (hasCustomFont) "Change Font File" else "Choose Font File")
            }
            if (hasCustomFont) {
                Spacer(modifier = Modifier.padding(start = 8.dp))
                TextButton(onClick = {
                    CustomFontManager.removeFont(context)
                    hasCustomFont = false
                    if (selectedStyle == EmojiStyle.CUSTOM) {
                        selectedStyle = EmojiStyle.SYSTEM
                        prefs.emojiStyle = EmojiStyle.SYSTEM
                    }
                }) { Text("Remove") }
            }
        }
        Text(
            text = "Note: fonts extracted from iOS use a color-glyph format Android often can't " +
                "render correctly - you may see blank boxes for some emoji. Works best with " +
                "Android-compatible emoji fonts (COLR/CBDT format).",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, bottom = 4.dp)
        )
        if (customFontImportFailed) {
            Text(
                text = "Couldn't read that file as a font. Please pick a valid .ttf/.otf file.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, bottom = 4.dp)
            )
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Download emoji pack?") },
            text = {
                Text(
                    "This downloads Twemoji, a free open-source emoji set (not Apple's own " +
                        "emoji, which can't legally be bundled). It gives a flat, colorful, " +
                        "iOS-like look. Needs an internet connection; downloads once and is " +
                        "then cached for offline use."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    isDownloading = true
                    progress = 0 to 0
                    scope.launch {
                        val success = EmojiDownloader.downloadTwemojiPack(context) { done, total ->
                            progress = done to total
                        }
                        isDownloading = false
                        if (success) {
                            downloaded = true
                            prefs.twemojiDownloaded = true
                            selectedStyle = EmojiStyle.TWEMOJI
                            prefs.emojiStyle = EmojiStyle.TWEMOJI
                        } else {
                            downloadFailed = true
                        }
                    }
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun rememberBooleanPreference(context: Context, key: String, defaultValue: Boolean): MutableState<Boolean> {
    val prefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    val state = remember { mutableStateOf(prefs.getBoolean(key, defaultValue)) }

    DisposableEffect(key) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, k ->
            if (k == key) {
                state.value = sharedPreferences.getBoolean(key, defaultValue)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return remember(state) {
        object : MutableState<Boolean> {
            override var value: Boolean
                get() = state.value
                set(newValue) {
                    state.value = newValue
                    prefs.edit().putBoolean(key, newValue).apply()
                }

            override fun component1() = value
            override fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberIntPreference(context: Context, key: String, defaultValue: Int): MutableState<Int> {
    val prefs = remember { context.getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    val state = remember { mutableStateOf(prefs.getInt(key, defaultValue)) }

    DisposableEffect(key) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, k ->
            if (k == key) {
                state.value = sharedPreferences.getInt(key, defaultValue)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return remember(state) {
        object : MutableState<Int> {
            override var value: Int
                get() = state.value
                set(newValue) {
                    state.value = newValue
                    prefs.edit().putInt(key, newValue).apply()
                }

            override fun component1() = value
            override fun component2(): (Int) -> Unit = { value = it }
        }
    }
}
