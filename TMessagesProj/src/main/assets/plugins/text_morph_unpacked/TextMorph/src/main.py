"""
================================================================
 ███╗   ██╗███████╗██████╗ 
 ████╗  ██║██╔════╝██╔═══██╗
 ██╔██╗ ██║█████╗  ██║   ██║
 ██║╚██╗██║██╔══╝  ██║   ██║
 ██║ ╚████║███████╗╚██████╔╝
 ╚═╝  ╚═══╝╚══════╝ ╚═════╝ 
             [ TEXT MORPH ]
================================================================
 © 2026 mrneoner (@mrneoner, @neo_plugin)

 LICENSE / ЛИЦЕНЗИЯ:
 EN: Free for personal use and study. Copying, modifying, or 
     reusing code fragments in other releases WITHOUT explicit 
     permission from the developer and WITHOUT crediting the 
     author is STRICTLY PROHIBITED.

 RU: Использовать и изучать код в личных целях можно свободно. 
     ОДНАКО любое копирование, модификация или использование 
     фрагментов кода в других релизах БЕЗ разрешения 
     разработчика и БЕЗ упоминания авторства — 
     СТРОГО ЗАПРЕЩЕНО.
================================================================
"""

from typing import Any, List, Dict
import os
import random
import string
import threading
from base_plugin import BasePlugin, HookResult, HookStrategy, MenuItemData, MenuItemType
from ui.settings import Header, Switch, Text, Divider, Selector, Custom, Input
from ui.bulletin import BulletinHelper
from ui.alert import AlertDialogBuilder
from client_utils import get_last_fragment
from android_utils import run_on_ui_thread

try:
    from android_utils import OnClickListener
except Exception:
    OnClickListener = None

try:
    from org.telegram.messenger import MessagesController, UserConfig, LocaleController, AndroidUtilities, ApplicationLoader
except Exception:
    MessagesController = UserConfig = LocaleController = AndroidUtilities = ApplicationLoader = None

try:
    from org.telegram.ui.ActionBar import Theme, BottomSheet
except Exception:
    Theme = BottomSheet = None

try:
    from android.widget import EditText, LinearLayout, FrameLayout, TextView, ImageView, ScrollView
    from android.view import Gravity, View
    from android.graphics.drawable import GradientDrawable, Drawable
    from android.graphics import BitmapFactory, Typeface
    from android.text import InputType, Html, SpannableStringBuilder, Spannable
    from android.text.style import StyleSpan, UnderlineSpan, StrikethroughSpan, TypefaceSpan, BackgroundColorSpan, QuoteSpan, ForegroundColorSpan
except Exception:
    EditText = LinearLayout = FrameLayout = TextView = ImageView = ScrollView = None
    Gravity = View = None
    GradientDrawable = Drawable = None
    BitmapFactory = Typeface = None
    InputType = Html = SpannableStringBuilder = Spannable = None
    StyleSpan = UnderlineSpan = StrikethroughSpan = TypefaceSpan = BackgroundColorSpan = QuoteSpan = ForegroundColorSpan = None

try:
    from org.telegram.tgnet import TLRPC
except Exception:
    TLRPC = None

try:
    from org.telegram.ui.Components import UItem
except Exception:
    UItem = None

try:
    from org.telegram.ui.Components import AvatarDrawable
except Exception:
    AvatarDrawable = None

try:
    from org.telegram.ui.Components import BackupImageView
except Exception:
    BackupImageView = None

try:
    from java.util import ArrayList
except Exception:
    ArrayList = None

try:
    from java.lang import String
except Exception:
    String = None

CODE_KEYWORDS = {
    "print", "def", "import", "from", "class", "return", "for", "while", "if", "elif", "else",
    "try", "except", "finally", "with", "as", "pass", "break", "continue", "async", "await",
    "const", "let", "var", "function", "console", "echo", "cat", "grep", "cd", "ls", "git",
    "docker", "npm", "pip", "python", "node", "curl", "select", "insert", "update", "delete",
    "public", "private", "protected", "void", "int", "float", "double", "bool", "string",
    "main", "struct", "enum", "type", "package", "val", "fun"
}

__id__ = "text_morph"
__name__ = "Text Morph"
__description__ = "Изменение формы и стиля текста. Стакинг стилей, различные функции."
__author__ = "@mrneoner & @neo_plugin"
__version__ = "1.2.0"
__icon__ = "sekaiware/3"

STRINGS = {
    "ru": {
        "plugin_disabled_shadow": "Плагин выключен. Включите его, чтобы увидеть все настройки.",
        "state_on": "включен",
        "state_off": "выключен",
        "toast_plugin_state": "Плагин Text Morph {val}.",
        "toast_ui_error": "Ошибка интерфейса.",
        "about_dev": "О разработчике",
        "plugin_channel": "Канал плагина",
        "contact_me": "Связь со мной",
        "link_copied": "Ссылка скопирована!",
        "share_link_title": "Поделиться ссылкой",
        "copy_link": "Копировать ссылку",
        "share_link": "Поделиться ссылкой",
        "unk_name": "Неизвестно 乁⁠(⁠ •⁠_⁠•⁠ ⁠)⁠ㄏ",
        "unk_noname": "Без имени",
        "unk_chat": "Неизвестный чат 乁⁠(⁠ •⁠_⁠•⁠ ⁠)⁠ㄏ",

        "menu_global_btn": "Text Morph: Статус",
        "menu_global_sub": "Включение/Выключение плагина",
        "menu_chat_btn": "Text Morph: Исключения",
        "menu_chat_sub": "Управление исключениями",

        "main_settings_header": "Основные настройки",
        "main_enable_plugin": "Включить плагин",
        
        "main_acc_header": "Аккаунты",
        "main_acc_title": "Активные аккаунты",
        "main_acc_sub": "Активно профилей: {val} шт.",
        "acc_sub_all": "Все аккаунты",
        "acc_manage_title": "Управление профилями",
        "acc_all_toggle": "Работать на всех аккаунтах",
        "acc_all_shadow": "Плагин будет применяться ко всем аккаунтам.",
        "acc_specific_shadow": "Добавьте аккаунты, где будет работать плагин.",
        "acc_add_btn": "Привязать текущий аккаунт",
        "acc_add_desc": "Включить плагин на текущем аккаунте",
        "acc_active_list": "Работает на аккаунтах:",
        "acc_no_active": "Плагин нигде не работает!",
        "acc_profile_id": "ID аккаунта: {val}",
        "acc_delete_hint": "Нажмите на аккаунт в списке, чтобы отключить его.",
        "acc_clear_all": "Очистить список",
        "msg_acc_added": "Аккаунт успешно включен!",
        "msg_acc_exists": "Этот аккаунт уже включен.",
        "msg_acc_removed": "Аккаунт отключен от плагина.",
        "msg_acc_err": "Ошибка получения ID аккаунта.",
        "msg_acc_cleared": "Список аккаунтов очищен.",
        "acc_current_badge": "текущий",
        "acc_device_list_title": "Аккаунты на этом устройстве",
        "acc_device_hint": "Нажмите на карточку аккаунта, чтобы включить или отключить Text Morph.",
        "acc_status_active": "Включен",
        "acc_status_inactive": "Подключить",
        "acc_other_list_title": "Другие сохраненные ID",
        "acc_actions_header": "Действия",
        "acc_add_manual_desc": "Ввести числовой ID другого аккаунта вручную",
        "dlg_acc_add_title": "Добавить ID аккаунта",

        "preview_header": "ПРЕДПРОСМОТР",
        "preview_edit_btn": "Изменить текст",
        "preview_default_text": "Привет! Это пример оформления сообщения в Text Morph.",
        "preview_hint": "Так ваше сообщение будет выглядеть в чате.",
        "preview_empty_hint": "Стили не выбраны — текст останется обычным.",
        "dlg_preview_edit_title": "Текст предпросмотра",
        "dlg_preview_edit_hint": "Введите тестовую фразу...",

        "welcome_opened": "Приветственный экран открыт",
        "welcome_sheet_btn": "Приветственный экран",
        "welcome_sheet_desc": "Показать экран возможностей (или зажмите версию)",
        "welcome_title": "Text Morph",
        "welcome_subtitle": "Инструмент стилизации и трансформации текста",
        "welcome_card_title": "Преображение текста сообщений",
        "welcome_card_body": "Text Morph автоматически трансформирует ваши исходящие сообщения на лету: применяет кастомные шрифты, стакинг стилей оформления (жирный, курсив, моноширинный, спойлеры, цитаты), глифы Zalgo, Leet Speak, умный анти-капс и подписи.\n\nВсе изменения происходят локально прямо перед отправкой в Telegram.",
        "welcome_pill1": "Стили разметки",
        "welcome_pill2": "100% Локально",
        "welcome_pill3": "Zalgo & Leet",
        "welcome_btn_continue": "Принять и продолжить",
        "about_plugin_title": "О плагине",

        "format_modifiers_header": "Модификаторы",
        "s_bold": "Жирный",
        "s_italic": "Курсив",
        "s_bold_italic": "Жирный + Курсив",
        "s_mono": "Моноширинный",
        "s_code": "Блок кода",
        "code_lang": "Язык кода",
        "code_lang_default": "Без языка (стандартный блок)",
        "dlg_code_lang_title": "Язык блока кода",
        "dlg_code_lang_hint": "Например: python, bash, json, cpp, js...",
        "msg_code_lang_saved": "Язык кода сохранен!",
        "code_lang_shadow": "Укажите язык для блока кода (например: python, json, bash). Если пусто — будет стандартный блок кода без заголовка.",
        "s_quote": "Цитата",
        "s_strike": "Зачёркнутый",
        "s_spoiler": "Спойлер",
        "s_underline": "Подчёркнутый",
        "s_random": "Случайный стиль",
        "format_random_pool_btn": "Настройка пула случайных стилей",

        "modes_header": "Режимы обработки текста",
        "m_vaporwave": "Vaporwave ( Ａ Ｅ Ｓ )",
        "vaporwave_hint": "Работает только для английских букв, цифр и знаков препинания.",
        "m_zalgo": "Zalgo текст",
        "zalgo_lvl": "Мощность Zalgo",
        "lvl_light": "Слабый",
        "lvl_mid": "Средний",
        "lvl_hard": "Жесткий",
        "lvl_max": "Максимум",
        "zalgo_warn_title": "Предупреждение",
        "zalgo_warn_msg": "Внимание! Использование Zalgo-текста может негативно восприниматься модерационными ботами (например, Ирис) и приводить к мутам или кикам в беседах.\n\nИспользуйте с осторожностью. Автор плагина не несет ответственности за полученные ограничения.",
        "anticaps_header": "Настройки анти-капса",
        "m_anti_caps": "Анти-капс",
        "anti_caps_pct": "Порог срабатывания",
        "anti_caps_capitalize": "Заглавная первая буква",
        "affix_header": "Подпись к сообщениям",
        "prefix_text": "Префикс",
        "suffix_text": "Суффикс",
        "dlg_prefix_title": "Префикс текста",
        "dlg_prefix_hint": "Введите префикс (например: [TM] или » )",
        "dlg_suffix_title": "Суффикс текста",
        "dlg_suffix_hint": "Введите суффикс (например: ~ или // TM)",
        "msg_prefix_saved": "Префикс сохранен!",
        "msg_suffix_saved": "Суффикс сохранен!",
        "not_set": "Не задан",
        "dlg_btn_clear": "Очистить",
        "presuf_alert_shadow": "Пробелы нужно ставить вручную, даже если они вначале (когда нужен отступ от основого сообщения)",
        "m_leet_speak": "Leet Speak",
        "m_custom_font": "Шрифты",
        "font_normal": "Обычный",
        "font_bold": "Math Bold",
        "font_gothic": "Gothic",
        "font_cursive": "Cursive",
        "font_double": "Double Struck",
        "font_squares": "Squares",
        "font_bubbles": "Bubbles",
        
        "font_en_only_hint": "Шрифты работают только для английского алфавита.",
        "enable_modifier": "Включить модификатор",
        "leet_add_en_btn": "Добавить (Английский)",
        "leet_add_ru_btn": "Добавить (Русский)",
        "leet_err_not_en": "Ошибка: ожидается английская буква.",
        "leet_err_not_ru": "Ошибка: ожидается русская буква.",
        "leet_add_desc": "Введите новую букву и символ",
        "leet_err_length": "Ошибка: буква должна состоять ровно из 1 символа.",
        "leet_err_not_letter": "Ошибка: разрешены только буквы.",
        "leet_err_exists": "Ошибка: эта буква уже есть в списке.",
        "leet_msg_added": "Замена успешно добавлена!",
        "leet_msg_removed": "Замена удалена.",
        "leet_reset_btn": "Сбросить по умолчанию",
        "leet_msg_reset": "Настройки Leet Speak сброшены.",
        "leet_msg_edited": "Замена успешно изменена!",
        "dlg_leet_edit_title": "Редактирование {val}",
        "dlg_btn_save": "Сохранить",
        "leet_hint_full": "Нажмите, чтобы удалить. Зажмите, чтобы изменить символ.",
        "leet_delete_hint": "Нажмите на элемент, чтобы удалить его.",
        "dlg_leet_title": "Добавление замены",
        "dlg_leet_char_hint": "Буква (например, А)",
        "dlg_leet_rep_hint": "Символ (например, 4 или @)",

        "exc_header": "Исключения чатов",
        "exc_title": "Игнорируемые чаты",
        "exc_sub": "Исключений: {val} шт.",
        "exc_manage_title": "Управление исключениями",
        "exc_add_btn": "Добавить по ID",
        "exc_add_desc": "Ввести ID чата вручную",
        "exc_active_list": "Список исключений",
        "exc_list_empty": "Список исключений пока пуст",
        "exc_item_id": "ID: {val}",
        "exc_item_username": " • {val}",
        "exc_delete_hint": "Нажмите на чат в списке, чтобы удалить его из списка исключений.",
        "exc_clear_all": "Очистить весь список",
        "msg_enabled": "Text Morph включен для этого чата.",
        "msg_disabled": "Text Morph отключен для этого чата.",
        "msg_removed": "Удалено из исключений.",
        "msg_cleared": "Список полностью очищен.",
        "msg_added_dialog": "Чат добавлен в исключения.",
        "msg_invalid_id": "Введен некорректный ID.",
        "dlg_exc_add_title": "Добавить чат-исключение",
        "dlg_exc_add_hint": "-100123456789",
        "dlg_btn_add": "Добавить",
        "dlg_btn_cancel": "Отмена"
    },
    "en": {
        "plugin_disabled_shadow": "The plugin is disabled. Enable it to see all settings.",
        "state_on": "enabled",
        "state_off": "disabled",
        "toast_plugin_state": "Text Morph plugin {val}.",
        "toast_ui_error": "UI error.",
        "about_dev": "About Developer",
        "plugin_channel": "Plugin Channel",
        "contact_me": "Contact Me",
        "link_copied": "Link copied!",
        "share_link_title": "Share Link",
        "copy_link": "Copy Link",
        "share_link": "Share Link",
        "unk_name": "Unknown",
        "unk_noname": "No Name",
        "unk_chat": "Unknown Chat",

        "menu_global_btn": "Text Morph: Toggle",
        "menu_global_sub": "Turn on/off the plugin",
        "menu_chat_btn": "Text Morph: Exceptions",
        "menu_chat_sub": "Manage chat exceptions",

        "main_settings_header": "General Settings",
        "main_enable_plugin": "Enable Plugin",
        
        "main_acc_header": "Accounts",
        "main_acc_title": "Active Accounts",
        "main_acc_sub": "Active profiles: {val}",
        "acc_sub_all": "All accounts",
        "acc_manage_title": "Manage Profiles",
        "acc_all_toggle": "Work on all accounts",
        "acc_all_shadow": "The plugin will apply to all connected accounts.",
        "acc_specific_shadow": "Add accounts where the plugin should be active.",
        "acc_add_btn": "Link Current Account",
        "acc_add_desc": "Enable plugin on the active account",
        "acc_active_list": "Active on Accounts:",
        "acc_no_active": "Plugin is not active anywhere!",
        "acc_profile_id": "Account ID: {val}",
        "acc_delete_hint": "Tap on a profile in the list to remove it.",
        "acc_clear_all": "Clear List",
        "msg_acc_added": "Account successfully linked!",
        "msg_acc_exists": "This account is already linked.",
        "msg_acc_removed": "Account unlinked from the plugin.",
        "msg_acc_err": "Error retrieving account ID.",
        "msg_acc_cleared": "Account list cleared.",
        "acc_current_badge": "current",
        "acc_device_list_title": "Accounts on this device",
        "acc_device_hint": "Tap account card to toggle Text Morph on or off.",
        "acc_status_active": "Active",
        "acc_status_inactive": "Connect",
        "acc_other_list_title": "Other saved IDs",
        "acc_actions_header": "Actions",
        "acc_add_manual_desc": "Enter numeric ID of another account manually",
        "dlg_acc_add_title": "Add Account ID",

        "preview_header": "LIVE PREVIEW",
        "preview_edit_btn": "Edit Text",
        "preview_default_text": "Hello! This is a message styling example in Text Morph.",
        "preview_hint": "This is how your message will look in chat.",
        "preview_empty_hint": "No styles selected — text will remain normal.",
        "dlg_preview_edit_title": "Preview Text",
        "dlg_preview_edit_hint": "Enter sample text...",

        "welcome_opened": "Welcome screen opened",
        "welcome_sheet_btn": "Welcome Screen",
        "welcome_sheet_desc": "Show overview screen (or long-press version)",
        "welcome_title": "Text Morph",
        "welcome_subtitle": "Text styling & transformation tool",
        "welcome_card_title": "Message Text Transformation",
        "welcome_card_body": "Text Morph automatically modifies outgoing messages on the fly: applies custom fonts, markup styles stacking (bold, italic, monospace, spoilers, quotes), Zalgo glyphs, Leet Speak, smart anti-caps, and affixes.\n\nAll changes happen locally right before sending to Telegram.",
        "welcome_pill1": "Markup Styles",
        "welcome_pill2": "100% Local",
        "welcome_pill3": "Zalgo & Leet",
        "welcome_btn_continue": "Accept and Continue",
        "about_plugin_title": "About Plugin",

        "zalgo_lvl": "Zalgo Power",
        "lvl_light": "Light",
        "lvl_mid": "Medium",
        "lvl_hard": "Hard",
        "lvl_max": "Extreme",
        "format_modifiers_header": "Modifiers",
        "s_bold": "Bold",
        "s_italic": "Italic",
        "s_bold_italic": "Bold + Italic",
        "s_mono": "Monospace",
        "s_code": "Code Block",
        "code_lang": "Code Language",
        "code_lang_default": "No language (default block)",
        "dlg_code_lang_title": "Code Block Language",
        "dlg_code_lang_hint": "e.g. python, bash, json, cpp, js...",
        "msg_code_lang_saved": "Code language saved!",
        "code_lang_shadow": "Specify the language or header for code block (e.g. python, json, bash). If empty — default code block.",
        "s_quote": "Blockquote",
        "s_strike": "Strikethrough",
        "s_spoiler": "Spoiler",
        "s_underline": "Underline",
        "s_random": "Random Style",
        "format_random_pool_btn": "Random Style Pool Settings",
        "font_squares": "Squares",
        "font_bubbles": "Bubbles",

        "modes_header": "Text Processing Modes",
        "m_vaporwave": "Vaporwave ( Ａ Ｅ Ｓ )",
        "vaporwave_hint": "Works only with English letters, numbers, and punctuation.",
        "m_zalgo": "Zalgo Text",
        "zalgo_warn_title": "Warning",
        "zalgo_warn_msg": "Attention! Using Zalgo text may be negatively perceived by moderation bots and lead to mutes or kicks in group chats.\n\nUse with caution. The plugin author is not responsible for any restrictions received.",
        "anticaps_header": "Anti-caps Settings",
        "m_anti_caps": "Anti-caps",
        "anti_caps_pct": "Caps Threshold",
        "anti_caps_capitalize": "Capitalize First Letter",
        "affix_header": "Message Signature",
        "prefix_text": "Prefix",
        "suffix_text": "Suffix",
        "dlg_prefix_title": "Text Prefix",
        "dlg_prefix_hint": "Enter prefix (e.g. [TM] or » )",
        "dlg_suffix_title": "Text Suffix",
        "dlg_suffix_hint": "Enter suffix (e.g. ~ or // TM)",
        "msg_prefix_saved": "Prefix saved!",
        "msg_suffix_saved": "Suffix saved!",
        "not_set": "Not set",
        "dlg_btn_clear": "Clear",
        "presuf_alert_shadow": "Insert spaces manually (when spacing from the main message is needed)",
        "m_leet_speak": "Leet Speak",
        "m_custom_font": "Unicode Font",
        "font_normal": "Normal",
        "font_bold": "Math Bold",
        "font_gothic": "Gothic",
        "font_cursive": "Cursive",
        "font_double": "Double Struck",
        
        "font_en_only_hint": "Fonts only work for the English alphabet.",
        "enable_modifier": "Enable Modifier",
        "leet_add_en_btn": "Add (English)",
        "leet_add_ru_btn": "Add (Russian)",
        "leet_err_not_en": "Error: English letter expected.",
        "leet_err_not_ru": "Error: Russian letter expected.",
        "leet_add_desc": "Enter a new letter and symbol",
        "leet_err_length": "Error: Letter must be exactly 1 character.",
        "leet_err_not_letter": "Error: Only letters are allowed.",
        "leet_err_exists": "Error: This letter is already in the list.",
        "leet_msg_added": "Substitution added!",
        "leet_msg_removed": "Substitution removed.",
        "leet_reset_btn": "Reset to Defaults",
        "leet_msg_reset": "Leet Speak settings reset.",
        "leet_msg_edited": "Substitution edited!",
        "dlg_leet_edit_title": "Edit {val}",
        "dlg_btn_save": "Save",
        "leet_hint_full": "Tap to remove. Long press to edit symbol.",
        "leet_delete_hint": "Tap on an item to remove it.",
        "dlg_leet_title": "Add Substitution",
        "dlg_leet_char_hint": "Letter (e.g., A)",
        "dlg_leet_rep_hint": "Symbol (e.g., 4 or @)",

        "exc_header": "Chat Exceptions",
        "exc_title": "Ignored Chats",
        "exc_sub": "Exceptions: {val}",
        "exc_manage_title": "Manage Exceptions",
        "exc_add_btn": "Add by ID",
        "exc_add_desc": "Enter chat ID manually",
        "exc_active_list": "Exceptions List",
        "exc_list_empty": "Exceptions list is currently empty",
        "exc_item_id": "ID: {val}",
        "exc_item_username": " • {val}",
        "exc_delete_hint": "Tap on a chat in the list to remove it from exceptions.",
        "exc_clear_all": "Clear Entire List",
        "msg_enabled": "Text Morph enabled for this chat.",
        "msg_disabled": "Text Morph disabled for this chat.",
        "msg_removed": "Removed from exceptions.",
        "msg_cleared": "List entirely cleared.",
        "msg_added_dialog": "Chat added to exceptions.",
        "msg_invalid_id": "Invalid ID entered.",
        "dlg_exc_add_title": "Add Exception Chat",
        "dlg_exc_add_hint": "-100123456789",
        "dlg_btn_add": "Add",
        "dlg_btn_cancel": "Cancel"
    }
}

