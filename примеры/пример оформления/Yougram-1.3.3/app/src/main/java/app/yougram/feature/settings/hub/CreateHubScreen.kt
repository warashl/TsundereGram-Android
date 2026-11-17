package app.yougram.feature.settings.hub

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.runtime.Composable
import app.yougram.feature.account.data.AccountManager
import app.yougram.feature.settings.SettingsPage
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsPageColumn

/** Вкладка «Создать / Добавить»: новая группа, новый канал, добавление аккаунта. */
@Composable
fun CreateHubScreen(
    accountManager: AccountManager,
    contentPadding: PaddingValues,
    onNavigate: (SettingsPage) -> Unit,
) {
    SettingsPageColumn(contentPadding) {
        SettingGroup {
            item { SettingRow("Создать группу", subtitle = "Новая группа с участниками", icon = Icons.Filled.Group, onClick = { onNavigate(SettingsPage.CreateGroup) }) }
            item { SettingRow("Создать канал", subtitle = "Публичный или приватный", icon = Icons.Filled.Campaign, onClick = { onNavigate(SettingsPage.CreateChannel) }) }
            item { SettingRow("Добавить аккаунт", subtitle = "Войти во второй аккаунт Telegram", icon = Icons.Filled.PersonAdd, onClick = { accountManager.addAndRestart() }) }
        }
    }
}
