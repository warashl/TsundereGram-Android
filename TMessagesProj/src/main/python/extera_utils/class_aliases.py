"""Имена Java-классов TsundereGram → имена классов tsunderegram.

Плагины каталога написаны под TsundereGram и берут его классы по полному имени:
``find_class("com.tsunderegram.messenger.pillstack.core.PillRegistry")``,
``jclass(...)`` или ``from com.tsunderegram.messenger.plugins import PluginsController``.
У нас тот же код лежит в ``app.tsunderegram.*``, поэтому без подстановки плагин
получает ``None`` и падает на первом же обращении.

Подстановка работает только на имени: если класса с полученным именем у нас нет,
вызывающий получает прежний отказ. Разрешения проверяются уже по нашему имени —
правила в ``plugin_loader._JAVA_CLASS_RULES`` записаны для ``app.tsunderegram.*``.
"""

import importlib as _importlib
import importlib.util as _importlib_util
import sys as _sys
import types as _types

ROOT = "com.tsunderegram.messenger"
MEDIA_ROOT = "com.google.android.exoplayer2"
ROOTS = (ROOT, MEDIA_ROOT)


def _under_root(name):
    for root in ROOTS:
        if name.startswith(root + "."):
            return True
    return False


_EXACT = {
    "com.tsunderegram.messenger.utils.chats.ChatUtils":
        "com.tsunderegram.messenger.utils.chats.ChatUtils",
    "com.tsunderegram.messenger.utils.ChatUtils":
        "com.tsunderegram.messenger.utils.chats.ChatUtils",
    "com.tsunderegram.messenger.utils.text.LocaleUtils":
        "com.tsunderegram.messenger.utils.text.LocaleUtils",
    "com.tsunderegram.messenger.utils.LocaleUtils":
        "com.tsunderegram.messenger.utils.text.LocaleUtils",
    "com.tsunderegram.messenger.utils.AppUtils":
        "com.tsunderegram.messenger.utils.AppUtils",
    "com.tsunderegram.messenger.R":
        "org.telegram.messenger.R",
    "com.tsunderegram.messenger.utils.system.VibratorUtils":
        "com.tsunderegram.messenger.utils.system.VibratorUtils",
    "com.tsunderegram.messenger.ai.AiConfig":
        "app.tsunderegram.ai.AiConfig",
    "com.tsunderegram.messenger.ai.AiController":
        "com.tsunderegram.messenger.ai.AiController",
    "com.tsunderegram.messenger.ai.ui.ResponseAlert":
        "com.tsunderegram.messenger.ai.ui.ResponseAlert",
    "com.tsunderegram.messenger.ai.ui.GenerateFromMessageBottomSheet":
        "com.tsunderegram.messenger.ai.ui.GenerateFromMessageBottomSheet",
    "com.tsunderegram.messenger.plugins.ui.components.InstallPluginBottomSheet":
        "com.tsunderegram.messenger.plugins.ui.components.InstallPluginBottomSheet",
    "com.tsunderegram.messenger.utils.system.SystemUtils":
        "com.tsunderegram.messenger.utils.system.SystemUtils",
    "com.tsunderegram.messenger.utils.SystemUtils":
        "com.tsunderegram.messenger.utils.system.SystemUtils",
    "com.tsunderegram.messenger.preferences.MainPreferencesActivity":
        "app.tsunderegram.settings.OpenExteraSettingsActivity",
    "com.tsunderegram.messenger.preferences.GeneralPreferencesActivity":
        "app.tsunderegram.settings.OpenExteraGeneralActivity",
    "com.tsunderegram.messenger.preferences.AppearancePreferencesActivity":
        "app.tsunderegram.settings.OpenExteraAppearanceActivity",
    "com.tsunderegram.messenger.preferences.ChatsPreferencesActivity":
        "app.tsunderegram.settings.OpenExteraChatsActivity",
    "com.tsunderegram.messenger.preferences.OtherPreferencesActivity":
        "app.tsunderegram.settings.OpenExteraOtherActivity",
    "com.tsunderegram.messenger.preferences.AppNavigationPreferencesActivity":
        "app.tsunderegram.settings.OpenExteraAppNavigationActivity",
    "com.tsunderegram.messenger.preferences.BasePreferencesActivity":
        "com.tsunderegram.messenger.preferences.BasePreferencesActivity",
    "com.tsunderegram.messenger.preferences.components.AltSeekbar":
        "app.tsunderegram.appearance.AltSeekbar",
    "com.tsunderegram.messenger.utils.chats.MainMenuHelper":
        "app.tsunderegram.drawer.MainMenuHelper",
    "com.tsunderegram.messenger.icons.ui.IconPacksActivity":
        "app.tsunderegram.icons.IconPacksActivity",
    "com.tsunderegram.messenger.pillstack.ui.pills.crypto.utils.ColoredBackground":
        "app.tsunderegram.pillstack.pills.ColoredBackground",
    "com.tsunderegram.messenger.pillstack.ui.pills.weather.WeatherPill":
        "app.tsunderegram.pillstack.pills.WeatherPill",
    "com.tsunderegram.messenger.pillstack.ui.PillStackPreferencesActivity":
        "app.tsunderegram.pillstack.PillStackSettingsActivity",
    "com.tsunderegram.messenger.pillstack.ui.PillStackLayout":
        "app.tsunderegram.pillstack.PillStackView",
    "com.tsunderegram.messenger.pillstack.ui.pills.weather.WeatherPreferencesActivity":
        "app.tsunderegram.pillstack.pills.weather.WeatherSettingsActivity",
    "com.tsunderegram.messenger.preferences.appearance.AppearancePreferencesActivity":
        "app.tsunderegram.settings.OpenExteraAppearanceActivity",
    "com.tsunderegram.messenger.nowplaying.ui.components.NowPlayingCard":
        "app.tsunderegram.components.ProfileMusicCard",
    "com.tsunderegram.messenger.nowplaying.NowPlayingController":
        "app.tsunderegram.nowplaying.NowPlayingController",
    "com.tsunderegram.messenger.proxy.ProxyController":
        "app.tsunderegram.proxy.ProxyController",
    "com.tsunderegram.messenger.utils.ui.UIUtil":
        "app.tsunderegram.utils.UIUtil",
    "com.tsunderegram.messenger.utils.ui.MainTabsUiHelper":
        "app.tsunderegram.appearance.MainTabsUiHelper",
    "com.google.android.exoplayer2.util.Consumer":
        "androidx.media3.common.util.Consumer",
    "com.google.android.exoplayer2.video.VideoSize":
        "androidx.media3.common.VideoSize",
    "com.google.android.exoplayer2.PlaybackException":
        "androidx.media3.common.PlaybackException",
    "com.google.android.exoplayer2.PlaybackParameters":
        "androidx.media3.common.PlaybackParameters",
    "com.google.android.exoplayer2.C":
        "androidx.media3.common.C",
    "org.telegram.ui.Gifts.ProfileGiftsContainer$SelectGiftsBottomSheet":
        "org.telegram.ui.Gifts.SelectGiftsBottomSheet",
}