def get_lang() -> str:
    try:
        inst = LocaleController.getInstance()
        if inst and inst.getCurrentLocale():
            return "ru" if inst.getCurrentLocale().getLanguage() == "ru" else "en"
    except Exception:
        pass
    return "ru"

LANG = get_lang()
ZALGO_MARKS = [chr(i) for i in range(0x0300, 0x036F)]
DEFAULT_LEET = {
    "A": "4", "E": "3", "I": "1", "O": "0", "S": "5", "T": "7", "B": "8", "G": "6", "Z": "2",
    "А": "4", "Е": "3", "О": "0", "С": "5", "Т": "7", "З": "3", "Б": "6", "В": "8", "Ч": "4"
}
NORMAL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

ANTI_CAPS_THRESHOLDS = {0: 30, 1: 50, 2: 70, 3: 90, 4: 100}
ZALGO_INTENSITIES = {0: 1, 1: 4, 2: 8, 3: 15}

STYLE_MAP = {
    "bold": getattr(TLRPC, "TL_messageEntityBold", None),
    "italic": getattr(TLRPC, "TL_messageEntityItalic", None),
    "mono": getattr(TLRPC, "TL_messageEntityCode", getattr(TLRPC, "TL_messageEntityPre", None)),
    "code": getattr(TLRPC, "TL_messageEntityPre", None),
    "quote": getattr(TLRPC, "TL_messageEntityBlockquote", None),
    "strike": getattr(TLRPC, "TL_messageEntityStrike", None),
    "spoiler": getattr(TLRPC, "TL_messageEntitySpoiler", None),
    "underline": getattr(TLRPC, "TL_messageEntityUnderline", None)
}

FONT_MAPS = {
    1: str.maketrans(NORMAL_CHARS, "𝗔𝗕𝗖𝗗𝗘𝗙𝗚𝗛𝗜𝗝𝗞𝗟𝗠𝗡𝗢𝗣𝗤𝗥𝗦𝗧𝗨𝗩𝗪𝗫𝗬𝗭𝗮𝗯𝗰𝗱𝗲𝗳𝗴𝗵𝗶𝗷𝗸𝗹𝗺𝗻𝗼𝗽𝗾𝗿𝘀𝘁𝘂𝘃𝘄𝘅𝘆𝘇"),
    2: str.maketrans(NORMAL_CHARS, "𝕬𝕭𝕮𝕯𝕰𝕱𝕲𝕳𝕴𝕵𝕶𝕷𝕸𝕹𝕺𝕻𝕼𝕽𝕾𝕿𝖀𝖁𝖂𝖃𝖄𝖅𝖆𝖇𝖈𝖉𝖊𝖋𝖌𝖍𝖎𝖏𝖐𝖑𝖒𝖓𝖔𝖕𝖖𝖗𝖘𝖙𝖚𝖛𝖜𝖝𝖞𝖟"),
    3: str.maketrans(NORMAL_CHARS, "𝓐𝓑𝓒𝓓𝓔𝓕𝓖𝓗𝓘𝓙𝓚𝓛𝓜𝓝𝓞𝓟𝓠𝓡𝓢𝓣𝓤𝓥𝓦𝓧𝓨𝓩𝓪𝓫𝓬𝓭𝓮𝓯𝓰𝓱𝓲𝓳𝓴𝓵𝓶𝓷𝓸𝓹𝓺𝓻𝓼𝘁𝘂𝘃𝘄𝔁𝔂𝔃"),
    4: str.maketrans(NORMAL_CHARS, "𝔸𝔹ℂ𝔻𝔼𝔽𝔾ℍ𝕀𝕁𝕂𝕃𝕄ℕ𝕆ℙℚℝ𝕊𝕋𝕌𝕍𝕎𝕏𝕐ℤ𝕒𝫔𝕔𝕕𝕖𝕗𝕘𝕙𝕚𝕛𝕜𝕝𝕞𝕟오𝕡𝕢𝕣𝕤𝕥𝕦𝕧𝕨𝕩𝕪𝕫")
}

def T(key: str) -> str:
    return STRINGS.get(LANG, STRINGS["en"]).get(key, key)

def c(val: int) -> int:
    val = val & 0xFFFFFFFF
    return val - 0x100000000 if val > 0x7FFFFFFF else val

def dp(val: float) -> int:
    try:
        if AndroidUtilities:
            return AndroidUtilities.dp(float(val))
    except Exception:
        pass
    return int(val * 2)

def is_dark_theme() -> bool:
    try:
        if Theme:
            return Theme.isCurrentThemeDark()
    except Exception:
        pass
    return True

def safe_set_click_listener(view, callback):
    if not view or not callback:
        return
    if OnClickListener:
        try:
            view.setOnClickListener(OnClickListener(lambda *a: callback()))
            return
        except Exception:
            pass
    try:
        view.setOnClickListener(lambda *a: callback())
    except Exception:
        pass

def set_view_click(view, callback):
    safe_set_click_listener(view, callback)

def create_styled_edit_text(ctx, hint_text: str = "", default_text: str = "", input_type: int = None):
    dark = is_dark_theme()
    edit = EditText(ctx)
    if hint_text:
        edit.setHint(hint_text)
    if default_text:
        edit.setText(default_text)
    edit.setTextSize(1, 15.0)
    edit.setTextColor(c(0xFFF1F5F9 if dark else 0xFF0F172A))
    edit.setHintTextColor(c(0xFF64748B if dark else 0xFF94A3B8))
    if input_type is not None:
        edit.setInputType(input_type)
    
    et_bg = GradientDrawable()
    et_bg.setColor(c(0x250F172A if dark else 0x10000000))
    et_bg.setCornerRadius(float(dp(12)))
    et_bg.setStroke(dp(1), c(0x5038BDF8 if dark else 0x400284C7))
    edit.setBackground(et_bg)
    edit.setPadding(dp(14), dp(12), dp(14), dp(12))

    if default_text:
        try:
            edit.setSelection(len(default_text))
        except Exception:
            pass
    return edit


def get_tg_drawable(ctx, *names):
    if not ctx:
        return 0
    try:
        res = ctx.getResources()
        pkg = ctx.getPackageName()
        for name in names:
            rid = res.getIdentifier(str(name), "drawable", pkg)
            if rid != 0:
                return rid
    except Exception:
        pass
    return 0

def load_web_avatar(icon_view, avatar_inner=None, dp_func=None, url="https://github.com/MrNeoner.png"):
    if not icon_view:
        return

    def _circle_crop_bytes(raw_bytes: bytes) -> bytes:
        try:
            from PIL import Image, ImageDraw
            import io
            im = Image.open(io.BytesIO(raw_bytes)).convert("RGBA")
            w, h = im.size
            min_dim = min(w, h)
            left = (w - min_dim) // 2
            top = (h - min_dim) // 2
            im = im.crop((left, top, left + min_dim, top + min_dim))

            scale = 4
            big_dim = min_dim * scale
            big_mask = Image.new("L", (big_dim, big_dim), 0)
            draw = ImageDraw.Draw(big_mask)
            draw.ellipse((0, 0, big_dim, big_dim), fill=255)
            mask = big_mask.resize((min_dim, min_dim), Image.Resampling.LANCZOS)

            im.putalpha(mask)

            out = io.BytesIO()
            im.save(out, format="PNG")
            return out.getvalue()
        except Exception:
            return raw_bytes

    def _fetch():
        try:
            import urllib.request
            import ssl
            import os

            cache_file = ""
            try:
                if ApplicationLoader is not None:
                    fdir = str(ApplicationLoader.getFilesDirFixed().getAbsolutePath())
                    cache_dir = os.path.join(fdir, "cache", "textmorph_res")
                    os.makedirs(cache_dir, exist_ok=True)
                    cache_file = os.path.join(cache_dir, "web_avatar_circle_v4.png")
            except Exception:
                pass

            if cache_file and os.path.exists(cache_file) and os.path.getsize(cache_file) > 100:
                try:
                    if BitmapFactory is not None and run_on_ui_thread:
                        bmp = BitmapFactory.decodeFile(cache_file)
                        if bmp is not None:
                            def apply_cached():
                                try:
                                    if avatar_inner and dp_func:
                                        avatar_inner.setPadding(dp_func(2.5), dp_func(2.5), dp_func(2.5), dp_func(2.5))
                                    icon_view.clearColorFilter()
                                    icon_view.setImageBitmap(bmp)
                                except Exception:
                                    pass
                            run_on_ui_thread(apply_cached)
                except Exception:
                    pass

            ssl_ctx = ssl.create_default_context()
            ssl_ctx.check_hostname = False
            ssl_ctx.verify_mode = ssl.CERT_NONE

            req = urllib.request.Request(
                url,
                headers={"User-Agent": "TextMorph/1.2 (Android)"}
            )
            with urllib.request.urlopen(req, timeout=12, context=ssl_ctx) as resp:
                raw_data = resp.read()

            if raw_data and len(raw_data) > 100:
                circular_data = _circle_crop_bytes(raw_data)

                if cache_file:
                    try:
                        with open(cache_file, "wb") as f:
                            f.write(circular_data)
                    except Exception:
                        pass

                if BitmapFactory is not None and run_on_ui_thread:
                    bmp = None
                    if cache_file and os.path.exists(cache_file):
                        try:
                            bmp = BitmapFactory.decodeFile(cache_file)
                        except Exception:
                            pass
                    if bmp is None:
                        try:
                            bmp = BitmapFactory.decodeByteArray(circular_data, 0, len(circular_data))
                        except Exception:
                            pass

                    if bmp is not None:
                        def update_avatar():
                            try:
                                if avatar_inner and dp_func:
                                    avatar_inner.setPadding(dp_func(2.5), dp_func(2.5), dp_func(2.5), dp_func(2.5))
                                icon_view.clearColorFilter()
                                icon_view.setImageBitmap(bmp)
                            except Exception:
                                pass
                        run_on_ui_thread(update_avatar)
        except Exception:
            pass

    import threading
    t = threading.Thread(target=_fetch, daemon=True)
    t.start()


