package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.ProfileActivity.sendLogs;

import android.app.Activity;
import android.net.Uri;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LiteModeSettingsActivity;
import org.telegram.ui.PrivacySettingsActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import app.tsunderegram.ai.ui.AiSettingsActivity;
import app.tsunderegram.icons.IconPacksActivity;
import app.tsunderegram.pillstack.pills.weather.WeatherSettingsActivity;
import app.tsunderegram.settings.OpenExteraCloudActivity;
import app.tsunderegram.settings.OpenExteraGlyphActivity;
import app.tsunderegram.pillstack.PillStackSettingsActivity;
import app.tsunderegram.plugins.ui.PluginsActivity;
import app.tsunderegram.settings.OpenExteraAppNavigationActivity;
import app.tsunderegram.settings.OpenExteraAppearanceActivity;
import app.tsunderegram.settings.OpenExteraAyuMomentsActivity;
import app.tsunderegram.settings.OpenExteraChatsActivity;
import app.tsunderegram.settings.OpenExteraGeneralActivity;
import app.tsunderegram.settings.OpenExteraOtherActivity;
import app.tsunderegram.settings.OpenExteraSettingsActivity;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.settings.BaseNekoXSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoAboutActivity;
import tw.nekomimi.nekogram.settings.NekoEmojiSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoPasscodeSettingsActivity;
import tw.nekomimi.nekogram.settings.NekoTranslatorSettingsActivity;

public class SettingsHelper {

    private static final String HOST_NAGRAM = "nasettings";
    private static final String HOST_TSUNDEREGRAM = "tsunderegram";
    /** Публичный префикс ссылок на настройки (старый HOST_TSUNDEREGRAM по-прежнему принимается). */
    private static final String HOST_TSUNDERE = "tsunderegram";

    private static final Map<String, String> SEARCH_TITLE_ALIASES = new HashMap<>();
    private static final Map<String, String> TRANSLATOR_ROWS = new HashMap<>();
    private static final Map<String, String> NAGRAM_ROWS = new HashMap<>();
    private static final Map<String, String> MOVED_ROWS = new HashMap<>();