_PREFIXES = (
    ("com.tsunderegram.messenger.ai.data.", "app.tsunderegram.ai.data."),
    ("com.tsunderegram.messenger.ai.network.", "app.tsunderegram.ai.network."),
    ("com.tsunderegram.messenger.pillstack.core.", "app.tsunderegram.pillstack."),
    ("com.tsunderegram.messenger.pillstack.ui.pills.", "app.tsunderegram.pillstack.pills."),
    ("com.tsunderegram.messenger.pillstack.ui.", "app.tsunderegram.pillstack."),
    ("com.tsunderegram.messenger.preferences.", "app.tsunderegram.settings."),
    ("com.tsunderegram.messenger.plugins.", "app.tsunderegram.plugins."),
    ("com.tsunderegram.messenger.icons.", "app.tsunderegram.icons."),
    ("com.tsunderegram.messenger.camera.", "app.tsunderegram.camera."),
    ("com.tsunderegram.messenger.backup.", "app.tsunderegram.backup."),
    ("com.tsunderegram.messenger.feed.", "app.tsunderegram.feed."),
    ("com.tsunderegram.messenger.drawer.", "app.tsunderegram.drawer."),
    ("com.tsunderegram.messenger.components.", "app.tsunderegram.components."),
    ("com.tsunderegram.messenger.utils.", "app.tsunderegram.utils."),
)


