package app.yougram.feature.settings.general

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.yougram.core.settings.SettingsRepository
import app.yougram.core.settings.VideoCameraChoice
import app.yougram.feature.settings.component.CheckSwitch
import app.yougram.feature.settings.component.ChoiceDialog
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import app.yougram.feature.settings.component.SwitchRow

/** Настройки камеры для видеосообщений: выбор камеры, запоминание, слайдер и «статичный» зум. */
@Composable
fun CameraSettingsScreen(settings: SettingsRepository, contentPadding: PaddingValues) {
    val prefs by settings.camera.collectAsState()
    var expanded by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf(false) }

    // Сколько из пяти опций включено: тип камеры всегда активен.
    val enabled = listOf(true, prefs.advanced, prefs.rememberLast, prefs.zoomSlider, prefs.staticZoom).count { it }

    SettingsPageColumn(contentPadding) {
        SettingGroup {
            item {
                SettingRow(
                    "Тип камеры",
                    value = "Camera X",
                    icon = Icons.Filled.CameraAlt,
                )
            }
            item {
                SettingRow(
                    "Расширенные настройки",
                    value = "$enabled/5",
                    icon = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    onClick = { expanded = !expanded },
                    trailing = {
                        CheckSwitch(prefs.advanced) { v -> settings.updateCamera { it.copy(advanced = v) } }
                    },
                )
            }
            if (expanded) {
                item {
                    SettingRow(
                        "Камера в видеосообщениях",
                        value = prefs.videoCamera.label,
                        onClick = { dialog = true },
                    )
                }
                item {
                    SwitchRow(
                        "Запоминать последнюю камеру",
                        prefs.rememberLast,
                        { v -> settings.updateCamera { it.copy(rememberLast = v) } },
                        subtitle = "При записи видеосообщений будет автоматически выбрана последняя использованная камера.",
                    )
                }
                item {
                    SwitchRow(
                        "Слайдер зума",
                        prefs.zoomSlider,
                        { v -> settings.updateCamera { it.copy(zoomSlider = v) } },
                        subtitle = "Показывать регулятор приближения под камерой видеосообщений.",
                    )
                }
                item {
                    SwitchRow(
                        "Статичный зум",
                        prefs.staticZoom,
                        { v -> settings.updateCamera { it.copy(staticZoom = v) } },
                    )
                }
            }
        }
        SettingsFootnote(
            if (prefs.staticZoom) {
                "При записи видеосообщений уровень приближения не будет сбрасываться, если отпустить пальцы."
            } else {
                "При записи видеосообщений уровень приближения будет сбрасываться, когда вы отпустите слайдер."
            },
        )
        if (!prefs.advanced) {
            SettingsFootnote("Расширенные настройки выключены: запись идёт с камерой по умолчанию и без слайдера зума.")
        }
    }

    if (dialog) {
        ChoiceDialog(
            title = "Камера в видеосообщениях",
            options = VideoCameraChoice.entries,
            selected = prefs.videoCamera,
            label = { it.label },
            onSelect = { v ->
                settings.updateCamera { it.copy(videoCamera = v) }
                dialog = false
            },
            onDismiss = { dialog = false },
        )
    }
}