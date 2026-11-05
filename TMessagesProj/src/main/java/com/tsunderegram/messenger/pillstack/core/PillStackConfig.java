package com.tsunderegram.messenger.pillstack.core;

import java.util.ArrayList;

public final class PillStackConfig {
    private PillStackConfig() {
    }

    public static ArrayList<Integer> getActivePills() {
        app.tsunderegram.pillstack.PillStackConfig.loadConfig(false);
        return app.tsunderegram.pillstack.PillStackConfig.getActivePills();
    }

    public static ArrayList<Integer> getHiddenPills() {
        app.tsunderegram.pillstack.PillStackConfig.loadConfig(false);
        return app.tsunderegram.pillstack.PillStackConfig.getHiddenPills();
    }

    public static int getLastActivePillId() {
        app.tsunderegram.pillstack.PillStackConfig.loadConfig(false);
        return app.tsunderegram.pillstack.PillStackConfig.lastActivePillId.Int();
    }
}