_resolve_cache = {}
_field_shape_cache = {}
_NO_SHAPE = object()


def resolve(name):
    """Наше имя класса для имени TsundereGram; чужие имена возвращаются как есть."""
    if not isinstance(name, str):
        return name
    cached = _resolve_cache.get(name)
    if cached is not None:
        return cached
    result = _resolve_uncached(name)
    if len(_resolve_cache) < 4096:
        _resolve_cache[name] = result
    return result


def _resolve_uncached(name):
    exact = _EXACT.get(name)
    if exact is not None:
        return exact
    if not _under_root(name):
        return name
    outer, sep, nested = name.partition("$")
    exact = _EXACT.get(outer)
    if exact is not None:
        return exact + sep + nested
    for old, new in _PREFIXES:
        if outer.startswith(old):
            return new + outer[len(old):] + sep + nested
    return name


_FIELD_SHAPED = {
    "com.tsunderegram.messenger.ExteraConfig": {
        "translationProvider": ("getTranslationProvider", "setTranslationProvider"),
        "translationFormality": ("getTranslationFormality", "setTranslationFormality"),
        "disableNumberRounding": ("getDisableNumberRounding", "setDisableNumberRounding"),
        "formatTimeWithSeconds": ("getFormatTimeWithSeconds", "setFormatTimeWithSeconds"),
        "relativeLastSeen": ("getRelativeLastSeen", "setRelativeLastSeen"),
        "inAppVibration": ("getInAppVibration", "setInAppVibration"),
        "filterZalgo": ("getFilterZalgo", "setFilterZalgo"),
        "useYandexMaps": ("getUseYandexMaps", "setUseYandexMaps"),
        "downloadSpeedBoost": ("getDownloadSpeedBoost", "setDownloadSpeedBoost"),
        "uploadSpeedBoost": ("getUploadSpeedBoost", "setUploadSpeedBoost"),
        "hidePhoneNumber": ("getHidePhoneNumber", "setHidePhoneNumber"),
        "showIdAndDc": ("getShowIdAndDc", "setShowIdAndDc"),
        "hideArchiveFolder": ("getHideArchiveFolder", "setHideArchiveFolder"),
        "archiveOnPull": ("getArchiveOnPull", "setArchiveOnPull"),
        "disableUnarchiveSwipe": ("getDisableUnarchiveSwipe", "setDisableUnarchiveSwipe"),
        "doNotUseProxy": ("getDoNotUseProxy", "setDoNotUseProxy"),
        "customSavePath": ("getCustomSavePath", "setCustomSavePath"),
        "iconPack": ("getIconPack", "setIconPack"),
        "editingIconPackId": ("getEditingIconPackId", "setEditingIconPackId"),
        "avatarCorners": ("getAvatarCorners", "setAvatarCorners"),
        "singleCornerRadius": ("getSingleCornerRadius", "setSingleCornerRadius"),
        "dividerStyle": ("getDividerStyle", "setDividerStyle"),
        "forceSnow": ("getForceSnow", "setForceSnow"),
        "hideActionBarStatus": ("getHideActionBarStatus", "setHideActionBarStatus"),
        "centerTitle": ("getCenterTitle", "setCenterTitle"),
        "hideStories": ("getHideStories", "setHideStories"),
        "hideFloatingButton": ("getHideFloatingButton", "setHideFloatingButton"),
        "hideDialogsSearchBar": ("getHideDialogsSearchBar", "setHideDialogsSearchBar"),
        "senderMiniAvatars": ("getSenderMiniAvatars", "setSenderMiniAvatars"),
        "titleText": ("getTitleText", "setTitleText"),
        "tabIcons": ("getTabIcons", "setTabIcons"),
        "tabCounter": ("getTabCounter", "setTabCounter"),
        "hideAllChats": ("getHideAllChats", "setHideAllChats"),
        "squareFab": ("getSquareFab", "setSquareFab"),
        "sectionRadius": ("getSectionRadius", "setSectionRadius"),
        "sectionsSeparatedHeadersPreference": ("getSectionsSeparatedHeadersPreference", "setSectionsSeparatedHeadersPreference"),
        "newLoadingStyle": ("getNewLoadingStyle", "setNewLoadingStyle"),
        "newSliderStyle": ("getNewSliderStyle", "setNewSliderStyle"),
        "newSwitchStyle": ("getNewSwitchStyle", "setNewSwitchStyle"),
        "newChatHeaderStyle": ("getNewChatHeaderStyle", "setNewChatHeaderStyle"),
        "newNavigationBarStyle": ("getNewNavigationBarStyle", "setNewNavigationBarStyle"),
        "tabletMode": ("getTabletMode", "setTabletMode"),
        "useSystemFonts": ("getUseSystemFonts", "setUseSystemFonts"),
        "gooeyAvatarAnimation": ("getGooeyAvatarAnimation", "setGooeyAvatarAnimation"),
        "customThemes": ("getCustomThemes", "setCustomThemes"),
        "predictiveBackIntensity": ("getPredictiveBackIntensity", "setPredictiveBackIntensity"),
        "springAnimations": ("getSpringAnimations", "setSpringAnimations"),
        "glassOutlineStyle": ("getGlassOutlineStyle", "setGlassOutlineStyle"),
        "glassMessageMenu": ("getGlassMessageMenu", "setGlassMessageMenu"),
        "forceBlur": ("getForceBlur", "setForceBlur"),
        "eventType": ("getEventType", "setEventType"),
        "navigationDrawer": ("getNavigationDrawer", "setNavigationDrawer"),
        "immersiveDrawerAnimation": ("getImmersiveDrawerAnimation", "setImmersiveDrawerAnimation"),
        "showFeedTab": ("getShowFeedTab", "setShowFeedTab"),
        "showFeedUnreadCounter": ("getShowFeedUnreadCounter", "setShowFeedUnreadCounter"),
        "stickerSize": ("getStickerSize", "setStickerSize"),
        "hideStickerTime": ("getHideStickerTime", "setHideStickerTime"),
        "replyColors": ("getReplyColors", "setReplyColors"),
        "replyEmoji": ("getReplyEmoji", "setReplyEmoji"),
        "replyBackground": ("getReplyBackground", "setReplyBackground"),
        "stickerShape": ("getStickerShape", "setStickerShape"),
        "unlimitedRecentStickers": ("getUnlimitedRecentStickers", "setUnlimitedRecentStickers"),
        "hideReactionsInPrivateChats": ("getHideReactionsInPrivateChats", "setHideReactionsInPrivateChats"),
        "hideReactionsInChannels": ("getHideReactionsInChannels", "setHideReactionsInChannels"),
        "hideReactionsInGroups": ("getHideReactionsInGroups", "setHideReactionsInGroups"),
        "doubleTapAction": ("getDoubleTapAction", "setDoubleTapAction"),
        "doubleTapActionOutOwner": ("getDoubleTapActionOutOwner", "setDoubleTapActionOutOwner"),
        "bottomButton": ("getBottomButton", "setBottomButton"),
        "widePostsInFeed": ("getWidePostsInFeed", "setWidePostsInFeed"),
        "widePostsInChannels": ("getWidePostsInChannels", "setWidePostsInChannels"),
        "telegramAiEditor": ("getTelegramAiEditor", "setTelegramAiEditor"),
        "telegramAiSummaries": ("getTelegramAiSummaries", "setTelegramAiSummaries"),
        "quickAdminShortcuts": ("getQuickAdminShortcuts", "setQuickAdminShortcuts"),
        "quickTransitionForChannels": ("getQuickTransitionForChannels", "setQuickTransitionForChannels"),
        "quickTransitionForTopics": ("getQuickTransitionForTopics", "setQuickTransitionForTopics"),
        "disableGreetingSticker": ("getDisableGreetingSticker", "setDisableGreetingSticker"),
        "hideKeyboardOnScroll": ("getHideKeyboardOnScroll", "setHideKeyboardOnScroll"),
        "addCommaAfterMention": ("getAddCommaAfterMention", "setAddCommaAfterMention"),
        "disableMarkdown": ("getDisableMarkdown", "setDisableMarkdown"),
        "hideSendAsPeer": ("getHideSendAsPeer", "setHideSendAsPeer"),
        "removeMessageTail": ("getRemoveMessageTail", "setRemoveMessageTail"),
        "replaceEditedWithIcon": ("getReplaceEditedWithIcon", "setReplaceEditedWithIcon"),
        "showOnlineStatus": ("getShowOnlineStatus", "setShowOnlineStatus"),
        "hideShareButton": ("getHideShareButton", "setHideShareButton"),
        "showResultsBeforeVoting": ("getShowResultsBeforeVoting", "setShowResultsBeforeVoting"),
        "showCopyPhotoButton": ("getShowCopyPhotoButton", "setShowCopyPhotoButton"),
        "showSaveMessageButton": ("getShowSaveMessageButton", "setShowSaveMessageButton"),
        "showRepeatMessageButton": ("getShowRepeatMessageButton", "setShowRepeatMessageButton"),
        "showClearButton": ("getShowClearButton", "setShowClearButton"),
        "showHistoryButton": ("getShowHistoryButton", "setShowHistoryButton"),
        "showReportButton": ("getShowReportButton", "setShowReportButton"),
        "showGenerateButton": ("getShowGenerateButton", "setShowGenerateButton"),
        "showDetailsButton": ("getShowDetailsButton", "setShowDetailsButton"),
        "groupMessageMenu": ("getGroupMessageMenu", "setGroupMessageMenu"),
        "recognitionLanguage": ("getRecognitionLanguage", "setRecognitionLanguage"),
        "postprocessingWithAi": ("getPostprocessingWithAi", "setPostprocessingWithAi"),
        "cameraType": ("getCameraType", "setCameraType"),
        "extendedFramesPerSecond": ("getExtendedFramesPerSecond", "setExtendedFramesPerSecond"),
        "cameraStabilization": ("getCameraStabilization", "setCameraStabilization"),
        "cameraMirrorMode": ("getCameraMirrorMode", "setCameraMirrorMode"),
        "videoMessagesCamera": ("getVideoMessagesCamera", "setVideoMessagesCamera"),
        "rememberLastUsedCamera": ("getRememberLastUsedCamera", "setRememberLastUsedCamera"),
        "startWithWideAngleCamera": ("getStartWithWideAngleCamera", "setStartWithWideAngleCamera"),
        "zoomSlider": ("getZoomSlider", "setZoomSlider"),
        "staticZoom": ("getStaticZoom", "setStaticZoom"),
        "alwaysSendInHD": ("getAlwaysSendInHD", "setAlwaysSendInHD"),
        "hideCameraTile": ("getHideCameraTile", "setHideCameraTile"),
        "doubleTapSeekDuration": ("getDoubleTapSeekDuration", "setDoubleTapSeekDuration"),
        "preferOriginalQuality": ("getPreferOriginalQuality", "setPreferOriginalQuality"),
        "swipeToPip": ("getSwipeToPip", "setSwipeToPip"),
        "unmuteWithVolumeButtons": ("getUnmuteWithVolumeButtons", "setUnmuteWithVolumeButtons"),
        "pauseOnMinimizeVideo": ("getPauseOnMinimizeVideo", "setPauseOnMinimizeVideo"),
        "pauseOnMinimizeVoice": ("getPauseOnMinimizeVoice", "setPauseOnMinimizeVoice"),
        "pauseOnMinimizeRound": ("getPauseOnMinimizeRound", "setPauseOnMinimizeRound"),
        "useGoogleCrashlytics": ("getUseGoogleCrashlytics", "setUseGoogleCrashlytics"),
        "useGoogleAnalytics": ("getUseGoogleAnalytics", "setUseGoogleAnalytics"),
        "enableAdBlock": ("getEnableAdBlock", "setEnableAdBlock"),
        "updateScheduleTimestamp": ("getUpdateScheduleTimestamp", "setUpdateScheduleTimestamp"),
        "sdkUpdateScheduleTimestamp": ("getSdkUpdateScheduleTimestamp", "setSdkUpdateScheduleTimestamp"),
        "targetLang": ("getTargetLang", "setTargetLang"),
        "flashWarmth": ("getFlashWarmth", "setFlashWarmth"),
        "flashIntensity": ("getFlashIntensity", "setFlashIntensity"),
        "pluginsDevMode": ("getPluginsDevMode", "setPluginsDevMode"),
        "pluginsSafeMode": ("getPluginsSafeMode", "setPluginsSafeMode"),
        "pluginsCompactView": ("getPluginsCompactView", "setPluginsCompactView"),
        "pluginsPySdkAutoUpdate": ("getPluginsPySdkAutoUpdate", "setPluginsPySdkAutoUpdate"),
        "pluginsPySdkBetaVersions": ("getPluginsPySdkBetaVersions", "setPluginsPySdkBetaVersions"),
        "pluginsDisableArtOpts": ("getPluginsDisableArtOpts", "setPluginsDisableArtOpts"),
        "pinnedPlugins": ("getPinnedPlugins", "setPinnedPlugins"),
        "useSystemIconShape": ("getUseSystemIconShape", "setUseSystemIconShape"),
        "editor": "getEditor",
        "preferences": "getPreferences",
        "GSON": "getGSON",
        "pluginsEngine": ("getPluginsEngine", "setPluginsEngine"),
        "avatarSquareness": "getAvatarSquareness",
        "sectionRadiusDp": "getSectionRadiusDp",
        "sectionsSeparatedHeaders": ("getSectionsSeparatedHeaders", "setSectionsSeparatedHeaders"),
        "logging": ("getLogging", "setLogging"),
        "mainMenuLayout": "getMainMenuLayout",
        "mainMenuHiddenItems": "getMainMenuHiddenItems",
        "defaultMainMenuLayout": "getDefaultMainMenuLayout",
        "iconPacksLayout": "getIconPacksLayout",
        "iconPacksHidden": "getIconPacksHidden",
        "doNotMarkAsNew": "getDoNotMarkAsNew",
        "newFeaturesShowedAt": "getNewFeaturesShowedAt",
        "yandexSearchEngine": "getYandexSearchEngine",
        "currentLangName": "getCurrentLangName",
        "apiBotInfo": "getApiBotInfo",
        "backupKeys": "getBackupKeys",
        "onlineDotInnerRadius": "getOnlineDotInnerRadius",
        "onlineDotOuterRadius": "getOnlineDotOuterRadius",
    },
    "com.tsunderegram.messenger.plugins.PluginsController": {
        "engines": "getEngines",
        "plugins": "getPlugins",
        "pluginsDir": "getPluginsDir",
        "preferences": "getPreferences",
        "initialized": "getInitialized",
        "settings": "getSettings",
        "watchdog": "getWatchdog",
    },
    "com.tsunderegram.messenger.pillstack.core.PillStackConfig": {
        "activePills": "getActivePills",
        "hiddenPills": "getHiddenPills",
        "configLoaded": "isConfigLoaded",
    },
    "com.tsunderegram.messenger.ai.AiConfig": {
        "saveHistory": ("getSaveHistory", "setSaveHistory"),
        "responseStreaming": ("getResponseStreaming", "setResponseStreaming"),
        "temperature": ("getTemperature", "setTemperature"),
        "showResponseOnly": ("getShowResponseOnly", "setShowResponseOnly"),
        "insertAsQuote": ("getInsertAsQuote", "setInsertAsQuote"),
        "selectedServiceId": ("getSelectedServiceId", "setSelectedServiceId"),
        "selectedRole": ("getSelectedRole", "setSelectedRole"),
        "preferences": "getPreferences",
        "services": "getServices",
        "roles": "getRoles",
        "conversationHistory": "getConversationHistory",
        "selectedService": "getSelectedService",
    },
}


