package com.tsunderegram.messenger.api.dto;

import androidx.annotation.NonNull;

import java.util.Objects;

public final class BadgeDTO {

    public static final long TSUNDERE_DEV_DOC_ID = -7777777L;
    public static final long TSUNDERE_LITTLE_DOC_ID = -7777778L;
    public static final long TSUNDERE_CROWN_DOC_ID = -7777779L;
    public static final long TSUNDERE_STAR_DOC_ID = -7777780L;
    public static final long TSUNDERE_HEART_DOC_ID = -7777781L;
    public static final long TSUNDERE_SHIELD_DOC_ID = -7777782L;
    public static final long TSUNDERE_EXTERA_DOC_ID = -7777783L;
    public static final long TSUNDERE_CUSTOM_DOC_ID = -7777788L;

    private final long documentId;
    private String text;
    private String imageUrl;

    public BadgeDTO(long documentId, String text) {
        this(documentId, text, null);
    }

    public BadgeDTO(long documentId, String text, String imageUrl) {
        this.documentId = documentId;
        this.text = text;
        this.imageUrl = imageUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public boolean hasImageUrl() {
        return imageUrl != null && !imageUrl.isEmpty();
    }

    public boolean isTsundereDev() {
        return documentId == TSUNDERE_DEV_DOC_ID;
    }

    public boolean isTsundereLittle() {
        return documentId == TSUNDERE_LITTLE_DOC_ID;
    }

    public boolean isTsundereCrown() {
        return documentId == TSUNDERE_CROWN_DOC_ID;
    }

    public boolean isTsundereStar() {
        return documentId == TSUNDERE_STAR_DOC_ID;
    }

    public boolean isTsundereHeart() {
        return documentId == TSUNDERE_HEART_DOC_ID;
    }

    public boolean isTsundereShield() {
        return documentId == TSUNDERE_SHIELD_DOC_ID;
    }

    public boolean isTsundereExtera() {
        return documentId == TSUNDERE_EXTERA_DOC_ID;
    }

    public boolean isCustomBadge() {
        return documentId < 0;
    }

    public long getDocumentId() {
        return documentId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long component1() {
        return documentId;
    }

    public String component2() {
        return text;
    }

    public BadgeDTO copy(long documentId, String text) {
        return new BadgeDTO(documentId, text);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BadgeDTO)) {
            return false;
        }
        BadgeDTO badge = (BadgeDTO) other;
        return documentId == badge.documentId && Objects.equals(text, badge.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentId, text);
    }

    @NonNull
    @Override
    public String toString() {
        return "BadgeDTO(documentId=" + documentId + ", text=" + text + ")";
    }
}