def load_web_banner(bg_view, bg_container, dp_func, url):
    if not url or bg_view is None:
        return

    urls = [url] if isinstance(url, str) else list(url)

    def _fetch_banner():
        try:
            import urllib.request
            import ssl
            import hashlib
            import os

            for u in urls:
                if not u:
                    continue
                try:
                    cache_file = ""
                    try:
                        if ApplicationLoader is not None:
                            fdir = str(ApplicationLoader.getFilesDirFixed().getAbsolutePath())
                            cdir = os.path.join(fdir, "cache", "textmorph_ui")
                            os.makedirs(cdir, exist_ok=True)
                            h = hashlib.md5(u.encode("utf-8")).hexdigest()[:12]
                            cache_file = os.path.join(cdir, f"web_banner_{h}.png")
                    except Exception:
                        pass

                    if cache_file and os.path.exists(cache_file) and os.path.getsize(cache_file) > 100:
                        try:
                            if BitmapFactory is not None and run_on_ui_thread:
                                bmp = BitmapFactory.decodeFile(cache_file)
                                if bmp is not None:
                                    run_on_ui_thread(lambda b=bmp: bg_view.setImageBitmap(b))
                                    return
                        except Exception:
                            pass

                    ssl_ctx = ssl.create_default_context()
                    ssl_ctx.check_hostname = False
                    ssl_ctx.verify_mode = ssl.CERT_NONE

                    req = urllib.request.Request(
                        u,
                        headers={"User-Agent": "TextMorph/1.2 (Android)"}
                    )
                    with urllib.request.urlopen(req, timeout=12, context=ssl_ctx) as resp:
                        raw_data = resp.read()

                    if raw_data and len(raw_data) > 100:
                        if cache_file:
                            try:
                                with open(cache_file, "wb") as f:
                                    f.write(raw_data)
                            except Exception:
                                pass

                        if BitmapFactory is not None and run_on_ui_thread:
                            bmp = None
                            if cache_file and os.path.exists(cache_file):
                                try:
                                    bmp = BitmapFactory.decodeFile(cache_file)
                                except Exception:
                                    pass
                            if bmp is None:
                                try:
                                    bmp = BitmapFactory.decodeByteArray(raw_data, 0, len(raw_data))
                                except Exception:
                                    pass
                            if bmp is not None:
                                run_on_ui_thread(lambda b=bmp: bg_view.setImageBitmap(b))
                                return
                except Exception:
                    continue
        except Exception:
            pass

    import threading
    t = threading.Thread(target=_fetch_banner, daemon=True)
    t.start()


