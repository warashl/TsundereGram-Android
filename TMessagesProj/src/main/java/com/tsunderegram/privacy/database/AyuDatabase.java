/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package com.tsunderegram.privacy.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.tsunderegram.privacy.database.dao.DeletedMessageDao;
import com.tsunderegram.privacy.database.dao.EditedMessageDao;
import com.tsunderegram.privacy.database.dao.LastSeenDao;
import com.tsunderegram.privacy.database.dao.SpyDao;
import com.tsunderegram.privacy.database.dao.DeletedDialogDao;
import com.tsunderegram.privacy.database.entities.DeletedMessage;
import com.tsunderegram.privacy.database.entities.DeletedMessageReaction;
import com.tsunderegram.privacy.database.entities.EditedMessage;
import com.tsunderegram.privacy.database.entities.LastSeenEntity;
import com.tsunderegram.privacy.database.entities.SpyMessageRead;
import com.tsunderegram.privacy.database.entities.SpyMessageContentsRead;
import com.tsunderegram.privacy.database.entities.DeletedDialog;

@Database(entities = {
        EditedMessage.class,
        DeletedMessage.class,
        DeletedMessageReaction.class,
        LastSeenEntity.class,
        SpyMessageRead.class,
        SpyMessageContentsRead.class,
        DeletedDialog.class
}, version = AyuDatabase.VERSION)
public abstract class AyuDatabase extends RoomDatabase {
    public static final int MIN_SUPPORTED_VERSION = 21;
    public static final int VERSION = 27;

    public abstract EditedMessageDao editedMessageDao();

    public abstract DeletedMessageDao deletedMessageDao();

    public abstract LastSeenDao lastSeenDao();

    public abstract SpyDao spyDao();

    public abstract DeletedDialogDao deletedDialogDao();
}
