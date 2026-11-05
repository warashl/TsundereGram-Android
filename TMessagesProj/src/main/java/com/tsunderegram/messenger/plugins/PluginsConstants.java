package com.tsunderegram.messenger.plugins;

/**
 * Шим строковых констант TsundereGram. Значения — те же, что в
 * {@code com.tsunderegram.messenger.plugins.PluginsConstants} 12.9.0, а не наши
 * внутренние: плагин сравнивает их с тем, что приходит из SDK.
 */
public final class PluginsConstants {

    public static final String PYTHON = app.tsunderegram.plugins.PluginsConstants.PYTHON;
    public static final String SEND_MESSAGE_HOOK = app.tsunderegram.plugins.PluginsConstants.SEND_MESSAGE_HOOK;
    public static final String STRATEGY = app.tsunderegram.plugins.PluginsConstants.STRATEGY;
    public static final String PARAMS = app.tsunderegram.plugins.PluginsConstants.PARAMS;
    public static final String UPDATE = app.tsunderegram.plugins.PluginsConstants.UPDATE;
    public static final String UPDATES = app.tsunderegram.plugins.PluginsConstants.UPDATES;
    public static final String REQUEST = app.tsunderegram.plugins.PluginsConstants.REQUEST;
    public static final String RESPONSE = app.tsunderegram.plugins.PluginsConstants.RESPONSE;
    public static final String ERROR = app.tsunderegram.plugins.PluginsConstants.ERROR;
    public static final String PLUGINS = app.tsunderegram.plugins.PluginsConstants.PLUGINS;
    public static final String PLUGINS_EXT = app.tsunderegram.plugins.PluginsConstants.PLUGINS_EXT;
    public static final String PLUGINS_SDK = app.tsunderegram.plugins.PluginsConstants.PLUGINS_SDK;
    public static final String CREATE_SETTINGS = app.tsunderegram.plugins.PluginsConstants.CREATE_SETTINGS;
    public static final String APP_START = app.tsunderegram.plugins.PluginsConstants.APP_START;
    public static final String APP_STOP = app.tsunderegram.plugins.PluginsConstants.APP_STOP;
    public static final String APP_PAUSE = app.tsunderegram.plugins.PluginsConstants.APP_PAUSE;
    public static final String APP_RESUME = app.tsunderegram.plugins.PluginsConstants.APP_RESUME;
    public static final String ON_APP_EVENT = app.tsunderegram.plugins.PluginsConstants.ON_APP_EVENT;
    public static final String ON_PLUGIN_LOAD = app.tsunderegram.plugins.PluginsConstants.ON_PLUGIN_LOAD;
    public static final String ON_PLUGIN_UNLOAD = app.tsunderegram.plugins.PluginsConstants.ON_PLUGIN_UNLOAD;

    private PluginsConstants() {
    }

