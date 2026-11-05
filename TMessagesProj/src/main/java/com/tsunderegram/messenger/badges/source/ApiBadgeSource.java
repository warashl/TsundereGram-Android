package com.tsunderegram.messenger.badges.source;

import com.tsunderegram.messenger.api.dto.BadgeDTO;
import com.tsunderegram.messenger.api.model.ProfileStatus;

import java.util.concurrent.ConcurrentHashMap;

public final class ApiBadgeSource {

    private final ConcurrentHashMap<Long, BadgeInfo> cache = new ConcurrentHashMap<>();

    public BadgeDTO getBadge(long id, boolean isUser) {
        BadgeInfo info = cache.get(Long.valueOf(id));
        return info == null ? null : info.getBadge();
    }

    public boolean isDeveloper(long id) {
        BadgeInfo info = cache.get(Long.valueOf(id));
        return info != null && info.getStatus() == ProfileStatus.DEVELOPER;
    }

    public boolean isSupporter(long id) {
        BadgeInfo info = cache.get(Long.valueOf(id));
        return info != null && info.getStatus() == ProfileStatus.SUPPORTER;
    }

    public boolean canChangeBadge(long id) {
        BadgeInfo info = cache.get(Long.valueOf(id));
        return (info != null && info.getCanChangeBadge()) || isDeveloper(id);
    }

    public BadgeInfo getInfo(long id) {
        return cache.get(Long.valueOf(id));
    }

    public void put(long id, BadgeInfo info) {
        if (info == null) {
            cache.remove(Long.valueOf(id));
        } else {
            cache.put(Long.valueOf(id), info);
        }
    }

    public void clear() {
        cache.clear();
    }
}