    static {
        SEARCH_TITLE_ALIASES.put("OEGeneral:botPrivacyAlwaysAsk", "OEBotPrivacyAlwaysAsk");
        SEARCH_TITLE_ALIASES.put("OEGeneral:botPrivacyEnabled", "OEBotPrivacyEnabled");
        SEARCH_TITLE_ALIASES.put("OEGeneral:botPrivacyAccount", "OEBotPrivacyAccount");
        SEARCH_TITLE_ALIASES.put("OEAyu:ayuGhost", "GhostMode");
        SEARCH_TITLE_ALIASES.put("OEAyu:ayuDisableAll", "OEGeneralAyuMomentsDisableAll");
        SEARCH_TITLE_ALIASES.put("OEAyu:ayuClearDatabase", "ClearMessageDatabase");
        SEARCH_TITLE_ALIASES.put("OEAppearance:appNavigation", "OEAppearanceNavigation");
        SEARCH_TITLE_ALIASES.put("OEChats:hideStories", "OEAppearanceStories");
        SEARCH_TITLE_ALIASES.put("OEChats:hideActionBarStatus", "OEAppearanceHideActionBarStatus");
        SEARCH_TITLE_ALIASES.put("OEChats:hideFloatingButton", "OEAppearanceHideFloatingButton");
        SEARCH_TITLE_ALIASES.put("OEChats:hideSearchBar", "OEAppearanceHideSearchBar");
        SEARCH_TITLE_ALIASES.put("OEChats:hideAllChats", "HideAllTab");
        SEARCH_TITLE_ALIASES.put("OEChats:hideShareButton", "HideShareButtonInChannel");
        SEARCH_TITLE_ALIASES.put("OEChats:hideAi", "OEAppearanceHideAi");
        SEARCH_TITLE_ALIASES.put("OEChats:hideAiEditor", "OEAppearanceHideAiEditor");
        SEARCH_TITLE_ALIASES.put("OEChats:hideAiSummary", "OEAppearanceHideAiSummary");
        SEARCH_TITLE_ALIASES.put("OEChats:hideAiIv", "OEAppearanceHideAiIv");
        SEARCH_TITLE_ALIASES.put("OEChats:hideSettingsSections", "OEAppearanceHideSettingsSections");
        SEARCH_TITLE_ALIASES.put("OEChats:disableGreeting", "OEChatsDisableGreetingSticker");
        SEARCH_TITLE_ALIASES.put("OEChats:hideKeyboardOnScroll", "HideKeyboardOnChatScroll");
        SEARCH_TITLE_ALIASES.put("OEChats:transcribeProvider", "PremiumPreviewVoiceToText");
        SEARCH_TITLE_ALIASES.put("OEGeneral:translator", "TranslatorSettings");
        SEARCH_TITLE_ALIASES.put("OEGeneral:translateInSheet", "OEChatsTranslateInSheet");
        SEARCH_TITLE_ALIASES.put("OEGeneral:glyph", "OEGlyphTitle");
        SEARCH_TITLE_ALIASES.put("OpenExtera:channel", "ProfileChannel");
        SEARCH_TITLE_ALIASES.put("OEGeneral:downloadSpeed", "OEGeneralSpeedHeader");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3Styles", "OEAppearanceMaterialDesign3");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3Loading", "OEAppearanceNewLoadingStyle");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3Slider", "OEAppearanceSliderStyle");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3Switch", "OEAppearanceSwitchStyle");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3NavBar", "OEAppearanceNewNavigationBarStyle");
        SEARCH_TITLE_ALIASES.put("OEAppearance:md3ListItems", "OEAppearanceM3ListItems");
        SEARCH_TITLE_ALIASES.put("OEAppearance:iosStyles", "OEAppearanceIosDesign");
        SEARCH_TITLE_ALIASES.put("OEAppearance:iosNavBar", "OEAppearanceIosNavigationBarStyle");
        SEARCH_TITLE_ALIASES.put("OEChats:doubleTapReaction", "DoubleTapSetting");
        SEARCH_TITLE_ALIASES.put("OEChats:quickTransition", "OEChatsQuickTransitions");
        SEARCH_TITLE_ALIASES.put("OEChats:inlineMathResult", "OEChatsInlineMath");
        SEARCH_TITLE_ALIASES.put("OEChats:searchHashtagChat", "SearchHashtagDefaultPageChat");
        SEARCH_TITLE_ALIASES.put("OEChats:searchHashtagChannel", "SearchHashtagDefaultPageChannel");
        SEARCH_TITLE_ALIASES.put("OEChats:replaceEdited", "OEChatsReplaceEditedWithIcon");
        SEARCH_TITLE_ALIASES.put("OEChats:videoMessagesCamera", "CameraInVideoMessages");
        SEARCH_TITLE_ALIASES.put("OEChats:voskModels", "VoskModelsShort");
        MOVED_ROWS.put("chats:translateInSheet", "general");
        MOVED_ROWS.put("other:unlimitedPinnedDialogs", "general");
        MOVED_ROWS.put("other:glyph", "general");
        moveRows("chats", "menus", "bottomButton", "adminShortcuts", "chatMenu", "messageMenu",
                "groupedMessageMenu", "textStyle", "TextStyle", "mediaViewerMenu", "actionBarButtons",
                "defaultDeleteMenu", "DefaultDeleteMenu");
        moveRows("chats", "media", "cameraType", "extendedSettings", "videoMessagesCamera",
                "rememberLastUsedCamera", "zoomSlider", "staticZoom", "alwaysSendInHD", "hdrPhotos",
                "disableInstantCamera", "DisableInstantCamera", "doubleTapSeekDuration", "preferOriginalQuality",
                "videoPlayerDecoder", "VideoPlayerDecoder", "swipeToPip", "unmuteWithVolumeButtons",
                "showSmallGIF", "ShowSmallGIF", "dontAutoPlayNextVoice", "DontAutoPlayNextVoice",
                "disableProximityEvents", "DisableProximityEvents", "pauseOnMinimize", "transcribeProvider",
                "TranscribeProviderShort", "cloudflareCredentials", "CloudflareCredentials", "llmProviderGeminiKey",
                "LlmProviderGeminiKey", "transcribeProviderOpenAI", "TranscribeProviderOpenAI", "voskModels",
                "VoskModelsShort");
        moveRows("chats", "hiding", "disableTrending", "DisableTrending", "hideGroupSticker", "hideReactions",
                "disableGreeting", "hideSendAsPeer", "hideShareButton", "hideGiftButton", "hideSearchButton",
                "hideCameraTile", "premiumElements", "PremiumElements");
        moveRows("other", "media", "noiseSuppressAndVoiceEnhance", "NoiseSuppressAndVoiceEnhance",
                "enhancedVideoBitrate", "EnhancedVideoBitrate");
        moveRows("appearance", "hiding", "hideActionBarStatus", "hideStories", "HideStoriesFromHeader",
                "DisableStories", "hideFloatingButton", "hideSearchBar", "hideAllChats", "hideAi", "hideAiEditor",
                "hideAiSummary", "hideAiIv", "hideSettingsSections", "hidePremiumSection", "HidePremiumSection",
                "hideHelpSection", "HideHelpSection");
        TRANSLATOR_ROWS.put("translateButton", "showTranslate");
        TRANSLATOR_ROWS.put("translateChatButton", "TelegramUIAutoTranslate");
        TRANSLATOR_ROWS.put("translationProvider", "translationProvider");
        TRANSLATOR_ROWS.put("translateToLang", "TranslateTo");
        TRANSLATOR_ROWS.put("doNotTranslate", "DoNotTranslate");
        NAGRAM_ROWS.put("HidePremiumSection", "appearance");
        NAGRAM_ROWS.put("HideHelpSection", "appearance");
        NAGRAM_ROWS.put("HideStoriesFromHeader", "appearance");
        NAGRAM_ROWS.put("DisableStories", "appearance");
        NAGRAM_ROWS.put("MainTabsHideTitles", "navigation");
        NAGRAM_ROWS.put("MainTabsHideContacts", "navigation");
        NAGRAM_ROWS.put("MainTabsHideCallsSettings", "navigation");
        NAGRAM_ROWS.put("MainTabsHideProfile", "navigation");
        NAGRAM_ROWS.put("DisableSystemAccount", "privacy");
        NAGRAM_ROWS.put("PerformanceClass", "powersaving");
        NAGRAM_ROWS.put("VideoPlayerDecoder", "chats");
    }

    private static void moveRows(String from, String to, String... rows) {
        for (String row : rows) {
            MOVED_ROWS.put(from + ":" + row, to);
        }
    }

    private static final Set<String> TSUNDEREGRAM_SCREENS = new HashSet<>(Arrays.asList(
            "settings", "general", "appearance", "chats", "plugins", "pillstack", "other",
            "ayumoments", "navigation", "menus", "media", "hiding"));

    public static boolean isDeepLink(String path) {
        if (path == null) {
            return false;
        }
        if (path.startsWith(HOST_NAGRAM + "/")) {
            return true;
        }
        if (path.startsWith(HOST_TSUNDERE + "/")) {
            return TSUNDEREGRAM_SCREENS.contains(path.substring(HOST_TSUNDERE.length() + 1));
        }
        if (!path.startsWith(HOST_TSUNDEREGRAM + "/")) {
            return false;
        }
        return TSUNDEREGRAM_SCREENS.contains(path.substring(HOST_TSUNDEREGRAM.length() + 1));
    }

    public static String linkPathFor(String key) {
        if (key == null) {
            return null;
        }
        switch (key) {
            case "tsunderegram":
                return HOST_TSUNDERE + "/settings";
            case "tsunderegram_general":
                return HOST_TSUNDERE + "/general";
            case "tsunderegram_appearance":
                return HOST_TSUNDERE + "/appearance";
            case "tsunderegram_chats":
                return HOST_TSUNDERE + "/chats";
            case "tsunderegram_menus":
                return HOST_TSUNDERE + "/menus";
            case "tsunderegram_media":
                return HOST_TSUNDERE + "/media";
            case "tsunderegram_hiding":
                return HOST_TSUNDERE + "/hiding";
            case "tsunderegram_other":
                return HOST_TSUNDERE + "/other";
            case "tsunderegram_ayumoments":
                return HOST_TSUNDERE + "/ayumoments";
            case "pillstack":
                return HOST_TSUNDERE + "/pillstack";
            case "tsunderegram_navigation":
                return HOST_TSUNDERE + "/navigation";
            default:
                if (key.startsWith("tsunderegram_")) {
                    return HOST_NAGRAM + "/tsundere_" + key.substring("tsunderegram_".length());
                }
                return HOST_NAGRAM + "/" + key;
        }
    }

    public static void processDeepLink(Activity activity, Uri uri, Callback callback, Runnable unknown) {
        if (uri == null) {
            unknown.run();
            return;
        }
        var segments = uri.getPathSegments();
        if (segments.isEmpty() || segments.size() > 2) {
            unknown.run();
            return;
        }
        final boolean tsunderegram = HOST_TSUNDEREGRAM.equals(segments.get(0)) || HOST_TSUNDERE.equals(segments.get(0));
        final String seg1 = segments.size() > 1 ? normalizeKey(segments.get(1)) : null;
        if (!tsunderegram && !HOST_NAGRAM.equals(segments.get(0))) {
            unknown.run();
            return;
        }
        if (tsunderegram && segments.size() < 2) {
            unknown.run();
            return;
        }
        var row = uri.getQueryParameter("r");
        if (TextUtils.isEmpty(row)) {
            row = uri.getQueryParameter("row");
        }
        BaseFragment fragment;
        BaseNekoSettingsActivity neko_fragment = null;
        BaseNekoXSettingsActivity nekox_fragment = null;
        OpenExteraAppNavigationActivity navigation_fragment = null;
        String screen = tsunderegram ? segments.get(1) : nagramScreen(seg1, row);
        if (screen == null && segments.size() == 2 && seg1.startsWith("tsunderegram_")) {
            String base = seg1.substring("tsunderegram_".length());
            if (row != null && MOVED_ROWS.containsKey(base + ":" + row)) {
                screen = base;
            }
        }
        if (screen != null && row != null) {
            String moved = MOVED_ROWS.get(screen + ":" + row);
            if (moved != null) {
                screen = moved;
            }
        }
        if (screen != null) {
            switch (screen) {
                case "settings":
                    fragment = neko_fragment = new OpenExteraSettingsActivity();
                    break;
                case "general":
                    if (TRANSLATOR_ROWS.containsKey(row)) {
                        fragment = nekox_fragment = new NekoTranslatorSettingsActivity();
                        row = TRANSLATOR_ROWS.get(row);
                    } else {
                        fragment = neko_fragment = new OpenExteraGeneralActivity();
                    }
                    break;
                case "appearance":
                    fragment = neko_fragment = "useSystemEmoji".equals(row)
                            ? new NekoEmojiSettingsActivity() : new OpenExteraAppearanceActivity();
                    break;
                case "chats":
                    fragment = neko_fragment = new OpenExteraChatsActivity();
                    break;
                case "menus":
                    fragment = neko_fragment = new OpenExteraChatsActivity(OpenExteraChatsActivity.SCREEN_MENUS);
                    break;
                case "media":
                    fragment = neko_fragment = new OpenExteraChatsActivity(OpenExteraChatsActivity.SCREEN_MEDIA);
                    break;
                case "hiding":
                    fragment = neko_fragment = new OpenExteraChatsActivity(OpenExteraChatsActivity.SCREEN_HIDING);
                    break;
                case "other":
                    fragment = neko_fragment = new OpenExteraOtherActivity();
                    break;
                case "ayumoments":
                    fragment = neko_fragment = new OpenExteraAyuMomentsActivity();
                    break;
                case "pillstack":
                    fragment = neko_fragment = new PillStackSettingsActivity();
                    break;
                case "plugins":
                    fragment = new PluginsActivity();
                    break;
                case "navigation":
                    fragment = navigation_fragment = new OpenExteraAppNavigationActivity();
                    break;
                case "privacy":
                    fragment = new PrivacySettingsActivity();
                    break;
                case "powersaving":
                    fragment = new LiteModeSettingsActivity();
                    break;
                default:
                    unknown.run();
                    return;
            }
        } else if (PasscodeHelper.getSettingsKey().equals(seg1)) {
            fragment = neko_fragment = new NekoPasscodeSettingsActivity();
        } else {
            switch (seg1) {
                case "about":
                    fragment = new NekoAboutActivity();
                    break;
                case "emoji":
                    fragment = neko_fragment = new NekoEmojiSettingsActivity();
                    break;
                case "translator":
                case "translate":
                case "t":
                    fragment = nekox_fragment = new NekoTranslatorSettingsActivity();
                    break;
                case "tsunderegram":
                    fragment = neko_fragment = new OpenExteraSettingsActivity();
                    break;
                case "tsunderegram_general":
                    if (TRANSLATOR_ROWS.containsKey(row)) {
                        fragment = nekox_fragment = new NekoTranslatorSettingsActivity();
                        row = TRANSLATOR_ROWS.get(row);
                    } else {
                        fragment = neko_fragment = new OpenExteraGeneralActivity();
                    }
                    break;
                case "tsunderegram_appearance":
                    fragment = neko_fragment = "useSystemEmoji".equals(row)
                            ? new NekoEmojiSettingsActivity() : new OpenExteraAppearanceActivity();
                    break;
                case "tsunderegram_chats":
                    fragment = neko_fragment = new OpenExteraChatsActivity();
                    break;
                case "tsunderegram_other":
                    fragment = neko_fragment = new OpenExteraOtherActivity();
                    break;
                case "tsunderegram_ayumoments":
                    fragment = neko_fragment = new OpenExteraAyuMomentsActivity();
                    break;
                case "pillstack":
                    fragment = neko_fragment = new PillStackSettingsActivity();
                    break;
                case "pillstackweather":
                    fragment = neko_fragment = new WeatherSettingsActivity();
                    break;
                case "iconpacks":
                    fragment = neko_fragment = new IconPacksActivity();
                    break;
                case "tsunderegram_ai":
                    fragment = neko_fragment = new AiSettingsActivity();
                    break;
                case "tsunderegram_cloud":
                    fragment = neko_fragment = new OpenExteraCloudActivity();
                    break;
                case "tsunderegram_glyph":
                    fragment = neko_fragment = new OpenExteraGlyphActivity();
                    break;
                case "send_logs":
                    sendLogs(activity, false);
                    return;
                default:
                    unknown.run();
                    return;
            }
        }
        callback.presentFragment(fragment);
        var value = uri.getQueryParameter("v");
        if (TextUtils.isEmpty(value)) {
            value = uri.getQueryParameter("value");
        }
        if (!TextUtils.isEmpty(row)) {
            var rowFinal = row;
            if (neko_fragment != null) {
                BaseNekoSettingsActivity finalNeko_fragment = neko_fragment;
                AndroidUtilities.runOnUIThread(() -> finalNeko_fragment.scrollToRow(rowFinal, unknown));
            } else if (navigation_fragment != null) {
                OpenExteraAppNavigationActivity finalNavigation_fragment = navigation_fragment;
                AndroidUtilities.runOnUIThread(() -> finalNavigation_fragment.scrollToRow(rowFinal, unknown));
            } else if (nekox_fragment != null) {
                BaseNekoXSettingsActivity finalNekoX_fragment = nekox_fragment;
                if (!TextUtils.isEmpty(value)) {
                    String finalValue = value;
                    AndroidUtilities.runOnUIThread(() -> finalNekoX_fragment.importToRow(rowFinal, finalValue, unknown));
                } else {
                    AndroidUtilities.runOnUIThread(() -> finalNekoX_fragment.scrollToRow(rowFinal, unknown));
                }
            }
        }
    }

    /** «tsundere_xxx» (новые ссылки) → внутренний ключ «tsunderegram_xxx». */
    private static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        if ("tsundere".equals(key)) {
            return "tsunderegram";
        }
        if (key.startsWith("tsundere_")) {
            return "tsunderegram_" + key.substring("tsundere_".length());
        }
        return key;
    }

    private static String nagramScreen(String segment, String row) {
        if (segment == null) {
            return "settings";
        }
        String fallback;
        switch (segment) {
            case "general":
            case "g":
                fallback = "general";
                break;
            case "chat":
            case "chats":
            case "c":
                fallback = "chats";
                break;
            case "experimental":
            case "e":
                fallback = "other";
                break;
            default:
                return null;
        }
        String moved = row == null ? null : NAGRAM_ROWS.get(row);
        return moved != null ? moved : fallback;
    }

    private static String rowTitle(BaseNekoSettingsActivity fragment, String key) {
        String title = getString(key);
        if (title != null && !title.isEmpty() && !title.equals(key)) {
            return title;
        }
        if (key.isEmpty()) {
            return null;
        }
        String capitalized = Character.toUpperCase(key.charAt(0)) + key.substring(1);
        String prefix = fragment.getSearchPrefix();
        if (prefix != null) {
            String alias = SEARCH_TITLE_ALIASES.get(prefix + ":" + key);
            if (alias != null) {
                title = resolved(alias);
                if (title != null) {
                    return title;
                }
            }
            title = resolved(prefix + capitalized);
            if (title != null) {
                return title;
            }
        }
        return resolved(capitalized);
    }

    private static String resolved(String name) {
        if (LocaleController.getStringResId(name) == 0) {
            return null;
        }
        String value = getString(name);
        return value == null || value.isEmpty() ? null : value;
    }

    public interface Callback {
        void presentFragment(BaseFragment fragment);
    }

    public static ArrayList<SettingsSearchResult> onCreateSearchArray(Callback callback) {
        ArrayList<SettingsSearchResult> items = new ArrayList<>();
        ArrayList<BaseNekoXSettingsActivity> fragments = new ArrayList<>();
        fragments.add(new NekoTranslatorSettingsActivity());

        ArrayList<BaseNekoSettingsActivity> tsunderegramFragments = new ArrayList<>();
        tsunderegramFragments.add(new OpenExteraSettingsActivity());
        tsunderegramFragments.add(new OpenExteraGeneralActivity());
        tsunderegramFragments.add(new OpenExteraAppearanceActivity());
        tsunderegramFragments.add(new OpenExteraChatsActivity());
        tsunderegramFragments.add(new OpenExteraChatsActivity(OpenExteraChatsActivity.SCREEN_MENUS));
        tsunderegramFragments.add(new OpenExteraChatsActivity(OpenExteraChatsActivity.SCREEN_MEDIA));
        tsunderegramFragments.add(new OpenExteraChatsActivity(OpenExteraChatsActivity.SCREEN_HIDING));
        tsunderegramFragments.add(new OpenExteraOtherActivity());
        tsunderegramFragments.add(new OpenExteraAyuMomentsActivity());
        tsunderegramFragments.add(new PillStackSettingsActivity());
        tsunderegramFragments.add(new AiSettingsActivity());

        String e_title = getString(R.string.OpenExtera);
        for (BaseNekoSettingsActivity fragment : tsunderegramFragments) {
            try {
                fragment.buildRowsForSearch();
            } catch (Exception e) {
                continue;
            }
            int uid = fragment.getSearchGuid();
            int drawable = fragment.getSearchIcon();
            String f_title = fragment.getSearchTitle();
            for (Map.Entry<Integer, String> entry : fragment.getSearchRows().entrySet()) {
                String key = entry.getValue();
                if (key == null || key.endsWith("Header") || key.equals(String.valueOf(entry.getKey()))) {
                    continue;
                }
                String title = rowTitle(fragment, key);
                if (title == null || title.isEmpty()) {
                    continue;
                }
                Runnable open = () -> {
                    callback.presentFragment(fragment);
                    AndroidUtilities.runOnUIThread(() -> fragment.scrollToRow(key, null));
                };
                items.add(new SettingsSearchResult(
                        uid + entry.getKey(), title, e_title, f_title, drawable, open));
            }
        }

        items.add(new SettingsSearchResult(29000, getString(R.string.OEAppearanceUseSystemEmoji), e_title,
                getString(R.string.EmojiSets), R.drawable.msg_emoji_smiles, () -> {
                    NekoEmojiSettingsActivity emoji = new NekoEmojiSettingsActivity();
                    callback.presentFragment(emoji);
                    AndroidUtilities.runOnUIThread(() -> emoji.scrollToRow("useSystemEmoji", null));
                }));

        String n_title = getString(R.string.Language);
        for (BaseNekoXSettingsActivity fragment: fragments) {
            int uid = fragment.getBaseGuid();
            int drawable = fragment.getDrawable();
            String f_title = fragment.getTitle();
            for (Map.Entry<Integer, String> entry : fragment.getRowMapReverse().entrySet()) {
                Integer i = entry.getKey();
                String key = entry.getValue();
                if (key.equals(String.valueOf(i))) {
                    continue;
                }
                int guid = uid + i;
                String title = getString(key);
                if (title == null || title.isEmpty()) {
                    continue;
                }
                Runnable open = () -> {
                    callback.presentFragment(fragment);
                    AndroidUtilities.runOnUIThread(() -> fragment.scrollToRow(key, null));
                };
                SettingsSearchResult result = new SettingsSearchResult(
                        guid, title, n_title, f_title, drawable, open
                );
                items.add(result);
            }
        }
        return items;
    }
}
