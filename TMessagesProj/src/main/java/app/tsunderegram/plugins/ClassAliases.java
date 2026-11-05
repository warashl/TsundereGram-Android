package app.tsunderegram.plugins;

public final class ClassAliases {

    public static final String ROOT = "com.tsunderegram.messenger";

    public static final String MEDIA_ROOT = "com.google.android.exoplayer2";

    private static final String[] ROOTS = {ROOT, MEDIA_ROOT};

    private static final String[][] EXACT = {
            {"com.tsunderegram.messenger.utils.chats.ChatUtils", "com.tsunderegram.messenger.utils.chats.ChatUtils"},
            {"com.tsunderegram.messenger.utils.ChatUtils", "com.tsunderegram.messenger.utils.chats.ChatUtils"},
            {"com.tsunderegram.messenger.utils.text.LocaleUtils", "com.tsunderegram.messenger.utils.text.LocaleUtils"},
            {"com.tsunderegram.messenger.utils.LocaleUtils", "com.tsunderegram.messenger.utils.text.LocaleUtils"},
            {"com.tsunderegram.messenger.utils.AppUtils", "com.tsunderegram.messenger.utils.AppUtils"},
            {"com.tsunderegram.messenger.R", "org.telegram.messenger.R"},
            {"com.tsunderegram.messenger.utils.system.VibratorUtils", "com.tsunderegram.messenger.utils.system.VibratorUtils"},
            {"com.tsunderegram.messenger.ai.AiConfig", "app.tsunderegram.ai.AiConfig"},
            {"com.tsunderegram.messenger.ai.AiController", "com.tsunderegram.messenger.ai.AiController"},
            {"com.tsunderegram.messenger.ai.ui.ResponseAlert", "com.tsunderegram.messenger.ai.ui.ResponseAlert"},
            {"com.tsunderegram.messenger.ai.ui.GenerateFromMessageBottomSheet", "com.tsunderegram.messenger.ai.ui.GenerateFromMessageBottomSheet"},
            {"com.tsunderegram.messenger.plugins.ui.components.InstallPluginBottomSheet", "com.tsunderegram.messenger.plugins.ui.components.InstallPluginBottomSheet"},
            {"com.tsunderegram.messenger.utils.system.SystemUtils", "com.tsunderegram.messenger.utils.system.SystemUtils"},
            {"com.tsunderegram.messenger.utils.SystemUtils", "com.tsunderegram.messenger.utils.system.SystemUtils"},
            {"com.tsunderegram.messenger.preferences.MainPreferencesActivity", "app.tsunderegram.settings.OpenExteraSettingsActivity"},
            {"com.tsunderegram.messenger.preferences.GeneralPreferencesActivity", "app.tsunderegram.settings.OpenExteraGeneralActivity"},
            {"com.tsunderegram.messenger.preferences.AppearancePreferencesActivity", "app.tsunderegram.settings.OpenExteraAppearanceActivity"},
            {"com.tsunderegram.messenger.preferences.ChatsPreferencesActivity", "app.tsunderegram.settings.OpenExteraChatsActivity"},
            {"com.tsunderegram.messenger.preferences.OtherPreferencesActivity", "app.tsunderegram.settings.OpenExteraOtherActivity"},
            {"com.tsunderegram.messenger.preferences.AppNavigationPreferencesActivity", "app.tsunderegram.settings.OpenExteraAppNavigationActivity"},
            {"com.tsunderegram.messenger.preferences.BasePreferencesActivity", "com.tsunderegram.messenger.preferences.BasePreferencesActivity"},
            {"com.tsunderegram.messenger.preferences.components.AltSeekbar", "app.tsunderegram.appearance.AltSeekbar"},
            {"com.tsunderegram.messenger.utils.chats.MainMenuHelper", "app.tsunderegram.drawer.MainMenuHelper"},
            {"com.tsunderegram.messenger.icons.ui.IconPacksActivity", "app.tsunderegram.icons.IconPacksActivity"},
            {"com.tsunderegram.messenger.pillstack.ui.pills.crypto.utils.ColoredBackground", "app.tsunderegram.pillstack.pills.ColoredBackground"},
            {"com.tsunderegram.messenger.pillstack.ui.pills.weather.WeatherPill", "app.tsunderegram.pillstack.pills.WeatherPill"},
            {"com.tsunderegram.messenger.pillstack.ui.PillStackPreferencesActivity", "app.tsunderegram.pillstack.PillStackSettingsActivity"},
            {"com.tsunderegram.messenger.pillstack.ui.PillStackLayout", "app.tsunderegram.pillstack.PillStackView"},
            {"com.tsunderegram.messenger.pillstack.ui.pills.weather.WeatherPreferencesActivity", "app.tsunderegram.pillstack.pills.weather.WeatherSettingsActivity"},
            {"com.tsunderegram.messenger.preferences.appearance.AppearancePreferencesActivity", "app.tsunderegram.settings.OpenExteraAppearanceActivity"},
            {"com.tsunderegram.messenger.nowplaying.ui.components.NowPlayingCard", "app.tsunderegram.components.ProfileMusicCard"},
            {"com.tsunderegram.messenger.nowplaying.NowPlayingController", "app.tsunderegram.nowplaying.NowPlayingController"},
            {"com.tsunderegram.messenger.proxy.ProxyController", "app.tsunderegram.proxy.ProxyController"},
            {"com.tsunderegram.messenger.utils.ui.UIUtil", "app.tsunderegram.utils.UIUtil"},
            {"com.tsunderegram.messenger.utils.ui.MainTabsUiHelper", "app.tsunderegram.appearance.MainTabsUiHelper"},
            {"com.google.android.exoplayer2.util.Consumer", "androidx.media3.common.util.Consumer"},
            {"com.google.android.exoplayer2.video.VideoSize", "androidx.media3.common.VideoSize"},
            {"com.google.android.exoplayer2.PlaybackException", "androidx.media3.common.PlaybackException"},
            {"com.google.android.exoplayer2.PlaybackParameters", "androidx.media3.common.PlaybackParameters"},
            {"com.google.android.exoplayer2.C", "androidx.media3.common.C"},
            {"org.telegram.ui.Gifts.ProfileGiftsContainer$SelectGiftsBottomSheet", "org.telegram.ui.Gifts.SelectGiftsBottomSheet"},
    };