_INSTANCE_SHAPED = {
    "org.telegram.messenger.SharedConfig$ProxyInfo": {
        "address": ("getAddress", "setAddress"),
        "port": ("getPort", "setPort"),
        "username": ("getUsername", "setUsername"),
        "password": ("getPassword", "setPassword"),
        "secret": ("getSecret", "setSecret"),
        "link": "getLink",
    },
}

_WRAP_RESULT = {
    "org.telegram.messenger.SharedConfig": {
        "currentProxy": "org.telegram.messenger.SharedConfig$ProxyInfo",
    },
}


class _FieldShapedObject:
    """Java-объект, поля которого у эталона есть, а у нас переехали в геттеры."""

    def __init__(self, java_obj, fields):
        getters, setters = {}, {}
        for field, target in fields.items():
            if isinstance(target, (tuple, list)):
                getters[field] = target[0]
                if len(target) > 1 and target[1]:
                    setters[field] = target[1]
            else:
                getters[field] = target
        object.__setattr__(self, "_tsunderegram_java", java_obj)
        object.__setattr__(self, "_tsunderegram_fields", getters)
        object.__setattr__(self, "_tsunderegram_setters", setters)

    def __getattr__(self, attr):
        target = object.__getattribute__(self, "_tsunderegram_java")
        method = object.__getattribute__(self, "_tsunderegram_fields").get(attr)
        if method is not None:
            return getattr(target, method)()
        return getattr(target, attr)

    def __setattr__(self, attr, value):
        target = object.__getattribute__(self, "_tsunderegram_java")
        method = object.__getattribute__(self, "_tsunderegram_setters").get(attr)
        if method is not None:
            getattr(target, method)(value)
            return
        setattr(target, attr, value)

    def __eq__(self, other):
        return object.__getattribute__(self, "_tsunderegram_java") == unwrap(other)

    def __hash__(self):
        return hash(object.__getattribute__(self, "_tsunderegram_java"))

    def __repr__(self):
        return repr(object.__getattribute__(self, "_tsunderegram_java"))


