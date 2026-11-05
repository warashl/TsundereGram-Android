package com.tsunderegram.messenger.pillstack.ui.pills;

import android.content.Context;
import org.telegram.ui.ActionBar.Theme;

public abstract class BasePill extends app.tsunderegram.pillstack.pills.BasePill {
    public BasePill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
    }
}