    private static final String[][] PREFIXES = {
            {"com.tsunderegram.messenger.ai.data.", "app.tsunderegram.ai.data."},
            {"com.tsunderegram.messenger.ai.network.", "app.tsunderegram.ai.network."},
            {"com.tsunderegram.messenger.pillstack.core.", "app.tsunderegram.pillstack."},
            {"com.tsunderegram.messenger.pillstack.ui.pills.", "app.tsunderegram.pillstack.pills."},
            {"com.tsunderegram.messenger.pillstack.ui.", "app.tsunderegram.pillstack."},
            {"com.tsunderegram.messenger.preferences.", "app.tsunderegram.settings."},
            {"com.tsunderegram.messenger.plugins.", "app.tsunderegram.plugins."},
            {"com.tsunderegram.messenger.icons.", "app.tsunderegram.icons."},
            {"com.tsunderegram.messenger.camera.", "app.tsunderegram.camera."},
            {"com.tsunderegram.messenger.backup.", "app.tsunderegram.backup."},
            {"com.tsunderegram.messenger.feed.", "app.tsunderegram.feed."},
            {"com.tsunderegram.messenger.drawer.", "app.tsunderegram.drawer."},
            {"com.tsunderegram.messenger.components.", "app.tsunderegram.components."},
            {"com.tsunderegram.messenger.utils.", "app.tsunderegram.utils."},
    };

    private ClassAliases() {
    }

    public static String resolve(String name) {
        if (name == null) {
            return null;
        }
        String exact = lookup(name);
        if (exact != null) {
            return exact;
        }
        if (!underRoot(name)) {
            return name;
        }
        int dollar = name.indexOf('$');
        String outer = dollar < 0 ? name : name.substring(0, dollar);
        String nested = dollar < 0 ? "" : name.substring(dollar);
        exact = lookup(outer);
        if (exact != null) {
            return exact + nested;
        }
        for (String[] pair : PREFIXES) {
            if (outer.startsWith(pair[0])) {
                return pair[1] + outer.substring(pair[0].length()) + nested;
            }
        }
        return name;
    }

    private static boolean underRoot(String name) {
        for (String root : ROOTS) {
            if (name.startsWith(root + ".")) {
                return true;
            }
        }
        return false;
    }

    private static String lookup(String name) {
        for (String[] pair : EXACT) {
            if (pair[0].equals(name)) {
                return pair[1];
            }
        }
        return null;
    }
}