def wrap_instance(obj, java_name):
    fields = _INSTANCE_SHAPED.get(java_name)
    if obj is None or fields is None or isinstance(obj, _FieldShapedObject):
        return obj
    try:
        return _FieldShapedObject(obj, fields)
    except Exception:
        return obj


class _FieldShapedClass:
    """Java-класс, у которого часть статических методов читается как поля.

    У TsundereGram это поля (Kotlin-делегаты), у нас — методы: поле не умеет
    отдавать живое значение настройки. Chaquopy различает вызов и чтение
    атрибута, поэтому плагин, написанный под поле, получал объект метода —
    всегда истинный. Так zwylib считал, что включён safe mode, и молча
    отключал свои хуки.
    """

    def __init__(self, java_class, fields, wraps=None):
        getters = {}
        setters = {}
        for field, target in fields.items():
            if isinstance(target, (tuple, list)):
                getters[field] = target[0]
                if len(target) > 1 and target[1]:
                    setters[field] = target[1]
            else:
                getters[field] = target
        object.__setattr__(self, "_tsunderegram_java", java_class)
        object.__setattr__(self, "_tsunderegram_fields", getters)
        object.__setattr__(self, "_tsunderegram_setters", setters)
        object.__setattr__(self, "_tsunderegram_wraps", wraps or {})

    def __getattr__(self, attr):
        target = object.__getattribute__(self, "_tsunderegram_java")
        method = object.__getattribute__(self, "_tsunderegram_fields").get(attr)
        if method is not None:
            value = getattr(target, method)()
        else:
            value = getattr(target, attr)
        wrapped = object.__getattribute__(self, "_tsunderegram_wraps").get(attr)
        if wrapped is not None:
            return wrap_instance(value, wrapped)
        return value

    def __setattr__(self, attr, value):
        target = object.__getattribute__(self, "_tsunderegram_java")
        method = object.__getattribute__(self, "_tsunderegram_setters").get(attr)
        if method is not None:
            getattr(target, method)(value)
            return
        setattr(target, attr, value)

    def __repr__(self):
        return repr(object.__getattribute__(self, "_tsunderegram_java"))


