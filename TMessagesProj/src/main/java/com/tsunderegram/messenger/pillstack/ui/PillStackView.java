package com.tsunderegram.messenger.pillstack.ui;

import android.content.Context;
import com.tsunderegram.messenger.pillstack.ui.pills.BasePill;

public class PillStackView extends app.tsunderegram.pillstack.PillStackView {
    public PillStackView(Context context) {
        super(context);
    }

    public void addPill(BasePill pill) {
        super.addPill(pill);
    }
}