    public static final class Settings {
        public static final String TYPE = app.tsunderegram.plugins.PluginsConstants.Settings.TYPE;
        public static final String KEY = app.tsunderegram.plugins.PluginsConstants.Settings.KEY;
        public static final String TEXT = app.tsunderegram.plugins.PluginsConstants.Settings.TEXT;
        public static final String SUBTEXT = app.tsunderegram.plugins.PluginsConstants.Settings.SUBTEXT;
        public static final String ICON = app.tsunderegram.plugins.PluginsConstants.Settings.ICON;
        public static final String ACCENT = app.tsunderegram.plugins.PluginsConstants.Settings.ACCENT;
        public static final String RED = app.tsunderegram.plugins.PluginsConstants.Settings.RED;
        public static final String ON_CLICK = app.tsunderegram.plugins.PluginsConstants.Settings.ON_CLICK;
        public static final String DEFAULT = app.tsunderegram.plugins.PluginsConstants.Settings.DEFAULT;
        public static final String ITEMS = app.tsunderegram.plugins.PluginsConstants.Settings.ITEMS;
        public static final String HINT = app.tsunderegram.plugins.PluginsConstants.Settings.HINT;
        public static final String MULTILINE = app.tsunderegram.plugins.PluginsConstants.Settings.MULTILINE;
        public static final String MAX_LENGTH = app.tsunderegram.plugins.PluginsConstants.Settings.MAX_LENGTH;
        public static final String MASK = app.tsunderegram.plugins.PluginsConstants.Settings.MASK;
        public static final String ON_CHANGE = app.tsunderegram.plugins.PluginsConstants.Settings.ON_CHANGE;
        public static final String TYPE_SWITCH = app.tsunderegram.plugins.PluginsConstants.Settings.TYPE_SWITCH;
        public static final String TYPE_INPUT = app.tsunderegram.plugins.PluginsConstants.Settings.TYPE_INPUT;
        public static final String TYPE_SELECTOR = app.tsunderegram.plugins.PluginsConstants.Settings.TYPE_SELECTOR;
        public static final String TYPE_HEADER = app.tsunderegram.plugins.PluginsConstants.Settings.TYPE_HEADER;
        public static final String TYPE_DIVIDER = app.tsunderegram.plugins.PluginsConstants.Settings.TYPE_DIVIDER;
        public static final String TYPE_TEXT = app.tsunderegram.plugins.PluginsConstants.Settings.TYPE_TEXT;
        public static final String TYPE_EDIT_TEXT = app.tsunderegram.plugins.PluginsConstants.Settings.TYPE_EDIT_TEXT;
        public static final String TYPE_CUSTOM = app.tsunderegram.plugins.PluginsConstants.Settings.TYPE_CUSTOM;
        public static final String VIEW = app.tsunderegram.plugins.PluginsConstants.Settings.VIEW;
        public static final String ITEM = app.tsunderegram.plugins.PluginsConstants.Settings.ITEM;
        public static final String FACTORY = app.tsunderegram.plugins.PluginsConstants.Settings.FACTORY;
        public static final String FACTORY_ARGS = app.tsunderegram.plugins.PluginsConstants.Settings.FACTORY_ARGS;
        public static final String CREATE_SUB_FRAGMENT = app.tsunderegram.plugins.PluginsConstants.Settings.CREATE_SUB_FRAGMENT;
        public static final String ON_LONG_CLICK = app.tsunderegram.plugins.PluginsConstants.Settings.ON_LONG_CLICK;
        public static final String LINK_ALIAS = app.tsunderegram.plugins.PluginsConstants.Settings.LINK_ALIAS;

        private Settings() {
        }
    }

    public static final class Strategy {
        public static final String MODIFY = app.tsunderegram.plugins.PluginsConstants.Strategy.MODIFY;
        public static final String CANCEL = app.tsunderegram.plugins.PluginsConstants.Strategy.CANCEL;
        public static final String DEFAULT = app.tsunderegram.plugins.PluginsConstants.Strategy.DEFAULT;
        public static final String MODIFY_FINAL = app.tsunderegram.plugins.PluginsConstants.Strategy.MODIFY_FINAL;

        private Strategy() {
        }
    }

    public static final class Xposed {
        public static final String REPLACE_HOOKED_METHOD = app.tsunderegram.plugins.PluginsConstants.Xposed.REPLACE_HOOKED_METHOD;
        public static final String BEFORE_HOOKED_METHOD = app.tsunderegram.plugins.PluginsConstants.Xposed.BEFORE_HOOKED_METHOD;
        public static final String AFTER_HOOKED_METHOD = app.tsunderegram.plugins.PluginsConstants.Xposed.AFTER_HOOKED_METHOD;
        public static final String HOOK_FILTERS = app.tsunderegram.plugins.PluginsConstants.Xposed.HOOK_FILTERS;

        private Xposed() {
        }
    }

    public static final class DevServer {
        public static final String MODULE = app.tsunderegram.plugins.PluginsConstants.DevServer.MODULE;
        public static final String CLASS = app.tsunderegram.plugins.PluginsConstants.DevServer.CLASS;
        public static final String START_SERVER = app.tsunderegram.plugins.PluginsConstants.DevServer.START_SERVER;
        public static final String STOP_SERVER = app.tsunderegram.plugins.PluginsConstants.DevServer.STOP_SERVER;

        private DevServer() {
        }
    }