def unwrap(obj):
    """Настоящий Java-класс из обёртки; чужие объекты возвращаются как есть."""
    if isinstance(obj, (_FieldShapedClass, _FieldShapedObject)):
        return object.__getattribute__(obj, "_tsunderegram_java")
    return obj


def _field_shape(name):
    cached = _field_shape_cache.get(name, None)
    if cached is not None:
        return None if cached is _NO_SHAPE else cached
    shape = _field_shape_uncached(name)
    if isinstance(name, str) and len(_field_shape_cache) < 4096:
        _field_shape_cache[name] = _NO_SHAPE if shape is None else shape
    return shape


def _field_shape_uncached(name):
    fields = _FIELD_SHAPED.get(name)
    if fields is not None:
        return fields
    for source, shaped in _FIELD_SHAPED.items():
        if resolve(source) == name:
            return shaped
    return None


def adapt(name, obj):
    """Обёртка над классом, форма которого у нас разошлась с эталоном."""
    if obj is None or isinstance(obj, _FieldShapedClass):
        return obj
    replacement = substitute(name)
    if replacement is not None:
        return replacement
    fields = _field_shape(name)
    wraps = _WRAP_RESULT.get(name)
    if fields is None and wraps is None:
        return obj
    try:
        return _FieldShapedClass(obj, fields or {}, wraps)
    except Exception:
        return obj


