package tw.nekomimi.nekogram.ui.icons;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import androidx.annotation.Nullable;

import app.exteraless.icons.IconPackManager;

import xyz.nextalone.nagram.NaConfig;

@SuppressLint("UseCompatLoadingForDrawables")
public class IconsResources extends Resources {

    public static final int ICON_REPLACE_SOLAR = 1;
    public static volatile IconsResources instance;
    private final LruCache<Long, Drawable.ConstantState> constantStates = new LruCache<>(500);

    public IconsResources(Resources resources) {
        super(resources.getAssets(), resources.getDisplayMetrics(), resources.getConfiguration());
        instance = this;
    }

    public void clearCaches() {
        constantStates.evictAll();
    }

    public static void clearAllCaches() {
        if (instance != null) {
            instance.clearCaches();
        }
    }

    @Override
    public Drawable getDrawable(int id) throws NotFoundException {
        return getDrawableForDensity(id, 0, null);
    }

    @Override
    public Drawable getDrawable(int id, @Nullable Theme theme) throws NotFoundException {
        return getDrawableForDensity(id, 0, theme);
    }

    @Nullable
    @Override
    public Drawable getDrawableForDensity(int id, int density, @Nullable Theme theme) {
        // openExtera: сначала спрашиваем установленные паки иконок
        Drawable fromPack = IconPackManager.getInstance().getDrawable(this, id, density, theme);
        if (fromPack != null) {
            return fromPack;
        }
        return getOriginalDrawableForDensity(id, density, theme);
    }

    @Nullable
    @Override
    public Drawable getDrawableForDensity(int id, int density) throws NotFoundException {
        return getDrawableForDensity(id, density, null);
    }

    @Nullable
    public Drawable getOriginalDrawableForDensity(int id, int density, @Nullable Theme theme) {
        return getOriginalDrawableForDensity(id, density, theme, true);
    }

    /**
     * openExtera: оригинальный drawable.
     * Если applyConversion = false, иконка уже была сопоставлена базовым набором, повторная конвертация не нужна.
     */
    @Nullable
    public Drawable getOriginalDrawableForDensity(int id, int density, @Nullable Theme theme, boolean applyConversion) {
        int converted = applyConversion ? getConversion(id) : id;
        long key = ((long) converted << 32) | (density & 0xffffffffL);
        Drawable.ConstantState state = constantStates.get(key);
        if (state != null) {
            return state.newDrawable(this).mutate();
        }
        Drawable drawable = super.getDrawableForDensity(converted, density, theme);
        Drawable.ConstantState loaded = drawable != null ? drawable.getConstantState() : null;
        if (loaded == null || loaded.canApplyTheme()) {
            return drawable;
        }
        constantStates.put(key, loaded);
        return loaded.newDrawable(this).mutate();
    }

    private int getConversion(int icon) {
        return getConversion(icon, -1);
    }

    private int getConversion(int icon, int forcedIconsType) {
        int consideredIconsType = forcedIconsType == -1 ? NaConfig.INSTANCE.getIconReplacements().Int() : forcedIconsType;

        if (consideredIconsType == app.exteraless.icons.BaseIconPacks.BASE_SOLAR || consideredIconsType == app.exteraless.icons.BaseIconPacks.BASE_REMIX) {
            int mapped = app.exteraless.icons.BaseIconPacks.getIcon(icon);
            if (mapped != icon && mapped != 0) {
                return mapped;
            }
            if (consideredIconsType == ICON_REPLACE_SOLAR) {
                return SolarIcons.Companion.getConversion(icon);
            }
        }

        return icon;
    }
}
