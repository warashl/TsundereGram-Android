package com.tsunderegram.privacy.database.entities;

import androidx.room.Entity;

@Entity(primaryKeys = {"userId"})
public class LastSeenEntity {
    public long userId;
    public int lastSeen;
}