_PYTHON_SUBSTITUTES = {
    "com.tsunderegram.messenger.plugins.models.PluginItemFactory":
        ("ui.settings", "SimpleSettingFactory"),
}


def substitute(name):
    if not isinstance(name, str):
        return None
    target = _PYTHON_SUBSTITUTES.get(name)
    if target is None:
        return None
    module, attr = target
    try:
        return getattr(_importlib.import_module(module), attr, None)
    except Exception:
        return None


_plain_cache = {}


def is_plain(name):
    cached = _plain_cache.get(name)
    if cached is not None:
        return cached
    plain = (isinstance(name, str) and resolve(name) == name
             and name not in _PYTHON_SUBSTITUTES and name not in _WRAP_RESULT
             and _field_shape(name) is None)
    if len(_plain_cache) < 4096:
        _plain_cache[name] = plain
    return plain


def is_alias(name):
    """Стоит ли пытаться подставлять это имя."""
    return isinstance(name, str) and _under_root(name)


def _find_class(name):
    try:
        from hook_utils import find_class
        return find_class(name)
    except Exception:
        return None


class _AliasModule(_types.ModuleType):
    """Пакет-заглушка: атрибут сначала пробуется как класс, потом как подпакет."""

    def __getattr__(self, attr):
        if attr.startswith("__"):
            raise AttributeError(attr)
        full = self.__name__ + "." + attr
        replacement = substitute(full)
        if replacement is not None:
            return replacement
        found = _find_class(full)
        if found is not None:
            return found
        if attr[:1].isupper():
            raise AttributeError(attr)
        try:
            return _importlib.import_module(full)
        except Exception:
            raise AttributeError(attr)