class TextMorphPlugin(BasePlugin):
    def on_plugin_load(self):
        self.add_on_send_message_hook()
        
        self.add_menu_item(
            MenuItemData(
                menu_type=MenuItemType.CHAT_ACTION_MENU, 
                text=T("menu_chat_btn"), 
                on_click=self.toggle_chat_ignore, 
                icon="msg_contacts", 
                subtext=T("menu_chat_sub")
            )
        )
        self.add_menu_item(
            MenuItemData(
                menu_type=MenuItemType.CHAT_ACTION_MENU, 
                text=T("menu_global_btn"), 
                on_click=self.toggle_plugin_global, 
                icon="msg_settings", 
                subtext=T("menu_global_sub")
            )
        )

    def update_setting_value(self, key: str, value: Any):
        self.set_setting(key, value, reload_settings=True)

    def handle_switch(self, key: str, val: bool):
        all_styles = ["s_bold", "s_italic", "s_bold_italic", "s_mono", "s_code", "s_quote", "s_strike", "s_spoiler", "s_underline"]
        
        if key == "s_random":
            if val:
                for k in all_styles:
                    self.set_setting(k, False, reload_settings=False)
                self.set_setting("s_random", True, reload_settings=True)
            else:
                self.set_setting("s_random", False, reload_settings=True)
            return

        if val:
            self.set_setting("s_random", False, reload_settings=False)

            # Rule 1: Code block (Pre) is a standalone block in Telegram, not stackable with text styling
            if key == "s_code":
                for k in ["s_bold", "s_italic", "s_bold_italic", "s_mono", "s_quote", "s_strike", "s_spoiler", "s_underline"]:
                    self.set_setting(k, False, reload_settings=False)
            else:
                self.set_setting("s_code", False, reload_settings=False)

            # Rule 2: Monospace inline (Code) is clean text, mutually exclusive with Bold, Italic, Bold+Italic, and Spoiler
            if key == "s_mono":
                self.set_setting("s_bold", False, reload_settings=False)
                self.set_setting("s_italic", False, reload_settings=False)
                self.set_setting("s_bold_italic", False, reload_settings=False)
                self.set_setting("s_spoiler", False, reload_settings=False)
            elif key in ["s_bold", "s_italic", "s_bold_italic", "s_spoiler"]:
                if key == "s_spoiler":
                    self.set_setting("s_mono", False, reload_settings=False)
                else:
                    self.set_setting("s_mono", False, reload_settings=False)
                    if key == "s_bold_italic":
                        self.set_setting("s_bold", False, reload_settings=False)
                        self.set_setting("s_italic", False, reload_settings=False)
                    elif key in ["s_bold", "s_italic"]:
                        self.set_setting("s_bold_italic", False, reload_settings=False)
            
            self.set_setting(key, True, reload_settings=True)
        else:
            self.set_setting(key, False, reload_settings=True)

    def handle_font_change(self, val: Any):
        v = int(val)
        if v > 0:
            self.set_setting("m_vaporwave", False, reload_settings=False)
        self.set_setting("m_custom_font", v, reload_settings=True)

    def handle_vaporwave_change(self, val: bool):
        if val:
            self.set_setting("m_custom_font", 0, reload_settings=False)
        self.set_setting("m_vaporwave", val, reload_settings=True)

    def complete_onboarding(self):
        self.set_setting("onboarding_completed", True, reload_settings=True)

    def reset_onboarding(self):
        self.set_setting("onboarding_completed", False, reload_settings=True)

    def on_version_click(self, view: Any):
        fragment = get_last_fragment()
        ctx = fragment.getParentActivity() if fragment else None
        if ctx:
            self.show_about_sheet(ctx)

    def on_version_long_click(self, view: Any):
        try:
            BulletinHelper.show_success(T("welcome_opened"))
            self.reset_onboarding()
        except Exception:
            pass
        return True

    def toggle_plugin_global(self, context: Dict[str, Any]):
        curr = self.get_setting("enabled", True)
        self.set_setting("enabled", not curr)
        st = T("state_on") if not curr else T("state_off")
        BulletinHelper.show_success(T("toast_plugin_state").replace("{val}", st))

    def get_dialog_info(self, dialog_id: int):
        try:
            ctrl = MessagesController.getInstance(UserConfig.selectedAccount)
            if dialog_id > 0:
                user = ctrl.getUser(dialog_id)
                if user:
                    fn = getattr(user, "first_name", "") or ""
                    ln = getattr(user, "last_name", "") or ""
                    name = f"{fn} {ln}".strip() or getattr(user, "username", "") or T("unk_name")
                    return name, getattr(user, "username", "")
            else:
                chat = ctrl.getChat(-dialog_id)
                if chat:
                    return getattr(chat, "title", "") or T("unk_chat"), getattr(chat, "username", "")
        except Exception:
            pass
        return None, None

    def open_tg_link(self, domain: str):
        try:
            from android.content import Intent
            from android.net import Uri
            from org.telegram.messenger import ApplicationLoader
            intent = Intent(Intent.ACTION_VIEW, Uri.parse(f"tg://resolve?domain={domain}"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ApplicationLoader.applicationContext.startActivity(intent)
        except Exception:
            pass

    def copy_text(self, text: str):
        AndroidUtilities.addToClipboard(text)
        BulletinHelper.show_success(T("link_copied"))

    def share_link(self, domain: str):
        try:
            from android.content import Intent
            from org.telegram.messenger import ApplicationLoader
            intent = Intent(Intent.ACTION_SEND)
            intent.setType("text/plain")
            intent.putExtra(Intent.EXTRA_TEXT, f"https://t.me/{domain}")
            intent = Intent.createChooser(intent, T("share_link_title"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ApplicationLoader.applicationContext.startActivity(intent)
        except Exception:
            pass

    def on_link_long_click(self, domain: str) -> bool:
        fragment = get_last_fragment()
        ctx = fragment.getParentActivity() if fragment else None
        if not ctx: return False
        
        builder = AlertDialogBuilder(ctx)
        builder.set_title(f"@{domain}")
        options = [T("copy_link"), T("share_link")]
        
        def menu_callback(d, w):
            if w == 0: self.copy_text(f"https://t.me/{domain}")
            elif w == 1: self.share_link(domain)
            d.dismiss()
            
        builder.set_items(options, menu_callback)
        builder.show()
        return True

    def get_all_device_accounts(self) -> List[Dict[str, Any]]:
        accs = []
        try:
            max_acc = getattr(UserConfig, "MAX_ACCOUNT_COUNT", 4)
            sel_acc = getattr(UserConfig, "selectedAccount", -1)
            for i in range(max_acc):
                if UserConfig.isValidAccount(i):
                    u_inst = UserConfig.getInstance(i)
                    uid = int(u_inst.getClientUserId())
                    user = u_inst.getCurrentUser()
                    first = getattr(user, "first_name", "") or ""
                    last = getattr(user, "last_name", "") or ""
                    full_name = f"{first} {last}".strip()
                    if not full_name:
                        full_name = f"Account #{i + 1}"
                    uname = getattr(user, "username", "") or ""
                    accs.append({
                        "acc_idx": i,
                        "user_id": uid,
                        "name": full_name,
                        "username": uname,
                        "is_current": (i == sel_acc),
                        "user": user
                    })
        except Exception:
            pass
        return accs

    def toggle_account_allowed(self, user_id: int, enabled: bool):
        accs = list(self.get_setting("allowed_accounts", []))
        if enabled:
            if user_id not in accs:
                accs.append(user_id)
        else:
            if user_id in accs:
                accs.remove(user_id)
        self.set_setting("allowed_accounts", accs, reload_settings=True)
        BulletinHelper.show_success(T("msg_acc_added") if enabled else T("msg_acc_removed"))

    def create_user_account_cell(self, ctx, acc_data: Dict[str, Any], is_allowed: bool) -> View:
        if ctx is None or FrameLayout is None or LinearLayout is None:
            return None

        try:
            dark = is_dark_theme()
            uid = acc_data["user_id"]
            user = acc_data.get("user")
            name = acc_data["name"]
            uname = acc_data.get("username", "")
            is_cur = acc_data.get("is_current", False)
            acc_idx = acc_data.get("acc_idx", 0)

            wrapper = FrameLayout(ctx)
            wrapper.setPadding(dp(14), dp(4), dp(14), dp(4))

            card = LinearLayout(ctx)
            card.setOrientation(LinearLayout.HORIZONTAL)
            card.setGravity(Gravity.CENTER_VERTICAL)
            card.setPadding(dp(14), dp(10), dp(14), dp(10))

            card_bg = GradientDrawable()
            if is_allowed:
                card_bg.setColor(c(0x2238BDF8 if dark else 0x120284C7))
                card_bg.setStroke(dp(1.2), c(0x7038BDF8 if dark else 0x500284C7))
            else:
                card_bg.setColor(c(0x181E293B if dark else 0x08000000))
                card_bg.setStroke(dp(1), c(0x25FFFFFF if dark else 0x15000000))
            card_bg.setCornerRadius(float(dp(14)))
            card.setBackground(card_bg)

            # 1. Avatar Container
            avatar_container = FrameLayout(ctx)
            av_lp = LinearLayout.LayoutParams(dp(44), dp(44))
            av_lp.gravity = Gravity.CENTER_VERTICAL

            avatar_added = False
            if BackupImageView is not None and user is not None:
                try:
                    biv = BackupImageView(ctx)
                    biv.setRoundRadius(dp(22))
                    av_draw = None
                    if AvatarDrawable is not None:
                        try:
                            av_draw = AvatarDrawable()
                            try:
                                av_draw.setTextSize(dp(16))
                            except Exception:
                                pass
                            try:
                                av_draw.setInfo(acc_idx, user)
                            except Exception:
                                try:
                                    av_draw.setInfo(user)
                                except Exception:
                                    pass
                        except Exception:
                            av_draw = None

                    try:
                        biv.getImageReceiver().setCurrentAccount(acc_idx)
                    except Exception:
                        pass

                    if av_draw is not None:
                        biv.setForUserOrChat(user, av_draw)
                    else:
                        try:
                            biv.setImageObject(user, "50_50", None)
                        except Exception:
                            pass

                    avatar_container.addView(biv, FrameLayout.LayoutParams(dp(44), dp(44)))
                    avatar_added = True
                except Exception:
                    avatar_added = False

            if not avatar_added:
                fallback_bg = GradientDrawable()
                fallback_bg.setShape(GradientDrawable.OVAL)
                fallback_bg.setColor(c(0xFF38BDF8 if is_allowed else (0xFF334155 if dark else 0xFFCBD5E1)))
                avatar_container.setBackground(fallback_bg)
                init_tv = TextView(ctx)
                init_tv.setText(name[:1].upper() if name else "U")
                init_tv.setTextSize(1, 16.0)
                init_tv.setTextColor(c(0xFFFFFFFF))
                try:
                    init_tv.setTypeface(Typeface.DEFAULT_BOLD)
                except Exception:
                    pass
                init_tv.setGravity(Gravity.CENTER)
                avatar_container.addView(init_tv, FrameLayout.LayoutParams(-1, -1))

            card.addView(avatar_container, av_lp)

            # 2. Text Info Column
            info_col = LinearLayout(ctx)
            info_col.setOrientation(LinearLayout.VERTICAL)
            info_lp = LinearLayout.LayoutParams(0, -2, 1.0)
            info_lp.setMargins(dp(12), 0, dp(8), 0)

            name_row = LinearLayout(ctx)
            name_row.setOrientation(LinearLayout.HORIZONTAL)
            name_row.setGravity(Gravity.CENTER_VERTICAL)

            name_tv = TextView(ctx)
            name_tv.setText(name)
            name_tv.setTextSize(1, 15.0)
            name_tv.setTextColor(c(0xFFF8FAFC if dark else 0xFF0F172A))
            try:
                name_tv.setTypeface(Typeface.DEFAULT_BOLD)
            except Exception:
                pass
            name_row.addView(name_tv, LinearLayout.LayoutParams(-2, -2))

            if is_cur:
                cur_badge = TextView(ctx)
                cur_badge.setText(T("acc_current_badge"))
                cur_badge.setTextSize(1, 10.5)
                cur_badge.setTextColor(c(0xFF38BDF8 if dark else 0xFF0284C7))
                cur_badge.setPadding(dp(6), dp(1.5), dp(6), dp(1.5))
                c_bg = GradientDrawable()
                c_bg.setColor(c(0x2538BDF8 if dark else 0x180284C7))
                c_bg.setCornerRadius(float(dp(6)))
                cur_badge.setBackground(c_bg)
                cb_lp = LinearLayout.LayoutParams(-2, -2)
                cb_lp.setMargins(dp(6), 0, 0, 0)
                name_row.addView(cur_badge, cb_lp)

            info_col.addView(name_row, LinearLayout.LayoutParams(-1, -2))

            sub_tv = TextView(ctx)
            sub_text = f"@{uname} • ID: {uid}" if uname else f"ID: {uid}"
            sub_tv.setText(sub_text)
            sub_tv.setTextSize(1, 12.0)
            sub_tv.setTextColor(c(0xFF94A3B8 if dark else 0xFF64748B))
            info_col.addView(sub_tv, LinearLayout.LayoutParams(-1, -2))

            card.addView(info_col, info_lp)

            # 3. Status Pill Button
            pill_btn = TextView(ctx)
            pill_btn.setTextSize(1, 12.0)
            pill_btn.setPadding(dp(10), dp(5), dp(10), dp(5))
            pill_btn.setGravity(Gravity.CENTER)
            try:
                pill_btn.setTypeface(Typeface.DEFAULT_BOLD)
            except Exception:
                pass

            p_bg = GradientDrawable()
            p_bg.setCornerRadius(float(dp(10)))
            if is_allowed:
                pill_btn.setText(f"✓ {T('acc_status_active')}")
                pill_btn.setTextColor(c(0xFFFFFFFF))
                p_bg.setColor(c(0xFF0284C7 if dark else 0xFF0369A1))
            else:
                pill_btn.setText(f"+ {T('acc_status_inactive')}")
                pill_btn.setTextColor(c(0xFF94A3B8 if dark else 0xFF64748B))
                p_bg.setColor(c(0x20FFFFFF if dark else 0x10000000))
                p_bg.setStroke(dp(1), c(0x35FFFFFF if dark else 0x20000000))
            pill_btn.setBackground(p_bg)

            card.addView(pill_btn, LinearLayout.LayoutParams(-2, -2))
            wrapper.addView(card, FrameLayout.LayoutParams(-1, -2))

            toggle_fn = lambda *a, u=uid, en=(not is_allowed): self.toggle_account_allowed(u, en)
            safe_set_click_listener(wrapper, toggle_fn)
            safe_set_click_listener(card, toggle_fn)
            safe_set_click_listener(pill_btn, toggle_fn)
            return wrapper
        except Exception:
            return None

    def show_add_account_id_dialog(self, view):
        fragment = get_last_fragment()
        ctx = fragment.getParentActivity() if fragment else None
        if not ctx: return

        layout = LinearLayout(ctx)
        layout.setOrientation(LinearLayout.VERTICAL)
        layout.setPadding(dp(22), dp(12), dp(22), dp(6))

        edit_text = create_styled_edit_text(
            ctx,
            hint_text="123456789",
            default_text="",
            input_type=InputType.TYPE_CLASS_NUMBER
        )
        layout.addView(edit_text, LinearLayout.LayoutParams(-1, -2))

        builder = AlertDialogBuilder(ctx)
        builder.set_title(T("dlg_acc_add_title"))
        builder.set_view(layout)

        def on_add(bld, w):
            val = edit_text.getText().toString().strip()
            try:
                uid = int(val)
                accs = list(self.get_setting("allowed_accounts", []))
                if uid not in accs:
                    accs.append(uid)
                    self.set_setting("allowed_accounts", accs, reload_settings=True)
                    BulletinHelper.show_success(T("msg_acc_added"))
                else:
                    BulletinHelper.show_error(T("msg_acc_exists"))
                bld.dismiss()
            except Exception:
                BulletinHelper.show_error(T("msg_invalid_id"))

        builder.set_positive_button(T("dlg_btn_add"), on_add)
        builder.set_negative_button(T("dlg_btn_cancel"), lambda b, w: b.dismiss())
        builder.show()

    def remove_account(self, view, user_id: int):
        accs = list(self.get_setting("allowed_accounts", []))
        if user_id in accs:
            accs.remove(user_id)
            self.set_setting("allowed_accounts", accs, reload_settings=True)
            BulletinHelper.show_success(T("msg_acc_removed"))

    def clear_allowed_accounts(self, view):
        self.set_setting("allowed_accounts", [], reload_settings=True)
        BulletinHelper.show_success(T("msg_acc_cleared"))

    def create_accounts_page(self) -> List[Any]:
        items = [
            Header(text=T("acc_manage_title")),
            Switch(
                key="work_on_all_accounts", 
                text=T("acc_all_toggle"), 
                default=True, 
                icon="msg_user",
                on_change=lambda c: self.update_setting_value("work_on_all_accounts", c)
            )
        ]
        
        is_all = self.get_setting("work_on_all_accounts", True)
        if is_all:
            items.append(Custom(item=UItem.asShadow(T("acc_all_shadow"))))
            return items

        items.append(Custom(item=UItem.asShadow(T("acc_specific_shadow"))))

        fragment = get_last_fragment()
        ctx = None
        if fragment is not None:
            try:
                ctx = fragment.getParentActivity()
            except Exception:
                pass
            if not ctx:
                try:
                    ctx = fragment.getContext()
                except Exception:
                    pass
        if not ctx and ApplicationLoader is not None:
            try:
                ctx = ApplicationLoader.applicationContext
            except Exception:
                pass

        device_accs = self.get_all_device_accounts()
        allowed_list = self.get_setting("allowed_accounts", [])
        allowed_set = set(allowed_list)

        if device_accs:
            items.append(Header(text=T("acc_device_list_title")))
            for acc in device_accs:
                uid = acc["user_id"]
                is_on = uid in allowed_set
                card_view = None
                if ctx is not None:
                    try:
                        card_view = self.create_user_account_cell(ctx, acc, is_on)
                    except Exception:
                        card_view = None

                if card_view is not None:
                    items.append(Custom(view=card_view))
                else:
                    cur_mark = f" ({T('acc_current_badge')})" if acc["is_current"] else ""
                    title = f"{acc['name']}{cur_mark}"
                    if acc["username"]:
                        title = f"{title} • @{acc['username']}"
                    items.append(Switch(
                        key=f"acc_toggle_{uid}",
                        text=title,
                        default=is_on,
                        icon="msg_user",
                        on_change=lambda val, u=uid: self.toggle_account_allowed(u, bool(val))
                    ))
            items.append(Custom(item=UItem.asShadow(T("acc_device_hint"))))

        device_uids = {acc["user_id"] for acc in device_accs}
        custom_uids = [u for u in allowed_list if u not in device_uids]
        if custom_uids:
            items.append(Header(text=T("acc_other_list_title")))
            for uid in custom_uids:
                name, username = self.get_dialog_info(uid)
                title = name if name else T("acc_profile_id").replace("{val}", str(uid))
                sub = f"@{username} • ID: {uid}" if username else f"ID: {uid}"
                items.append(Text(text=title, subtext=sub, icon="msg_user", on_click=lambda v, uid=uid: self.remove_account(v, uid)))

        items.append(Header(text=T("acc_actions_header")))
        items.append(Text(text=T("exc_add_btn"), subtext=T("acc_add_manual_desc"), icon="msg_add", on_click=self.show_add_account_id_dialog))

        if allowed_list:
            items.append(Divider())
            items.append(Text(text=T("acc_clear_all"), icon="msg_delete", on_click=self.clear_allowed_accounts))

        return items

    def toggle_chat_ignore(self, context: Dict[str, Any]):
        dialog_id = context.get("dialog_id") or context.get("peer")
        if not dialog_id:
            BulletinHelper.show_error(T("toast_ui_error"))
            return
            
        ign = self.get_setting("ignored_chats", [])
        if dialog_id in ign:
            ign.remove(dialog_id)
            self.set_setting("ignored_chats", ign)
            name, _ = self.get_dialog_info(dialog_id)
            if not name: name = str(dialog_id)
            BulletinHelper.show_success(T("msg_enabled"))
        else:
            ign.append(dialog_id)
            self.set_setting("ignored_chats", ign)
            name, _ = self.get_dialog_info(dialog_id)
            if not name: name = str(dialog_id)
            BulletinHelper.show_error(T("msg_disabled"))

    def show_add_chat_dialog(self, view):
        fragment = get_last_fragment()
        ctx = fragment.getParentActivity() if fragment else None
        if not ctx: return

        layout = LinearLayout(ctx)
        layout.setOrientation(LinearLayout.VERTICAL)
        layout.setPadding(dp(22), dp(12), dp(22), dp(6))

        edit = create_styled_edit_text(ctx, hint_text=T("dlg_exc_add_hint"), input_type=(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED))
        layout.addView(edit, LinearLayout.LayoutParams(-1, -2))

        builder = AlertDialogBuilder(ctx)
        builder.set_title(T("dlg_exc_add_title"))
        builder.set_view(layout)

        def on_positive(bld, which):
            text = edit.getText().toString().strip()
            if text:
                try:
                    chat_id = int(text)
                    ign = self.get_setting("ignored_chats", [])
                    if chat_id not in ign:
                        ign.append(chat_id)
                        self.set_setting("ignored_chats", ign, reload_settings=True)
                        BulletinHelper.show_success(T("msg_added_dialog"))
                except ValueError:
                    BulletinHelper.show_error(T("msg_invalid_id"))
            bld.dismiss()

        builder.set_positive_button(T("dlg_btn_add"), on_positive)
        builder.set_negative_button(T("dlg_btn_cancel"), lambda bld, which: bld.dismiss())
        builder.show()

    def remove_ignored_chat(self, view, chat_id: int):
        ign = self.get_setting("ignored_chats", [])
        if chat_id in ign:
            ign.remove(chat_id)
            self.set_setting("ignored_chats", ign, reload_settings=True)
            BulletinHelper.show_success(T("msg_removed"))

    def clear_ignored_chats(self, view):
        self.set_setting("ignored_chats", [], reload_settings=True)
        BulletinHelper.show_success(T("msg_cleared"))

    def create_ignored_chats_page(self) -> List[Any]:
        items = [
            Header(text=T("exc_manage_title")),
            Text(text=T("exc_add_btn"), subtext=T("exc_add_desc"), icon="msg_add", on_click=self.show_add_chat_dialog),
            Header(text=T("exc_active_list"))
        ]
        
        ign = self.get_setting("ignored_chats", [])
        if not ign:
            items.append(Text(text=T("exc_list_empty"), icon="msg_info"))
        else:
            for cid in ign:
                name, username = self.get_dialog_info(cid)
                title = name if name else T("exc_item_id").replace("{val}", str(cid))
                sub = f"ID: {cid}" + (T("exc_item_username").replace("{val}", f"@{username}") if username else "")
                items.append(Text(text=title, subtext=sub, icon="msg_channel", on_click=lambda v, cid=cid: self.remove_ignored_chat(v, cid)))
            
            items.append(Custom(item=UItem.asShadow(T("exc_delete_hint"))))
            items.append(Text(text=T("exc_clear_all"), icon="msg_delete", on_click=self.clear_ignored_chats))
            
        return items

    def handle_zalgo_switch(self, val: bool):
        if not val:
            self.update_setting_value("m_zalgo", False)
            return

        # Keep setting as False until user explicitly confirms in dialog
        self.set_setting("m_zalgo", False, reload_settings=False)

        def show_dialog():
            fragment = get_last_fragment()
            ctx = fragment.getParentActivity() if fragment else None
            if not ctx:
                self.update_setting_value("m_zalgo", True)
                return

            builder = AlertDialogBuilder(ctx)
            builder.set_title(T("zalgo_warn_title"))
            builder.set_message(T("zalgo_warn_msg"))

            confirmed = [False]

            def on_confirm(d, w):
                confirmed[0] = True
                self.set_setting("m_zalgo", True, reload_settings=True)
                d.dismiss()

            def on_dismiss_or_cancel(*_):
                if not confirmed[0]:
                    self.set_setting("m_zalgo", False, reload_settings=True)

            builder.set_positive_button(T("enable_modifier"), on_confirm)
            builder.set_negative_button(T("dlg_btn_cancel"), lambda d, w: d.dismiss())

            for m in ("set_on_dismiss_listener", "setOnDismissListener"):
                if hasattr(builder, m):
                    try:
                        getattr(builder, m)(on_dismiss_or_cancel)
                    except Exception:
                        pass
            for m in ("set_on_cancel_listener", "setOnCancelListener"):
                if hasattr(builder, m):
                    try:
                        getattr(builder, m)(on_dismiss_or_cancel)
                    except Exception:
                        pass

            dlg = None
            try:
                dlg = builder.show()
            except Exception:
                pass

            if dlg is not None:
                for m in ("setOnDismissListener", "setOnCancelListener", "set_on_dismiss_listener", "set_on_cancel_listener"):
                    if hasattr(dlg, m):
                        try:
                            getattr(dlg, m)(on_dismiss_or_cancel)
                        except Exception:
                            pass

        run_on_ui_thread(show_dialog)

    def reset_leet_mappings(self, view):
        self.set_setting("leet_mappings", DEFAULT_LEET, reload_settings=True)
        BulletinHelper.show_success(T("leet_msg_reset"))

    def remove_leet_mapping(self, view, key: str):
        mappings = dict(self.get_setting("leet_mappings", DEFAULT_LEET))
        if key in mappings:
            del mappings[key]
            self.set_setting("leet_mappings", mappings, reload_settings=True)
            BulletinHelper.show_success(T("leet_msg_removed"))

    def show_edit_leet_dialog(self, view, char: str, current_rep: str) -> bool:
        fragment = get_last_fragment()
        ctx = fragment.getParentActivity() if fragment else None
        if not ctx: return False

        layout = LinearLayout(ctx)
        layout.setOrientation(LinearLayout.VERTICAL)
        layout.setPadding(dp(22), dp(12), dp(22), dp(6))

        edit = create_styled_edit_text(ctx, hint_text=T("dlg_leet_rep_hint"), default_text=current_rep)
        layout.addView(edit, LinearLayout.LayoutParams(-1, -2))

        builder = AlertDialogBuilder(ctx)
        builder.set_title(T("dlg_leet_edit_title").replace("{val}", char))
        builder.set_view(layout)

        def on_save(bld, w):
            new_rep = edit.getText().toString().strip()
            if new_rep:
                mappings = dict(self.get_setting("leet_mappings", DEFAULT_LEET))
                mappings[char] = new_rep
                self.set_setting("leet_mappings", mappings, reload_settings=True)
                BulletinHelper.show_success(T("leet_msg_edited"))
            bld.dismiss()

        builder.set_positive_button(T("dlg_btn_save"), on_save)
        builder.set_negative_button(T("dlg_btn_cancel"), lambda b, w: b.dismiss())
        builder.show()
        return True

    def show_add_leet_dialog(self, view, lang: str):
        fragment = get_last_fragment()
        ctx = fragment.getParentActivity() if fragment else None
        if not ctx: return

        layout = LinearLayout(ctx)
        layout.setOrientation(LinearLayout.VERTICAL)
        layout.setPadding(dp(22), dp(12), dp(22), dp(6))

        edit_char = create_styled_edit_text(ctx, hint_text=T("dlg_leet_char_hint"))
        layout.addView(edit_char, LinearLayout.LayoutParams(-1, -2))

        edit_rep = create_styled_edit_text(ctx, hint_text=T("dlg_leet_rep_hint"))
        rep_lp = LinearLayout.LayoutParams(-1, -2)
        rep_lp.topMargin = dp(10)
        layout.addView(edit_rep, rep_lp)

        builder = AlertDialogBuilder(ctx)
        builder.set_title(T("dlg_leet_title"))
        builder.set_view(layout)

        def on_add(bld, w):
            c_val = edit_char.getText().toString().strip().upper()
            r_val = edit_rep.getText().toString().strip()

            if len(c_val) != 1:
                BulletinHelper.show_error(T("leet_err_length"))
                return
            if not c_val.isalpha():
                BulletinHelper.show_error(T("leet_err_not_letter"))
                return

            if lang == "en" and not (ord('A') <= ord(c_val) <= ord('Z')):
                BulletinHelper.show_error(T("leet_err_not_en"))
                return
            if lang == "ru" and not ('А' <= c_val <= 'Я' or c_val == 'Ё'):
                BulletinHelper.show_error(T("leet_err_not_ru"))
                return

            mappings = dict(self.get_setting("leet_mappings", DEFAULT_LEET))
            if c_val in mappings:
                BulletinHelper.show_error(T("leet_err_exists"))
                return

            mappings[c_val] = r_val
            self.set_setting("leet_mappings", mappings, reload_settings=True)
            BulletinHelper.show_success(T("leet_msg_added"))
            bld.dismiss()

        builder.set_positive_button(T("dlg_btn_add"), on_add)
        builder.set_negative_button(T("dlg_btn_cancel"), lambda b, w: b.dismiss())
        builder.show()

    def create_leet_page(self) -> List[Any]:
        items = [
            Header(text=T("m_leet_speak")),
            Switch(
                key="m_leet_speak", 
                text=T("enable_modifier"), 
                default=False, 
                icon="msg_edit",
                on_change=lambda c: self.update_setting_value("m_leet_speak", c)
            ),
            Header(text=T("dlg_leet_title")),
            Text(text=T("leet_add_en_btn"), subtext=T("leet_add_desc"), icon="msg_add", on_click=lambda v: self.show_add_leet_dialog(v, "en")),
            Text(text=T("leet_add_ru_btn"), subtext=T("leet_add_desc"), icon="msg_add", on_click=lambda v: self.show_add_leet_dialog(v, "ru")),
            Header(text=T("m_leet_speak"))
        ]

        mappings = self.get_setting("leet_mappings", DEFAULT_LEET)
        sorted_keys = sorted(mappings.keys())

        for k in sorted_keys:
            items.append(
                Text(
                    text=f"{k} ➔ {mappings[k]}", 
                    icon="msg_edit", 
                    on_click=lambda v, key=k: self.remove_leet_mapping(v, key),
                    on_long_click=lambda v, key=k, rep=mappings[k]: self.show_edit_leet_dialog(v, key, rep)
                )
            )

        items.extend([
            Custom(item=UItem.asShadow(T("leet_hint_full"))),
            Text(text=T("leet_reset_btn"), icon="msg_retry", on_click=self.reset_leet_mappings)
        ])
        return items

    def create_chaos_pool_page(self) -> List[Any]:
        items = [
            Header(text=T("format_random_pool_btn")),
            Switch(key="chaos_pool_bold_italic", text=T("s_bold_italic"), default=True, icon="msg_edit", on_change=lambda c: self.update_setting_value("chaos_pool_bold_italic", c))
        ]
        for k in STYLE_MAP:
            items.append(Switch(key=f"chaos_pool_{k}", text=T(f"s_{k}"), default=True, icon="msg_edit", on_change=lambda c, key=f"chaos_pool_{k}": self.update_setting_value(key, c)))
        return items

    def show_edit_preview_dialog(self, ctx):
        if not ctx: return

        layout = LinearLayout(ctx)
        layout.setOrientation(LinearLayout.VERTICAL)
        layout.setPadding(dp(22), dp(12), dp(22), dp(6))

        current_val = self.get_setting("preview_sample_text", "")
        if not current_val:
            current_val = T("preview_default_text")

        edit_text = create_styled_edit_text(
            ctx, 
            hint_text=T("dlg_preview_edit_hint"), 
            default_text=current_val, 
            input_type=(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        )
        layout.addView(edit_text, LinearLayout.LayoutParams(-1, -2))

        builder = AlertDialogBuilder(ctx)
        builder.set_title(T("dlg_preview_edit_title"))
        builder.set_view(layout)

        def on_save(bld, w):
            new_text = edit_text.getText().toString().strip()
            self.set_setting("preview_sample_text", new_text, reload_settings=True)
            bld.dismiss()

        builder.set_positive_button(T("dlg_btn_save"), on_save)
        builder.set_negative_button(T("dlg_btn_cancel"), lambda b, w: b.dismiss())
        builder.show()

    def show_edit_prefix_dialog(self, view) -> bool:
        fragment = get_last_fragment()
        ctx = fragment.getParentActivity() if fragment else None
        if not ctx: return False

        layout = LinearLayout(ctx)
        layout.setOrientation(LinearLayout.VERTICAL)
        layout.setPadding(dp(22), dp(12), dp(22), dp(6))

        current_val = str(self.get_setting("prefix_text", "") or "")
        edit_text = create_styled_edit_text(
            ctx,
            hint_text=T("dlg_prefix_hint"),
            default_text=current_val,
            input_type=InputType.TYPE_CLASS_TEXT
        )
        layout.addView(edit_text, LinearLayout.LayoutParams(-1, -2))

        builder = AlertDialogBuilder(ctx)
        builder.set_title(T("dlg_prefix_title"))
        builder.set_view(layout)

        def on_save(bld, w):
            new_val = edit_text.getText().toString()
            self.set_setting("prefix_text", new_val, reload_settings=True)
            BulletinHelper.show_success(T("msg_prefix_saved"))
            bld.dismiss()

        def on_clear(bld, w):
            self.set_setting("prefix_text", "", reload_settings=True)
            BulletinHelper.show_success(T("msg_prefix_saved"))
            bld.dismiss()

        builder.set_positive_button(T("dlg_btn_save"), on_save)
        if current_val:
            builder.set_neutral_button(T("dlg_btn_clear"), on_clear)
        builder.set_negative_button(T("dlg_btn_cancel"), lambda b, w: b.dismiss())
        builder.show()
        return True

    def show_edit_suffix_dialog(self, view) -> bool:
        fragment = get_last_fragment()
        ctx = fragment.getParentActivity() if fragment else None
        if not ctx: return False

        layout = LinearLayout(ctx)
        layout.setOrientation(LinearLayout.VERTICAL)
        layout.setPadding(dp(22), dp(12), dp(22), dp(6))

        current_val = str(self.get_setting("suffix_text", "") or "")
        edit_text = create_styled_edit_text(
            ctx,
            hint_text=T("dlg_suffix_hint"),
            default_text=current_val,
            input_type=InputType.TYPE_CLASS_TEXT
        )
        layout.addView(edit_text, LinearLayout.LayoutParams(-1, -2))

        builder = AlertDialogBuilder(ctx)
        builder.set_title(T("dlg_suffix_title"))
        builder.set_view(layout)

        def on_save(bld, w):
            new_val = edit_text.getText().toString()
            self.set_setting("suffix_text", new_val, reload_settings=True)
            BulletinHelper.show_success(T("msg_suffix_saved"))
            bld.dismiss()

        def on_clear(bld, w):
            self.set_setting("suffix_text", "", reload_settings=True)
            BulletinHelper.show_success(T("msg_suffix_saved"))
            bld.dismiss()

        builder.set_positive_button(T("dlg_btn_save"), on_save)
        if current_val:
            builder.set_neutral_button(T("dlg_btn_clear"), on_clear)
        builder.set_negative_button(T("dlg_btn_cancel"), lambda b, w: b.dismiss())
        builder.show()
        return True

    def show_edit_code_lang_dialog(self, view) -> bool:
        fragment = get_last_fragment()
        ctx = fragment.getParentActivity() if fragment else None
        if not ctx: return False

        layout = LinearLayout(ctx)
        layout.setOrientation(LinearLayout.VERTICAL)
        layout.setPadding(dp(22), dp(12), dp(22), dp(6))

        current_val = str(self.get_setting("code_lang", "") or "")
        edit_text = create_styled_edit_text(
            ctx,
            hint_text=T("dlg_code_lang_hint"),
            default_text=current_val,
            input_type=InputType.TYPE_CLASS_TEXT
        )
        layout.addView(edit_text, LinearLayout.LayoutParams(-1, -2))

        builder = AlertDialogBuilder(ctx)
        builder.set_title(T("dlg_code_lang_title"))
        builder.set_view(layout)

        def on_save(bld, w):
            new_val = edit_text.getText().toString().strip()
            self.set_setting("code_lang", new_val, reload_settings=True)
            BulletinHelper.show_success(T("msg_code_lang_saved"))
            bld.dismiss()

        def on_clear(bld, w):
            self.set_setting("code_lang", "", reload_settings=True)
            BulletinHelper.show_success(T("msg_code_lang_saved"))
            bld.dismiss()

        builder.set_positive_button(T("dlg_btn_save"), on_save)
        if current_val:
            builder.set_neutral_button(T("dlg_btn_clear"), on_clear)
        builder.set_negative_button(T("dlg_btn_cancel"), lambda b, w: b.dismiss())
        builder.show()
        return True

    def create_preview_banner(self, ctx) -> View:
        if ctx is None or LinearLayout is None or FrameLayout is None:
            return None

        dark = is_dark_theme()

        wrapper = FrameLayout(ctx)
        wrapper.setPadding(dp(14), dp(6), dp(14), dp(8))
        wrapper.setLayoutParams(FrameLayout.LayoutParams(-1, -2))

        card = LinearLayout(ctx)
        card.setOrientation(LinearLayout.VERTICAL)
        card.setLayoutParams(FrameLayout.LayoutParams(-1, -2))
        card.setPadding(dp(14), dp(12), dp(14), dp(12))

        card_bg = GradientDrawable()
        card_bg.setColor(c(0x201E293B if dark else 0x08000000))
        card_bg.setCornerRadius(float(dp(14)))
        card_bg.setStroke(dp(1), c(0x3538BDF8 if dark else 0x18000000))
        card.setBackground(card_bg)

        top_row = LinearLayout(ctx)
        top_row.setOrientation(LinearLayout.HORIZONTAL)
        top_row.setGravity(Gravity.CENTER_VERTICAL)
        top_row.setLayoutParams(LinearLayout.LayoutParams(-1, -2))

        header_tv = TextView(ctx)
        header_tv.setText(T("preview_header"))
        header_tv.setTextSize(1, 12.0)
        header_tv.setTextColor(c(0xFF38BDF8 if dark else 0xFF0284C7))
        try:
            header_tv.setTypeface(Typeface.DEFAULT_BOLD)
        except Exception:
            pass
        top_row.addView(header_tv, LinearLayout.LayoutParams(-2, -2, 1.0))

        edit_btn = TextView(ctx)
        edit_btn.setText(T("preview_edit_btn"))
        edit_btn.setTextSize(1, 11.5)
        edit_btn.setTextColor(c(0xFF94A3B8 if dark else 0xFF64748B))
        edit_btn_bg = GradientDrawable()
        edit_btn_bg.setColor(c(0x20FFFFFF if dark else 0x10000000))
        edit_btn_bg.setCornerRadius(float(dp(8)))
        edit_btn.setBackground(edit_btn_bg)
        edit_btn.setPadding(dp(8), dp(4), dp(8), dp(4))
        edit_btn.setClickable(True)
        edit_btn.setFocusable(True)
        safe_set_click_listener(edit_btn, lambda: self.show_edit_preview_dialog(ctx))

        top_row.addView(edit_btn, LinearLayout.LayoutParams(-2, -2))
        card.addView(top_row)

        bubble = LinearLayout(ctx)
        bubble.setOrientation(LinearLayout.VERTICAL)
        bubble.setPadding(dp(12), dp(10), dp(12), dp(10))

        is_code = self.get_setting("s_code", False)
        is_mono = self.get_setting("s_mono", False)
        code_lang_str = str(self.get_setting("code_lang", "") or "").strip()

        bubble_bg = GradientDrawable()
        if is_code:
            bubble_bg.setColor(c(0xFF0F172A if dark else 0xFF1E293B))
            bubble_bg.setCornerRadius(float(dp(8)))
            bubble_bg.setStroke(dp(1), c(0x3538BDF8 if dark else 0x2538BDF8))
        else:
            bubble_bg.setColor(c(0x350F172A if dark else 0x15E2E8F0))
            bubble_bg.setCornerRadius(float(dp(10)))
        bubble.setBackground(bubble_bg)

        if is_code:
            code_header = LinearLayout(ctx)
            code_header.setOrientation(LinearLayout.HORIZONTAL)
            code_header.setGravity(Gravity.CENTER_VERTICAL)
            ch_lp = LinearLayout.LayoutParams(-1, -2)
            ch_lp.bottomMargin = dp(6)

            lang_tv = TextView(ctx)
            lang_tv.setText(code_lang_str if code_lang_str else "code")
            lang_tv.setTextSize(1, 12.0)
            lang_tv.setTextColor(c(0xFF38BDF8 if dark else 0xFF7DD3FC))
            try:
                if Typeface is not None:
                    lang_tv.setTypeface(Typeface.MONOSPACE)
            except Exception:
                pass
            code_header.addView(lang_tv, LinearLayout.LayoutParams(-2, -2, 1.0))

            cp_tv = TextView(ctx)
            cp_tv.setText("⎘")
            cp_tv.setTextSize(1, 13.0)
            cp_tv.setTextColor(c(0xFF94A3B8))
            code_header.addView(cp_tv, LinearLayout.LayoutParams(-2, -2))

            bubble.addView(code_header, ch_lp)

            div = View(ctx)
            div.setBackgroundColor(c(0x3038BDF8))
            d_lp = LinearLayout.LayoutParams(-1, dp(1))
            d_lp.bottomMargin = dp(6)
            bubble.addView(div, d_lp)

        bubble_lp = LinearLayout.LayoutParams(-1, -2)
        bubble_lp.topMargin = dp(8)
        bubble_lp.bottomMargin = dp(6)

        sample_text = self.get_setting("preview_sample_text", "")
        if not sample_text:
            sample_text = T("preview_default_text")

        text_to_format = sample_text
        if (is_code or is_mono) and text_to_format:
            text_to_format = self._fix_code_autocap(text_to_format)

        prefix = self.get_setting("prefix_text", "")
        suffix = self.get_setting("suffix_text", "")
        if prefix or suffix:
            text_to_format = f"{prefix}{text_to_format}{suffix}"

        if not (is_code or is_mono) and self.get_setting("m_anti_caps", False):
            threshold = ANTI_CAPS_THRESHOLDS.get(self.get_setting("anti_caps_pct", 2), 70)
            alpha_chars = [ch for ch in text_to_format if ch.isalpha()]
            if alpha_chars and (len([ch for ch in alpha_chars if ch.isupper()]) / len(alpha_chars)) * 100 >= threshold:
                text_to_format = text_to_format.lower()
                if self.get_setting("anti_caps_capitalize", True):
                    for i, char in enumerate(text_to_format):
                        if char.isalpha():
                            text_to_format = text_to_format[:i] + char.upper() + text_to_format[i+1:]
                            break

        if self.get_setting("m_vaporwave", False):
            text_to_format = "".join(chr(ord(ch) + 0xFEE0) if 0x21 <= ord(ch) <= 0x7E else chr(0x3000) if ord(ch) == 0x20 else ch for ch in text_to_format)

        if self.get_setting("m_leet_speak", False):
            mappings = self.get_setting("leet_mappings", DEFAULT_LEET)
            trans_dict = {}
            for k, v in mappings.items():
                if v:
                    trans_dict[ord(k.lower())] = str(v).lower()
                    trans_dict[ord(k.upper())] = str(v).upper()
            text_to_format = text_to_format.translate(trans_dict)

        font_style = int(self.get_setting("m_custom_font", 0))
        if font_style in FONT_MAPS:
            text_to_format = text_to_format.translate(FONT_MAPS[font_style])

        if self.get_setting("m_zalgo", False):
            intensity = ZALGO_INTENSITIES.get(self.get_setting("zalgo_intensity", 1), 4)
            text_to_format = "".join(ch + "".join(random.choices(ZALGO_MARKS, k=random.randint(1, intensity))) if ch.strip() else ch for ch in text_to_format)

        preview_tv = TextView(ctx)
        preview_tv.setTextSize(1, 14.5)
        preview_tv.setTextColor(c(0xFFF1F5F9 if is_code else (0xFFF8FAFC if dark else 0xFF0F172A)))
        preview_tv.setLayoutParams(LinearLayout.LayoutParams(-1, -2))
        if is_mono or is_code:
            try:
                if Typeface is not None:
                    preview_tv.setTypeface(Typeface.MONOSPACE)
            except Exception:
                pass

        # 1. Native SpannableStringBuilder approach (pure formatted spans, zero HTML tag leakage)
        applied = False
        if SpannableStringBuilder is not None and StyleSpan is not None and Typeface is not None:
            try:
                ssb = SpannableStringBuilder(text_to_format)
                t_len = len(text_to_format)

                if is_code:
                    if TypefaceSpan is not None:
                        ssb.setSpan(TypefaceSpan("monospace"), 0, t_len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                elif is_mono:
                    if TypefaceSpan is not None:
                        ssb.setSpan(TypefaceSpan("monospace"), 0, t_len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if self.get_setting("s_strike", False) and StrikethroughSpan is not None:
                        ssb.setSpan(StrikethroughSpan(), 0, t_len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if self.get_setting("s_underline", False) and UnderlineSpan is not None:
                        ssb.setSpan(UnderlineSpan(), 0, t_len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if self.get_setting("s_quote", False) and QuoteSpan is not None:
                        ssb.setSpan(QuoteSpan(c(0xFF38BDF8 if dark else 0xFF0284C7)), 0, t_len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                else:
                    if self.get_setting("s_bold_italic", False):
                        ssb.setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, t_len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    else:
                        if self.get_setting("s_bold", False):
                            ssb.setSpan(StyleSpan(Typeface.BOLD), 0, t_len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        if self.get_setting("s_italic", False):
                            ssb.setSpan(StyleSpan(Typeface.ITALIC), 0, t_len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                    if self.get_setting("s_strike", False) and StrikethroughSpan is not None:
                        ssb.setSpan(StrikethroughSpan(), 0, t_len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if self.get_setting("s_underline", False) and UnderlineSpan is not None:
                        ssb.setSpan(UnderlineSpan(), 0, t_len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if self.get_setting("s_spoiler", False) and BackgroundColorSpan is not None:
                        ssb.setSpan(BackgroundColorSpan(c(0xFF334155 if dark else 0xFFCBD5E1)), 0, t_len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if self.get_setting("s_quote", False) and QuoteSpan is not None:
                        ssb.setSpan(QuoteSpan(c(0xFF38BDF8 if dark else 0xFF0284C7)), 0, t_len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                preview_tv.setText(ssb)
                applied = True
            except Exception:
                applied = False

        if not applied:
            # 2. Direct Html.fromHtml (standard HTML tags, NO AndroidUtilities.replaceTags to prevent raw tag leakage)
            html_content = text_to_format.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            if is_code:
                html_content = f"<tt>{html_content}</tt>"
            elif self.get_setting("s_mono", False):
                html_content = f"<tt>{html_content}</tt>"
                if self.get_setting("s_strike", False): html_content = f"<strike>{html_content}</strike>"
                if self.get_setting("s_underline", False): html_content = f"<u>{html_content}</u>"
                if self.get_setting("s_quote", False): html_content = f"<blockquote>{html_content}</blockquote>"
            else:
                if self.get_setting("s_bold_italic", False):
                    html_content = f"<b><i>{html_content}</i></b>"
                else:
                    if self.get_setting("s_bold", False): html_content = f"<b>{html_content}</b>"
                    if self.get_setting("s_italic", False): html_content = f"<i>{html_content}</i>"
                if self.get_setting("s_strike", False): html_content = f"<strike>{html_content}</strike>"
                if self.get_setting("s_underline", False): html_content = f"<u>{html_content}</u>"
                if self.get_setting("s_quote", False): html_content = f"<blockquote>{html_content}</blockquote>"
                if self.get_setting("s_spoiler", False): html_content = f"<font color='#64748B'>{html_content}</font>"

            spanned = None
            if Html is not None:
                try:
                    spanned = Html.fromHtml(html_content, Html.FROM_HTML_MODE_LEGACY)
                except Exception:
                    try:
                        spanned = Html.fromHtml(html_content)
                    except Exception:
                        pass

            if spanned is not None:
                preview_tv.setText(spanned)
            else:
                preview_tv.setText(text_to_format)

        bubble.addView(preview_tv)
        card.addView(bubble, bubble_lp)

        hint_tv = TextView(ctx)
        hint_tv.setText(T("preview_hint"))
        hint_tv.setTextSize(1, 11.0)
        hint_tv.setTextColor(c(0xFF64748B if dark else 0xFF94A3B8))
        card.addView(hint_tv, LinearLayout.LayoutParams(-1, -2))

        wrapper.addView(card, FrameLayout.LayoutParams(-1, -2))
        return wrapper

    def create_welcome_view(self, ctx, on_continue_click=None) -> View:
        if ctx is None or LinearLayout is None or FrameLayout is None:
            return None

        dark = is_dark_theme()

        root = LinearLayout(ctx)
        root.setOrientation(LinearLayout.VERTICAL)
        root.setGravity(Gravity.CENTER_HORIZONTAL)
        root.setPadding(0, 0, 0, dp(32))

        # 1. Top Web Banner Container (like Omniscient)
        bg_container = FrameLayout(ctx)
        cbg = GradientDrawable()
        cbg.setShape(GradientDrawable.RECTANGLE)
        cbg.setCornerRadius(float(dp(16)))
        cbg.setColor(c(0xFF000000 if dark else 0xFFF1F5F9))
        bg_container.setBackground(cbg)
        try:
            bg_container.setClipToOutline(True)
        except Exception:
            pass

        bg_view = ImageView(ctx)
        try:
            bg_view.setScaleType(ImageView.ScaleType.CENTER_CROP)
        except Exception:
            pass
        prefix = "https://raw.githubusercontent.com/MrNeoner/OmniscientReborn/refs/heads/main/assets/"
        banner_urls = [
            f"{prefix}{'welcome_bgdark.png' if dark else 'welcome_bg.png'}",
            f"{prefix}{'banner_bgdark.png' if dark else 'banner_bg.png'}",
            f"{prefix}welcome_bg.png",
            f"{prefix}banner_bg.png"
        ]
        load_web_banner(bg_view, bg_container, dp, banner_urls)
        bg_container.addView(bg_view, FrameLayout.LayoutParams(-1, -1))

        bg_lp = LinearLayout.LayoutParams(-1, dp(120))
        bg_lp.setMargins(dp(14), dp(10), dp(14), dp(8))
        root.addView(bg_container, bg_lp)

        # 2. Profile Card (Avatar from web, Version Chip, Channel Chip, About button)
        profile_card = LinearLayout(ctx)
        profile_card.setOrientation(LinearLayout.HORIZONTAL)
        profile_card.setGravity(Gravity.CENTER_VERTICAL)
        profile_card.setPadding(dp(14), dp(12), dp(14), dp(12))

        p_bg = GradientDrawable()
        p_bg.setShape(GradientDrawable.RECTANGLE)
        p_bg.setCornerRadius(float(dp(18)))
        p_bg.setColor(c(0x201E293B if dark else 0x08000000))
        p_bg.setStroke(dp(1), c(0x3538BDF8 if dark else 0x18000000))
        profile_card.setBackground(p_bg)

        avatar_holder = FrameLayout(ctx)
        av_cutout = GradientDrawable()
        av_cutout.setShape(GradientDrawable.OVAL)
        av_cutout.setColor(c(0xFF10192A if dark else 0xFFFFFFFF))
        av_stroke_color = 0xFF38BDF8 if dark else 0xFF0284C7
        av_cutout.setStroke(dp(2), c(av_stroke_color))
        avatar_holder.setBackground(av_cutout)
        avatar_holder.setPadding(dp(3), dp(3), dp(3), dp(3))
        try:
            avatar_holder.setClipToOutline(True)
        except Exception:
            pass

        avatar_inner = FrameLayout(ctx)
        av_inner_shape = GradientDrawable()
        av_inner_shape.setShape(GradientDrawable.OVAL)
        av_inner_shape.setColor(c(0xFF10192A if dark else 0xFFFFFFFF))
        avatar_inner.setBackground(av_inner_shape)
        try:
            avatar_inner.setClipToOutline(True)
        except Exception:
            pass

        icon = ImageView(ctx)
        try:
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER)
            icon.setAdjustViewBounds(True)
            icon.setClipToOutline(True)
        except Exception:
            pass

        av_loaded = False
        try:
            if ApplicationLoader is not None and BitmapFactory is not None:
                fdir = str(ApplicationLoader.getFilesDirFixed().getAbsolutePath())
                c_path = os.path.join(fdir, "cache", "textmorph_res", "web_avatar_circle_v4.png")
                if os.path.exists(c_path) and os.path.getsize(c_path) > 100:
                    cached_bmp = BitmapFactory.decodeFile(c_path)
                    if cached_bmp is not None:
                        if avatar_inner and dp:
                            avatar_inner.setPadding(dp(2.5), dp(2.5), dp(2.5), dp(2.5))
                        icon.clearColorFilter()
                        icon.setImageBitmap(cached_bmp)
                        av_loaded = True
        except Exception:
            pass

        if not av_loaded:
            ic_id = get_tg_drawable(ctx, "msg_openprofile", "msg_user")
            if ic_id != 0:
                try:
                    icon.setImageResource(ic_id)
                    icon.setColorFilter(c(av_stroke_color))
                except Exception:
                    pass

        load_web_avatar(icon, avatar_inner, dp, "https://github.com/MrNeoner.png")

        avatar_inner.addView(icon, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        avatar_holder.addView(avatar_inner, FrameLayout.LayoutParams(-1, -1))
        profile_card.addView(avatar_holder, LinearLayout.LayoutParams(dp(54), dp(54)))

        info_col = LinearLayout(ctx)
        info_col.setOrientation(LinearLayout.VERTICAL)
        info_col.setGravity(Gravity.CENTER_VERTICAL)
        info_lp = LinearLayout.LayoutParams(0, -2, 1.0)
        info_lp.leftMargin = dp(12)

        title_row = LinearLayout(ctx)
        title_row.setOrientation(LinearLayout.HORIZONTAL)
        title_row.setGravity(Gravity.CENTER_VERTICAL)

        title = TextView(ctx)
        title.setText("Text Morph")
        title.setTextSize(1, 18.0)
        title.setTextColor(c(0xFFFFFFFF if dark else 0xFF151A21))
        try:
            title.setTypeface(Typeface.DEFAULT_BOLD)
        except Exception:
            pass
        title_row.addView(title, LinearLayout.LayoutParams(-2, -2))

        ver_chip = LinearLayout(ctx)
        ver_chip.setOrientation(LinearLayout.HORIZONTAL)
        ver_chip.setGravity(Gravity.CENTER)
        v_bg = GradientDrawable()
        v_bg.setColor(c(0x2500E5FF if dark else 0x180284C7))
        v_bg.setCornerRadius(float(dp(10)))
        v_bg.setStroke(dp(1), c(0x6000E5FF if dark else 0x400284C7))
        ver_chip.setBackground(v_bg)
        ver_chip.setPadding(dp(7), dp(2), dp(7), dp(2))
        ver_chip.setClickable(True)
        ver_chip.setFocusable(True)
        safe_set_click_listener(ver_chip, lambda: self.show_about_sheet(ctx))

        v_dot = View(ctx)
        v_dot_bg = GradientDrawable()
        v_dot_bg.setShape(GradientDrawable.OVAL)
        v_dot_bg.setColor(c(0xFF00E5FF if dark else 0xFF0284C7))
        v_dot.setBackground(v_dot_bg)
        v_dot_lp = LinearLayout.LayoutParams(dp(6), dp(6))
        v_dot_lp.gravity = Gravity.CENTER_VERTICAL
        v_dot_lp.rightMargin = dp(4)
        ver_chip.addView(v_dot, v_dot_lp)

        v_txt = TextView(ctx)
        v_txt.setText(f"v{__version__}")
        v_txt.setTextSize(1, 10.5)
        v_txt.setTextColor(c(0xFF00E5FF if dark else 0xFF0284C7))
        try:
            v_txt.setTypeface(Typeface.DEFAULT_BOLD)
        except Exception:
            pass
        ver_chip.addView(v_txt)

        v_lp = LinearLayout.LayoutParams(-2, -2)
        v_lp.leftMargin = dp(8)
        title_row.addView(ver_chip, v_lp)
        info_col.addView(title_row, LinearLayout.LayoutParams(-1, -2))

        subtitle = TextView(ctx)
        subtitle.setText(T("welcome_subtitle"))
        subtitle.setTextSize(1, 12.0)
        subtitle.setTextColor(c(0xFF9AA4B2))
        sub_lp = LinearLayout.LayoutParams(-1, -2)
        sub_lp.topMargin = dp(2)
        info_col.addView(subtitle, sub_lp)

        chips_container = LinearLayout(ctx)
        chips_container.setOrientation(LinearLayout.HORIZONTAL)
        chips_container.setGravity(Gravity.CENTER_VERTICAL)
        c_row_lp = LinearLayout.LayoutParams(-1, -2)
        c_row_lp.topMargin = dp(8)

        chip_color = 0x1AFFFFFF if dark else 0x0C000000
        chip_tint = 0xFF9AA4B2 if dark else 0xFF334155

        chan_chip = LinearLayout(ctx)
        chan_chip.setOrientation(LinearLayout.HORIZONTAL)
        chan_chip.setGravity(Gravity.CENTER_VERTICAL)
        ch_bg = GradientDrawable()
        ch_bg.setColor(c(chip_color))
        ch_bg.setCornerRadius(float(dp(12)))
        chan_chip.setBackground(ch_bg)
        chan_chip.setPadding(dp(8), dp(4), dp(8), dp(4))
        chan_chip.setClickable(True)
        chan_chip.setFocusable(True)
        safe_set_click_listener(chan_chip, lambda: self.open_tg_link("neo_plugin"))

        chan_icon = ImageView(ctx)
        chan_id = get_tg_drawable(ctx, "msg_channel", "msg_send")
        if chan_id != 0:
            try:
                chan_icon.setImageResource(chan_id)
                chan_icon.setColorFilter(c(chip_tint))
            except Exception:
                pass
        chan_chip.addView(chan_icon, LinearLayout.LayoutParams(dp(14), dp(14)))

        chan_tv = TextView(ctx)
        chan_tv.setText("@neo_plugin")
        chan_tv.setTextSize(1, 11.5)
        chan_tv.setTextColor(c(chip_tint))
        chan_tv.setPadding(dp(5), 0, 0, 0)
        chan_chip.addView(chan_tv)
        chips_container.addView(chan_chip, LinearLayout.LayoutParams(-2, -2))

        info_btn = LinearLayout(ctx)
        info_btn.setOrientation(LinearLayout.HORIZONTAL)
        info_btn.setGravity(Gravity.CENTER_VERTICAL)
        ib_bg = GradientDrawable()
        ib_bg.setColor(c(chip_color))
        ib_bg.setCornerRadius(float(dp(12)))
        info_btn.setBackground(ib_bg)
        info_btn.setPadding(dp(8), dp(4), dp(8), dp(4))
        info_btn.setClickable(True)
        info_btn.setFocusable(True)
        safe_set_click_listener(info_btn, lambda: self.show_about_sheet(ctx))

        info_ic = ImageView(ctx)
        info_ic_id = get_tg_drawable(ctx, "msg_info", "menu_about")
        if info_ic_id != 0:
            try:
                info_ic.setImageResource(info_ic_id)
                info_ic.setColorFilter(c(chip_tint))
            except Exception:
                pass
        info_btn.addView(info_ic, LinearLayout.LayoutParams(dp(14), dp(14)))

        info_tv = TextView(ctx)
        info_tv.setText(T("about_plugin_title"))
        info_tv.setTextSize(1, 11.5)
        info_tv.setTextColor(c(chip_tint))
        info_tv.setPadding(dp(4), 0, 0, 0)
        info_btn.addView(info_tv)

        ib_lp = LinearLayout.LayoutParams(-2, -2)
        ib_lp.leftMargin = dp(6)
        chips_container.addView(info_btn, ib_lp)

        info_col.addView(chips_container, c_row_lp)
        profile_card.addView(info_col, info_lp)

        p_lp = LinearLayout.LayoutParams(-1, -2)
        p_lp.setMargins(dp(14), dp(4), dp(14), dp(6))
        root.addView(profile_card, p_lp)

        # 3. Outer Feature Card (Title, Body, Pills, Big Button)
        outer = FrameLayout(ctx)
        outer.setPadding(dp(10), dp(10), dp(10), dp(10))

        card = LinearLayout(ctx)
        card.setOrientation(LinearLayout.VERTICAL)
        card.setGravity(Gravity.CENTER_HORIZONTAL)
        card.setPadding(dp(14), dp(24), dp(14), dp(20))

        card_bg = 0xFFF7F8FA if dark else 0xFF111820
        primary_text = 0xFF141B24 if dark else 0xFFFFFFFF
        secondary_text = 0xCC141B24 if dark else 0xCCFFFFFF
        btn_bg_color = 0xFF111820 if dark else 0xFFFFFFFF
        btn_text_color = 0xFFFFFFFF if dark else 0xFF111820

        card_shape = GradientDrawable()
        card_shape.setShape(GradientDrawable.RECTANGLE)
        card_shape.setCornerRadius(float(dp(24)))
        card_shape.setColor(c(card_bg))
        card.setBackground(card_shape)

        card_title = TextView(ctx)
        card_title.setText(T("welcome_card_title"))
        card_title.setTextSize(1, 22.0)
        card_title.setTextColor(c(primary_text))
        card_title.setGravity(Gravity.CENTER)
        try:
            card_title.setTypeface(Typeface.DEFAULT_BOLD)
        except Exception:
            pass
        ct_lp = LinearLayout.LayoutParams(-1, -2)
        ct_lp.setMargins(dp(4), 0, dp(4), 0)
        card.addView(card_title, ct_lp)

        card_body = TextView(ctx)
        card_body.setText(T("welcome_card_body"))
        card_body.setTextSize(1, 13.5)
        card_body.setTextColor(c(secondary_text))
        card_body.setGravity(Gravity.CENTER)
        card_body.setLineSpacing(float(dp(2)), 1.15)
        cb_lp = LinearLayout.LayoutParams(-1, -2)
        cb_lp.setMargins(dp(4), dp(14), dp(4), 0)
        card.addView(card_body, cb_lp)

        pills_row = LinearLayout(ctx)
        pills_row.setOrientation(LinearLayout.HORIZONTAL)
        pills_row.setGravity(Gravity.CENTER)
        pills_lp = LinearLayout.LayoutParams(-1, -2)
        pills_lp.setMargins(0, dp(18), 0, 0)

        features = [
            T("welcome_pill1"),
            T("welcome_pill2"),
            T("welcome_pill3")
        ]

        for label_str in features:
            fpill = LinearLayout(ctx)
            fpill.setOrientation(LinearLayout.HORIZONTAL)
            fpill.setGravity(Gravity.CENTER)
            fp_bg = GradientDrawable()
            fp_bg.setCornerRadius(float(dp(11)))
            fp_bg.setColor(c(0x0C000000 if dark else 0x18FFFFFF))
            fpill.setBackground(fp_bg)
            fpill.setPadding(dp(4), 0, dp(4), 0)

            ft_tv = TextView(ctx)
            ft_tv.setText(label_str)
            ft_tv.setTextSize(1, 10.2)
            ft_tv.setSingleLine(True)
            ft_tv.setGravity(Gravity.CENTER)
            ft_tv.setTextColor(c(primary_text))
            try:
                ft_tv.setTypeface(Typeface.DEFAULT_BOLD)
            except Exception:
                pass
            fpill.addView(ft_tv, LinearLayout.LayoutParams(-1, -2))

            fp_lp = LinearLayout.LayoutParams(0, dp(34), 1.0)
            fp_lp.setMargins(dp(2.5), 0, dp(2.5), 0)
            pills_row.addView(fpill, fp_lp)

        card.addView(pills_row, pills_lp)

        button = LinearLayout(ctx)
        button.setOrientation(LinearLayout.HORIZONTAL)
        button.setGravity(Gravity.CENTER)
        button.setPadding(dp(18), 0, dp(18), 0)
        b_shape = GradientDrawable()
        b_shape.setShape(GradientDrawable.RECTANGLE)
        b_shape.setCornerRadius(float(dp(28)))
        b_shape.setColor(c(btn_bg_color))
        button.setBackground(b_shape)
        button.setClickable(True)
        button.setFocusable(True)
        if on_continue_click:
            safe_set_click_listener(button, on_continue_click)

        button_icon = ImageView(ctx)
        btn_ic_id = get_tg_drawable(ctx, "msg_check", "menu_download_round")
        if btn_ic_id != 0:
            try:
                button_icon.setImageResource(btn_ic_id)
                button_icon.setColorFilter(c(btn_text_color))
            except Exception:
                pass
        button.addView(button_icon, LinearLayout.LayoutParams(dp(22), dp(22)))

        button_label = TextView(ctx)
        button_label.setText(T("welcome_btn_continue"))
        button_label.setTextSize(1, 16.0)
        button_label.setTextColor(c(btn_text_color))
        try:
            button_label.setTypeface(Typeface.DEFAULT_BOLD)
        except Exception:
            pass
        bl_lp = LinearLayout.LayoutParams(-2, -2)
        bl_lp.leftMargin = dp(8)
        button.addView(button_label, bl_lp)

        btn_lp = LinearLayout.LayoutParams(-1, dp(48))
        btn_lp.setMargins(dp(8), dp(20), dp(8), 0)
        card.addView(button, btn_lp)

        outer.addView(card, FrameLayout.LayoutParams(-1, -2))
        root.addView(outer, LinearLayout.LayoutParams(-1, -2))
        return root

    def show_about_sheet(self, ctx):
        if not ctx or LinearLayout is None:
            return

        dark = is_dark_theme()
        sheet = None
        if BottomSheet is not None:
            try:
                sheet = BottomSheet(ctx, True)
            except Exception:
                sheet = None

        sheet_root = LinearLayout(ctx)
        sheet_root.setOrientation(LinearLayout.VERTICAL)
        sheet_root.setPadding(dp(20), dp(16), dp(20), dp(20))
        sheet_shape = GradientDrawable()
        sheet_shape.setColor(c(0xFF141C2B if dark else 0xFFFFFFFF))
        sheet_root.setBackground(sheet_shape)

        grabber = View(ctx)
        g_bg = GradientDrawable()
        g_bg.setColor(c(0x35FFFFFF if dark else 0x20000000))
        g_bg.setCornerRadius(float(dp(3)))
        grabber.setBackground(g_bg)
        g_lp = LinearLayout.LayoutParams(dp(36), dp(4))
        g_lp.gravity = Gravity.CENTER_HORIZONTAL
        g_lp.bottomMargin = dp(14)
        sheet_root.addView(grabber, g_lp)

        head_row = LinearLayout(ctx)
        head_row.setOrientation(LinearLayout.HORIZONTAL)
        head_row.setGravity(Gravity.CENTER_VERTICAL)

        title_tv = TextView(ctx)
        title_tv.setText("Text Morph")
        title_tv.setTextSize(1, 20.0)
        title_tv.setTextColor(c(0xFFFFFFFF if dark else 0xFF0F172A))
        try:
            title_tv.setTypeface(Typeface.DEFAULT_BOLD)
        except Exception:
            pass
        head_row.addView(title_tv)

        v_chip = LinearLayout(ctx)
        v_chip.setOrientation(LinearLayout.HORIZONTAL)
        v_chip.setGravity(Gravity.CENTER_VERTICAL)
        v_bg = GradientDrawable()
        v_bg.setColor(c(0x2500E5FF if dark else 0x180284C7))
        v_bg.setCornerRadius(float(dp(10)))
        v_bg.setStroke(dp(1), c(0x6000E5FF if dark else 0x400284C7))
        v_chip.setBackground(v_bg)
        v_chip.setPadding(dp(7), dp(2), dp(7), dp(2))

        v_dot = View(ctx)
        vd_bg = GradientDrawable()
        vd_bg.setShape(GradientDrawable.OVAL)
        vd_bg.setColor(c(0xFF00E5FF if dark else 0xFF0284C7))
        v_dot.setBackground(vd_bg)
        vd_lp = LinearLayout.LayoutParams(dp(6), dp(6))
        vd_lp.gravity = Gravity.CENTER_VERTICAL
        vd_lp.rightMargin = dp(4)
        v_chip.addView(v_dot, vd_lp)

        v_text = TextView(ctx)
        v_text.setText(f"v{__version__}")
        v_text.setTextSize(1, 10.5)
        v_text.setTextColor(c(0xFF00E5FF if dark else 0xFF0284C7))
        try:
            v_text.setTypeface(Typeface.DEFAULT_BOLD)
        except Exception:
            pass
        v_chip.addView(v_text)

        vc_lp = LinearLayout.LayoutParams(-2, -2)
        vc_lp.leftMargin = dp(10)
        head_row.addView(v_chip, vc_lp)
        sheet_root.addView(head_row, LinearLayout.LayoutParams(-1, -2))

        sub_tv = TextView(ctx)
        sub_tv.setText(T("welcome_subtitle"))
        sub_tv.setTextSize(1, 13.0)
        sub_tv.setTextColor(c(0xFF9AA4B2))
        sub_lp = LinearLayout.LayoutParams(-1, -2)
        sub_lp.topMargin = dp(4)
        sub_lp.bottomMargin = dp(14)
        sheet_root.addView(sub_tv, sub_lp)

        scroll = ScrollView(ctx)
        scroll_content = LinearLayout(ctx)
        scroll_content.setOrientation(LinearLayout.VERTICAL)

        intro_card = LinearLayout(ctx)
        intro_card.setOrientation(LinearLayout.VERTICAL)
        ic_bg = GradientDrawable()
        ic_bg.setColor(c(0x221E293B if dark else 0x08000000))
        ic_bg.setCornerRadius(float(dp(14)))
        ic_bg.setStroke(dp(1), c(0x2538BDF8 if dark else 0x15000000))
        intro_card.setBackground(ic_bg)
        intro_card.setPadding(dp(14), dp(12), dp(14), dp(12))

        intro_txt = TextView(ctx)
        intro_txt.setText(T("welcome_card_body"))
        intro_txt.setTextSize(1, 13.0)
        intro_txt.setTextColor(c(0xFFCBD5E1 if dark else 0xFF334155))
        intro_card.addView(intro_txt)
        scroll_content.addView(intro_card, LinearLayout.LayoutParams(-1, -2))

        links_row = LinearLayout(ctx)
        links_row.setOrientation(LinearLayout.HORIZONTAL)
        links_lp = LinearLayout.LayoutParams(-1, -2)
        links_lp.topMargin = dp(12)
        links_lp.bottomMargin = dp(14)

        ch_btn = TextView(ctx)
        ch_btn.setText(T("plugin_channel"))
        ch_btn.setTextSize(1, 12.0)
        ch_btn.setGravity(Gravity.CENTER)
        ch_btn.setTextColor(c(0xFF38BDF8 if dark else 0xFF0284C7))
        ch_bg = GradientDrawable()
        ch_bg.setColor(c(0x2038BDF8 if dark else 0x120284C7))
        ch_bg.setCornerRadius(float(dp(10)))
        ch_btn.setBackground(ch_bg)
        ch_btn.setPadding(dp(10), dp(8), dp(10), dp(8))
        safe_set_click_listener(ch_btn, lambda: self.open_tg_link("neo_plugin"))

        dev_btn = TextView(ctx)
        dev_btn.setText(T("contact_me"))
        dev_btn.setTextSize(1, 12.0)
        dev_btn.setGravity(Gravity.CENTER)
        dev_btn.setTextColor(c(0xFF94A3B8 if dark else 0xFF64748B))
        dev_bg = GradientDrawable()
        dev_bg.setColor(c(0x20FFFFFF if dark else 0x10000000))
        dev_bg.setCornerRadius(float(dp(10)))
        dev_btn.setBackground(dev_bg)
        dev_btn.setPadding(dp(10), dp(8), dp(10), dp(8))
        safe_set_click_listener(dev_btn, lambda: self.open_tg_link("mrneoner"))

        links_row.addView(ch_btn, LinearLayout.LayoutParams(0, -2, 1.0))
        dev_lp = LinearLayout.LayoutParams(0, -2, 1.0)
        dev_lp.leftMargin = dp(8)
        links_row.addView(dev_btn, dev_lp)
        scroll_content.addView(links_row, links_lp)

        close_btn = TextView(ctx)
        close_btn.setText(T("welcome_btn_continue"))
        close_btn.setTextSize(1, 14.5)
        close_btn.setTextColor(c(0xFFFFFFFF))
        close_btn.setGravity(Gravity.CENTER)
        try:
            close_btn.setTypeface(Typeface.DEFAULT_BOLD)
        except Exception:
            pass
        b_shape = GradientDrawable()
        b_shape.setColor(c(0xFF0284C7 if dark else 0xFF0284C7))
        b_shape.setCornerRadius(float(dp(12)))
        close_btn.setBackground(b_shape)
        close_btn.setPadding(0, dp(11), 0, dp(11))
        if sheet is not None:
            safe_set_click_listener(close_btn, lambda: sheet.dismiss())

        scroll_content.addView(close_btn, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(scroll_content, LinearLayout.LayoutParams(-1, -2))
        sheet_root.addView(scroll, LinearLayout.LayoutParams(-1, -2))

        if sheet is not None:
            sheet.setCustomView(sheet_root)
            sheet.show()
        else:
            bld = AlertDialogBuilder(ctx)
            bld.set_view(sheet_root)
            bld.show()

    def create_settings(self) -> List[Any]:
        fragment = get_last_fragment()
        ctx = fragment.getParentActivity() if fragment else None

        if not self.get_setting("onboarding_completed", False) and ctx:
            welcome_view = self.create_welcome_view(ctx, on_continue_click=self.complete_onboarding)
            if welcome_view is not None:
                try:
                    return [Custom(view=welcome_view)]
                except Exception:
                    try:
                        return [Custom(item=UItem.asCustom(welcome_view))]
                    except Exception:
                        pass

        if not self.get_setting("enabled", True):
            return [
                Header(text=T("main_settings_header")),
                Switch(key="enabled", text=T("main_enable_plugin"), default=True, icon="msg_settings", on_change=lambda c: self.update_setting_value("enabled", c)),
                Custom(item=UItem.asShadow(T("plugin_disabled_shadow")))
            ]

        accs = self.get_setting("allowed_accounts", [])
        ign = self.get_setting("ignored_chats", [])
        is_all = self.get_setting("work_on_all_accounts", True)
        acc_sub = T("acc_sub_all") if is_all else T("main_acc_sub").replace("{val}", str(len(accs)))
        
        settings = [
            Header(text=T("main_settings_header")),
            Switch(key="enabled", text=T("main_enable_plugin"), default=True, icon="msg_settings", on_change=lambda c: self.update_setting_value("enabled", c))
        ]

        if ctx:
            try:
                p_banner = self.create_preview_banner(ctx)
                if p_banner is not None:
                    try:
                        settings.append(Custom(view=p_banner))
                    except Exception:
                        settings.append(Custom(item=UItem.asCustom(p_banner)))
            except Exception:
                pass

        settings.extend([
            Header(text=T("main_acc_header")),
            Text(text=T("main_acc_title"), subtext=acc_sub, icon="msg_contacts", create_sub_fragment=self.create_accounts_page),
            
            Header(text=T("format_modifiers_header"))
        ])

        style_keys = ["s_bold", "s_italic", "s_bold_italic", "s_mono", "s_code", "s_quote", "s_strike", "s_spoiler", "s_underline", "s_random"]
        for k in style_keys:
            settings.append(Switch(key=k, text=T(k), default=False, icon="msg_edit", on_change=lambda c, k_name=k: self.handle_switch(k_name, c)))
            if k == "s_code":
                code_lang_val = str(self.get_setting("code_lang", "") or "").strip()
                code_sub = code_lang_val if code_lang_val else T("code_lang_default")
                settings.append(Text(
                    text=T("code_lang"), 
                    subtext=code_sub, 
                    icon="msg_edit", 
                    on_click=self.show_edit_code_lang_dialog
                ))
                settings.append(Custom(item=UItem.asShadow(T("code_lang_shadow"))))
            
        settings.extend([
            Text(text=T("format_random_pool_btn"), icon="msg_settings", create_sub_fragment=self.create_chaos_pool_page),
            
            Header(text=T("modes_header")),
            Text(text=T("m_leet_speak"), icon="msg_settings", create_sub_fragment=self.create_leet_page),
            Selector(
                key="m_custom_font",
                text=T("m_custom_font"),
                default=0,
                items=[T("font_normal"), T("font_bold"), T("font_gothic"), T("font_cursive"), T("font_double")],
                icon="msg_edit",
                on_change=self.handle_font_change
            ),
            Custom(item=UItem.asShadow(T("font_en_only_hint"))),
            Switch(key="m_zalgo", text=T("m_zalgo"), default=False, icon="msg_edit", on_change=self.handle_zalgo_switch),
            Selector(
                key="zalgo_intensity",
                text=T("zalgo_lvl"),
                default=1,
                items=[T("lvl_light"), T("lvl_mid"), T("lvl_hard"), T("lvl_max")],
                icon="msg_edit",
                on_change=lambda c: self.update_setting_value("zalgo_intensity", int(c))
            ),
            Switch(key="m_vaporwave", text=T("m_vaporwave"), default=False, icon="msg_edit", on_change=self.handle_vaporwave_change),
            Custom(item=UItem.asShadow(T("vaporwave_hint"))),
            
            Header(text=T("anticaps_header")),
            Switch(key="m_anti_caps", text=T("m_anti_caps"), default=False, icon="msg_edit"),
            Selector(
                key="anti_caps_pct",
                text=T("anti_caps_pct"),
                default=2,
                items=["30%", "50%", "70%", "90%", "100%"],
                icon="msg_edit",
                on_change=lambda c: self.update_setting_value("anti_caps_pct", int(c))
            ),
            Switch(key="anti_caps_capitalize", text=T("anti_caps_capitalize"), default=True, icon="msg_edit"),
            
            Header(text=T("affix_header")),
            Text(
                text=T("prefix_text"), 
                subtext=f'"{self.get_setting("prefix_text", "")}"' if self.get_setting("prefix_text", "") else T("not_set"), 
                icon="msg_edit", 
                on_click=self.show_edit_prefix_dialog
            ),
            Text(
                text=T("suffix_text"), 
                subtext=f'"{self.get_setting("suffix_text", "")}"' if self.get_setting("suffix_text", "") else T("not_set"), 
                icon="msg_edit", 
                on_click=self.show_edit_suffix_dialog
            ),
            Custom(item=UItem.asShadow(T("presuf_alert_shadow"))),
            
            Header(text=T("exc_header")),
            Text(text=T("exc_title"), subtext=T("exc_sub").replace("{val}", str(len(ign))), icon="msg_contacts", create_sub_fragment=self.create_ignored_chats_page),
            
            Header(text=T("about_dev")),
            Text(text=T("plugin_channel"), subtext="@neo_plugin", icon="msg_channel", on_click=lambda v: self.open_tg_link("neo_plugin"), on_long_click=lambda v: self.on_link_long_click("neo_plugin")),
            Text(text=T("contact_me"), subtext="@mrneoner", icon="msg_openprofile", on_click=lambda v: self.open_tg_link("mrneoner"), on_long_click=lambda v: self.on_link_long_click("mrneoner")),
            Divider(),
            Text(
                text=f"v{__version__}", 
                icon="msg_info", 
                on_click=self.on_version_click,
                on_long_click=self.on_version_long_click
            )
        ])
        
        return settings

    def _should_lowercase_first_word(self, word: str, rest_of_line: str) -> bool:
        if not word or not word[0].isupper():
            return False
        # Never lowercase all-caps acronyms like URL, JSON, HTTP, API, SQL
        if len(word) >= 2 and word.isupper():
            return False
        # Never lowercase non-ASCII words (e.g. Russian/Cyrillic words like "Привет")
        if not word.isascii():
            return False
        w_lower = word.lower()
        # 1. Standard programming keywords
        if w_lower in CODE_KEYWORDS:
            return True
        # 2. Function calls or method calls: Foo(...) or Foo.bar(...)
        rest_stripped = rest_of_line.lstrip()
        if rest_stripped.startswith("(") or rest_stripped.startswith("."):
            return True
        # 3. Variable assignments: X = ... or My_var = ...
        if rest_stripped.startswith("=") or rest_stripped.startswith("+=") or rest_stripped.startswith("-="):
            return True
        # 4. Single-letter variables in code: e.g. I in range, X, y = ...
        if len(word) == 1 and word.isascii() and (rest_stripped.startswith("in ") or rest_stripped.startswith(",")):
            return True
        return False

    def _fix_code_autocap(self, text: str) -> str:
        if not text:
            return text
        if text.startswith("`") and not text.startswith("```"):
            inner = text[1:]
            end_idx = inner.find("`")
            if end_idx != -1:
                code_part = inner[:end_idx]
                rest = inner[end_idx:]
                for i, ch in enumerate(code_part):
                    if ch.isalpha():
                        word_end = i + 1
                        while word_end < len(code_part) and (code_part[word_end].isalnum() or code_part[word_end] == '_'):
                            word_end += 1
                        word = code_part[i:word_end]
                        if self._should_lowercase_first_word(word, code_part[word_end:]):
                            code_part = code_part[:i] + ch.lower() + code_part[i+1:]
                        break
                return "`" + code_part + rest
            else:
                for i, ch in enumerate(inner):
                    if ch.isalpha():
                        word_end = i + 1
                        while word_end < len(inner) and (inner[word_end].isalnum() or inner[word_end] == '_'):
                            word_end += 1
                        word = inner[i:word_end]
                        if self._should_lowercase_first_word(word, inner[word_end:]):
                            inner = inner[:i] + ch.lower() + inner[i+1:]
                        break
                return "`" + inner

        lines = text.split("\n")
        new_lines = []
        for line in lines:
            trimmed = line.strip()
            if trimmed.startswith("```"):
                new_lines.append(line)
                continue
            fixed_line = line
            for i, char in enumerate(line):
                if char.isalpha():
                    word_end = i + 1
                    while word_end < len(line) and (line[word_end].isalnum() or line[word_end] == '_'):
                        word_end += 1
                    word = line[i:word_end]
                    if self._should_lowercase_first_word(word, line[word_end:]):
                        fixed_line = line[:i] + char.lower() + line[i+1:]
                    break
            new_lines.append(fixed_line)
        return "\n".join(new_lines)

    def _process_text(self, raw_text: str, entities: Any):
        modified = False

        # 1. Detect if message is in code mode
        is_code = self.get_setting("s_code", False) or self.get_setting("s_mono", False)
        raw_stripped = raw_text.strip()
        if raw_stripped.startswith("```") or raw_stripped.startswith("`"):
            is_code = True

        if entities:
            try:
                for ent in entities:
                    ent_name = ent.getClass().getSimpleName() if hasattr(ent, "getClass") else type(ent).__name__
                    if "Pre" in ent_name or "Code" in ent_name:
                        is_code = True
                        break
            except Exception:
                pass

        # Auto-detect if message looks like a code statement even without settings
        if not is_code and raw_stripped:
            first_line = raw_stripped.split("\n")[0].strip()
            fw = ""
            for ch in first_line:
                if ch.isalnum() or ch == '_':
                    fw += ch
                else:
                    break
            if fw.lower() in CODE_KEYWORDS:
                is_code = True
            elif fw:
                rest_after_word = first_line[len(fw):].lstrip()
                if rest_after_word.startswith("(") or rest_after_word.startswith("=") or rest_after_word.startswith("."):
                    is_code = True

        # 2. Fix auto-capitalized first letter in code (default, unconditional)
        if is_code and raw_text:
            fixed_code = self._fix_code_autocap(raw_text)
            if fixed_code != raw_text:
                raw_text = fixed_code
                modified = True

        prefix = self.get_setting("prefix_text", "")
        suffix = self.get_setting("suffix_text", "")

        if prefix or suffix:
            raw_text = f"{prefix}{raw_text}{suffix}"
            modified = True

        if not is_code and self.get_setting("m_anti_caps", False):
            threshold = ANTI_CAPS_THRESHOLDS.get(self.get_setting("anti_caps_pct", 2), 70)
            alpha_chars = [ch for ch in raw_text if ch.isalpha()]
            if alpha_chars and (len([ch for ch in alpha_chars if ch.isupper()]) / len(alpha_chars)) * 100 >= threshold:
                raw_text = raw_text.lower()
                if self.get_setting("anti_caps_capitalize", True):
                    for i, char in enumerate(raw_text):
                        if char.isalpha():
                            raw_text = raw_text[:i] + char.upper() + raw_text[i+1:]
                            break
                modified = True

        if self.get_setting("m_vaporwave", False):
            raw_text = "".join(chr(ord(ch) + 0xFEE0) if 0x21 <= ord(ch) <= 0x7E else chr(0x3000) if ord(ch) == 0x20 else ch for ch in raw_text)
            modified = True

        if self.get_setting("m_leet_speak", False):
            mappings = self.get_setting("leet_mappings", DEFAULT_LEET)
            trans_dict = {}
            for k, v in mappings.items():
                if v:
                    trans_dict[ord(k.lower())] = str(v).lower()
                    trans_dict[ord(k.upper())] = str(v).upper()
            raw_text = raw_text.translate(trans_dict)
            modified = True

        font_style = int(self.get_setting("m_custom_font", 0))
        if font_style in FONT_MAPS:
            raw_text = raw_text.translate(FONT_MAPS[font_style])
            modified = True

        if self.get_setting("m_zalgo", False):
            intensity = ZALGO_INTENSITIES.get(self.get_setting("zalgo_intensity", 1), 4)
            raw_text = "".join(ch + "".join(random.choices(ZALGO_MARKS, k=random.randint(1, intensity))) if ch.strip() else ch for ch in raw_text)
            modified = True

        is_random = self.get_setting("s_random", False)
        active = [k for k in STYLE_MAP if self.get_setting(f"s_{k}", False)]
        has_bold_italic = self.get_setting("s_bold_italic", False)

        if is_random or active or has_bold_italic:
            if entities is None:
                entities = ArrayList() if ArrayList is not None else []

            java_len = len(raw_text.encode("utf-16-le")) // 2

            def add_ents(classes):
                for cls in classes:
                    if not cls: continue
                    ent = cls()
                    ent.offset = 0
                    ent.length = java_len
                    if cls == getattr(TLRPC, "TL_messageEntityPre", None):
                        lang_str = str(self.get_setting("code_lang", "") or "").strip()
                        if lang_str:
                            try:
                                if String is not None:
                                    ent.language = String(lang_str)
                                else:
                                    ent.language = lang_str
                            except Exception:
                                ent.language = lang_str
                        else:
                            try:
                                ent.language = ""
                            except Exception:
                                pass
                    try:
                        entities.add(ent)
                    except AttributeError:
                        try:
                            entities.append(ent)
                        except Exception:
                            pass

            if is_random:
                pool = [[STYLE_MAP[k]] for k in STYLE_MAP if self.get_setting(f"chaos_pool_{k}", True)]
                if self.get_setting("chaos_pool_bold_italic", True): 
                    pool.append([STYLE_MAP["bold"], STYLE_MAP["italic"]])
                add_ents(random.choice(pool) if pool else [STYLE_MAP["bold"]])
            else:
                classes_to_add = []
                if self.get_setting("s_code", False):
                    if STYLE_MAP.get("code"):
                        classes_to_add.append(STYLE_MAP["code"])
                elif self.get_setting("s_mono", False):
                    if STYLE_MAP.get("mono"):
                        classes_to_add.append(STYLE_MAP["mono"])
                    for k in ["quote", "strike", "underline"]:
                        if self.get_setting(f"s_{k}", False) and STYLE_MAP.get(k):
                            classes_to_add.append(STYLE_MAP[k])
                else:
                    if has_bold_italic:
                        if STYLE_MAP.get("bold"): classes_to_add.append(STYLE_MAP["bold"])
                        if STYLE_MAP.get("italic"): classes_to_add.append(STYLE_MAP["italic"])
                    else:
                        if self.get_setting("s_bold", False) and STYLE_MAP.get("bold"):
                            classes_to_add.append(STYLE_MAP["bold"])
                        if self.get_setting("s_italic", False) and STYLE_MAP.get("italic"):
                            classes_to_add.append(STYLE_MAP["italic"])
                    for k in ["quote", "strike", "spoiler", "underline"]:
                        if self.get_setting(f"s_{k}", False) and STYLE_MAP.get(k):
                            classes_to_add.append(STYLE_MAP[k])
                add_ents(classes_to_add)
            modified = True

        return modified, raw_text, entities

    def on_send_message_hook(self, account: int, params: Any) -> HookResult:
        try:
            if not self.get_setting("enabled", True): return HookResult()
            
            if not self.get_setting("work_on_all_accounts", True):
                try:
                    current_uid = int(UserConfig.getInstance(account).getClientUserId()) if UserConfig else 0
                except Exception:
                    current_uid = 0
                allowed_uids = [int(x) for x in self.get_setting("allowed_accounts", []) if str(x).isdigit()]
                if current_uid and current_uid not in allowed_uids:
                    return HookResult()

            peer = getattr(params, "peer", getattr(params, "dialog_id", None))
            if peer is not None:
                try:
                    p_id = int(peer)
                except Exception:
                    p_id = peer
                ign_chats = [int(x) for x in self.get_setting("ignored_chats", []) if str(x).lstrip('-').isdigit()]
                if p_id in ign_chats or peer in self.get_setting("ignored_chats", []):
                    return HookResult()

            raw_text = ""
            prop_name = ""
            
            if getattr(params, "message", None) is not None:
                raw_text = str(params.message)
                prop_name = "message"
            elif getattr(params, "caption", None) is not None:
                raw_text = str(params.caption)
                prop_name = "caption"
                
            if not raw_text:
                return HookResult()

            # Skip bot commands so formatting doesn't break /start, /help, etc.
            if raw_text.startswith("/"):
                return HookResult()

            clean_text = raw_text.translate(str.maketrans('', '', string.whitespace + string.punctuation))
            if clean_text and not any(c.isalnum() for c in clean_text):
                return HookResult()

            modified, new_text, new_ents = self._process_text(raw_text, getattr(params, "entities", None))
            
            if modified:
                java_val = String(new_text) if String is not None else new_text
                if hasattr(params, prop_name):
                    try:
                        setattr(params, prop_name, java_val)
                    except Exception:
                        try:
                            setattr(params, prop_name, new_text)
                        except Exception:
                            pass
                if isinstance(params, dict):
                    params[prop_name] = new_text

                if new_ents is not None:
                    if hasattr(params, "entities"):
                        setattr(params, "entities", new_ents)
                    if isinstance(params, dict):
                        params["entities"] = new_ents
                return HookResult(strategy=HookStrategy.MODIFY, params=params)
                
            return HookResult()
        except Exception:
            return HookResult()