    public static final class MenuItemTypes {
        public static final String MESSAGE_CONTEXT_MENU = app.tsunderegram.plugins.PluginsConstants.MenuItemTypes.MESSAGE_CONTEXT_MENU;
        public static final String DRAWER_MENU = app.tsunderegram.plugins.PluginsConstants.MenuItemTypes.DRAWER_MENU;
        public static final String MAIN_MENU = app.tsunderegram.plugins.PluginsConstants.MenuItemTypes.MAIN_MENU;
        public static final String CHAT_ACTION_MENU = app.tsunderegram.plugins.PluginsConstants.MenuItemTypes.CHAT_ACTION_MENU;
        public static final String PROFILE_ACTION_MENU = app.tsunderegram.plugins.PluginsConstants.MenuItemTypes.PROFILE_ACTION_MENU;

        private MenuItemTypes() {
        }
    }

    public static final class MenuItemProperties {
        public static final String MENU_TYPE = app.tsunderegram.plugins.PluginsConstants.MenuItemProperties.MENU_TYPE;
        public static final String ITEM_ID = app.tsunderegram.plugins.PluginsConstants.MenuItemProperties.ITEM_ID;
        public static final String TEXT = app.tsunderegram.plugins.PluginsConstants.MenuItemProperties.TEXT;
        public static final String SUBTEXT = app.tsunderegram.plugins.PluginsConstants.MenuItemProperties.SUBTEXT;
        public static final String ICON = app.tsunderegram.plugins.PluginsConstants.MenuItemProperties.ICON;
        public static final String ON_CLICK = app.tsunderegram.plugins.PluginsConstants.MenuItemProperties.ON_CLICK;
        public static final String CONDITION = app.tsunderegram.plugins.PluginsConstants.MenuItemProperties.CONDITION;
        public static final String PRIORITY = app.tsunderegram.plugins.PluginsConstants.MenuItemProperties.PRIORITY;

        private MenuItemProperties() {
        }
    }

    public static final class HookFilterTypes {
        public static final String RESULT_IS_NULL = app.tsunderegram.plugins.PluginsConstants.HookFilterTypes.RESULT_IS_NULL;
        public static final String RESULT_IS_TRUE = app.tsunderegram.plugins.PluginsConstants.HookFilterTypes.RESULT_IS_TRUE;
        public static final String RESULT_IS_FALSE = app.tsunderegram.plugins.PluginsConstants.HookFilterTypes.RESULT_IS_FALSE;
        public static final String RESULT_NOT_NULL = app.tsunderegram.plugins.PluginsConstants.HookFilterTypes.RESULT_NOT_NULL;
        public static final String RESULT_IS_INSTANCE_OF = app.tsunderegram.plugins.PluginsConstants.HookFilterTypes.RESULT_IS_INSTANCE_OF;
        public static final String RESULT_EQUAL = app.tsunderegram.plugins.PluginsConstants.HookFilterTypes.RESULT_EQUAL;
        public static final String RESULT_NOT_EQUAL = app.tsunderegram.plugins.PluginsConstants.HookFilterTypes.RESULT_NOT_EQUAL;
        public static final String ARGUMENT_IS_NULL = app.tsunderegram.plugins.PluginsConstants.HookFilterTypes.ARGUMENT_IS_NULL;
        public static final String ARGUMENT_IS_TRUE = app.tsunderegram.plugins.PluginsConstants.HookFilterTypes.ARGUMENT_IS_TRUE;
        public static final String ARGUMENT_IS_FALSE = app.tsunderegram.plugins.PluginsConstants.HookFilterTypes.ARGUMENT_IS_FALSE;
        public static final String ARGUMENT_NOT_NULL = app.tsunderegram.plugins.PluginsConstants.HookFilterTypes.ARGUMENT_NOT_NULL;
        public static final String ARGUMENT_IS_INSTANCE_OF = app.tsunderegram.plugins.PluginsConstants.HookFilterTypes.ARGUMENT_IS_INSTANCE_OF;
        public static final String ARGUMENT_EQUAL = app.tsunderegram.plugins.PluginsConstants.HookFilterTypes.ARGUMENT_EQUAL;
        public static final String ARGUMENT_NOT_EQUAL = app.tsunderegram.plugins.PluginsConstants.HookFilterTypes.ARGUMENT_NOT_EQUAL;
        public static final String CONDITION = app.tsunderegram.plugins.PluginsConstants.HookFilterTypes.CONDITION;
        public static final String OR = app.tsunderegram.plugins.PluginsConstants.HookFilterTypes.OR;

        private HookFilterTypes() {
        }
    }
}