class _AliasFinder:
    """sys.meta_path-финдер на ``com.tsunderegram.*``.

    Нужен для формы ``from com.tsunderegram.messenger.plugins import PluginsController``:
    она идёт мимо find_class и jclass, в машинерию импорта.
    """

    PACKAGE = "com.tsunderegram"
    PACKAGES = ("com.tsunderegram", MEDIA_ROOT)

    def find_spec(self, fullname, path=None, target=None):
        if not any(fullname == root or fullname.startswith(root + ".")
                   or root.startswith(fullname + ".")
                   for root in self.PACKAGES):
            return None
        if fullname.rpartition(".")[2][:1].isupper():
            return None
        return _importlib_util.spec_from_loader(fullname, _AliasLoader(), is_package=True)


class _AliasLoader:

    def create_module(self, spec):
        module = _AliasModule(spec.name)
        module.__path__ = []
        return module

    def exec_module(self, module):
        return None


def _ensure_root_package():
    """Создаёт пакет ``com``, если его не даёт Chaquopy.

    На устройстве ``com`` существует (com.google, com.android), и подменять его
    нельзя. Заглушка ставится только там, где импорт вообще не проходит.
    """
    try:
        _importlib.import_module("com")
        return
    except Exception:
        pass
    module = _AliasModule("com")
    module.__path__ = []
    _sys.modules["com"] = module


def install_import_hook():
    """Ставит финдер в sys.meta_path. Идемпотентно, ничего не бросает."""
    try:
        if any(isinstance(finder, _AliasFinder) for finder in _sys.meta_path):
            return
        _sys.meta_path.insert(0, _AliasFinder())
        _ensure_root_package()
    except Exception:
        pass
