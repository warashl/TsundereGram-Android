package org.telegram.messenger;

import android.text.TextUtils;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Уведомления о реакциях на свои сообщения без серверного push.
 *
 * Сервер присылает REACT_* / CHAT_REACT_* только через push (FCM или UnifiedPush). В режиме
 * In-App, а также когда push-токен клиента не принимается сервером, уведомлений о реакциях
 * не было совсем: в updateMessageReactions приходят только новые реакции. Собираем из них
 * такое же уведомление, какое построил бы PushListenerController.
 *
 * Если push всё-таки работает, дубля не будет: NotificationsController склеивает уведомления
 * с одинаковым id сообщения в одном диалоге.
 */
public final class InAppReactionNotifier {

    /** Старые непрочитанные реакции (например, после долгого офлайна) не показываем. */
    private static final int MAX_AGE_SECONDS = 2 * 60 * 60;

    private static final Map<String, Integer> lastNotified = new LinkedHashMap<String, Integer>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > 512;
        }
    };

    private InAppReactionNotifier() {
    }

    public static void onReactionsUpdate(int account, long dialogId, int msgId, int topMsgId, TLRPC.Peer peer, TLRPC.MessageReactions reactions) {
        if (reactions == null || reactions.recent_reactions == null || reactions.recent_reactions.isEmpty() || peer == null || msgId <= 0) {
            return;
        }
        if (DialogObject.isEncryptedDialog(dialogId)) {
            return;
        }
        final long selfId = UserConfig.getInstance(account).getClientUserId();
        TLRPC.MessagePeerReaction best = null;
        for (int i = 0; i < reactions.recent_reactions.size(); i++) {
            final TLRPC.MessagePeerReaction r = reactions.recent_reactions.get(i);
            // unread сервер ставит только реакциям на НАШИ сообщения, которые мы ещё не видели
            if (r == null || !r.unread || r.peer_id == null || r.reaction == null) {
                continue;
            }
            final long peerId = MessageObject.getPeerId(r.peer_id);
            if (peerId == selfId || tw.nekomimi.nekogram.filters.ReactionFilter.isBlockedPeer(account, dialogId, peerId)) {
                continue;
            }
            if (best == null || r.date > best.date) {
                best = r;
            }
        }
        if (best == null) {
            return;
        }
        final int now = ConnectionsManager.getInstance(account).getCurrentTime();
        if (best.date != 0 && now - best.date > MAX_AGE_SECONDS) {
            return;
        }
        // без даты считаем реакцию одной и той же — иначе каждое обновление счётчиков давало бы новое уведомление
        final int stamp = best.date != 0 ? best.date : 1;
        final String key = account + "_" + dialogId + "_" + msgId;
        synchronized (lastNotified) {
            final Integer last = lastNotified.get(key);
            if (last != null && last >= stamp) {
                return;
            }
            lastNotified.put(key, stamp);
        }
        final TLRPC.MessagePeerReaction reaction = best;
        final int date = best.date != 0 ? best.date : now;
        // getMessage() ждёт очередь базы — вызываем не из неё
        Utilities.globalQueue.postRunnable(() -> {
            TLRPC.Message original = null;
            try {
                original = MessagesStorage.getInstance(account).getMessage(dialogId, msgId);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            if (original != null && !original.out) {
                return;
            }
            final TLRPC.Message originalFinal = original;
            AndroidUtilities.runOnUIThread(() -> post(account, dialogId, msgId, topMsgId, peer, reaction, originalFinal, date));
        });
    }

    private static void post(int account, long dialogId, int msgId, int topMsgId, TLRPC.Peer peer, TLRPC.MessagePeerReaction reaction, TLRPC.Message original, int date) {
        try {
            final MessagesController controller = MessagesController.getInstance(account);
            final long reactorId = MessageObject.getPeerId(reaction.peer_id);
            String reactorName = null;
            if (reactorId > 0) {
                final TLRPC.User user = controller.getUser(reactorId);
                if (user != null) {
                    reactorName = UserObject.getUserName(user);
                }
            } else {
                final TLRPC.Chat chat = controller.getChat(-reactorId);
                if (chat != null) {
                    reactorName = chat.title;
                }
            }
            if (TextUtils.isEmpty(reactorName)) {
                reactorName = LocaleController.getString(R.string.NotificationHiddenName);
            }

            final boolean isChat = DialogObject.isChatDialog(dialogId);
            final TLRPC.Chat chat = isChat ? controller.getChat(-dialogId) : null;
            if (chat != null && ChatObject.isChannel(chat) && !chat.megagroup) {
                return; // в каналах реакции анонимны, сервер и сам о них не уведомляет
            }
            final String chatTitle = chat != null ? chat.title : "";
            final boolean supergroup = chat != null && ChatObject.isChannel(chat);

            final String emoji = reactionToString(account, reaction.reaction);
            final String[] kind = describe(original);
            final String locKey = (isChat ? "CHAT_REACT_" : "REACT_") + kind[0];
            final Object[] args = isChat
                    ? new Object[]{reactorName, chatTitle, emoji, kind[1]}
                    : new Object[]{reactorName, emoji, kind[1]};
            String text = null;
            try {
                text = PushListenerController.getReactedText(locKey, args);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            if (TextUtils.isEmpty(text)) {
                text = isChat
                        ? LocaleController.formatString(R.string.PushChatReactNotext, reactorName, chatTitle, emoji)
                        : LocaleController.formatString(R.string.PushReactNoText, reactorName, emoji);
            }

            final TLRPC.TL_message owner = new TLRPC.TL_message();
            owner.id = msgId;
            owner.message = text;
            owner.date = date;
            owner.dialog_id = dialogId;
            owner.peer_id = peer;
            owner.flags |= 256;
            owner.from_id = reaction.peer_id;
            if (supergroup) {
                owner.flags |= 0x80000000;
            }
            final String name = isChat ? chatTitle : reactorName;
            final String userName = isChat ? reactorName : null;
            final MessageObject messageObject = new MessageObject(account, owner, text, name, userName, false, false, supergroup, false);
            if (topMsgId != 0) {
                messageObject.messageOwner.reply_to = new TLRPC.TL_messageReplyHeader();
                messageObject.messageOwner.reply_to.forum_topic = true;
                messageObject.messageOwner.reply_to.reply_to_top_id = topMsgId;
            }
            messageObject.isReactionPush = true;
            final ArrayList<MessageObject> list = new ArrayList<>(1);
            list.add(messageObject);
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("InAppReactionNotifier: " + locKey + " dialog=" + dialogId + " mid=" + msgId);
            }
            NotificationsController.getInstance(account).processNewMessages(list, true, true, null);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static String reactionToString(int account, TLRPC.Reaction reaction) {
        if (reaction instanceof TLRPC.TL_reactionEmoji) {
            final String emoticon = ((TLRPC.TL_reactionEmoji) reaction).emoticon;
            if (!TextUtils.isEmpty(emoticon)) {
                return emoticon;
            }
        } else if (reaction instanceof TLRPC.TL_reactionCustomEmoji) {
            try {
                final TLRPC.Document document = AnimatedEmojiDrawable.findDocument(account, ((TLRPC.TL_reactionCustomEmoji) reaction).document_id);
                final String emoticon = MessageObject.findAnimatedEmojiEmoticon(document, null);
                if (!TextUtils.isEmpty(emoticon)) {
                    return emoticon;
                }
            } catch (Throwable ignore) {
            }
        } else if (reaction instanceof TLRPC.TL_reactionPaid) {
            return "\u2B50";
        }
        return "\u2764\uFE0F";
    }

    /** {суффикс loc_key, дополнительный аргумент} — как у серверных REACT_*. */
    private static String[] describe(TLRPC.Message message) {
        if (message == null) {
            return new String[]{"NOTEXT", ""};
        }
        final TLRPC.MessageMedia media = MessageObject.getMedia(message);
        if (media instanceof TLRPC.TL_messageMediaPhoto) {
            return new String[]{"PHOTO", ""};
        }
        if (MessageObject.isRoundVideoMessage(message)) {
            return new String[]{"ROUND", ""};
        }
        if (MessageObject.isVoiceMessage(message)) {
            return new String[]{"AUDIO", ""};
        }
        if (MessageObject.isStickerMessage(message) || MessageObject.isAnimatedStickerMessage(message)) {
            String alt = "";
            final TLRPC.Document document = media != null ? media.document : null;
            if (document != null) {
                for (int i = 0; i < document.attributes.size(); i++) {
                    final TLRPC.DocumentAttribute attribute = document.attributes.get(i);
                    if (attribute instanceof TLRPC.TL_documentAttributeSticker && attribute.alt != null) {
                        alt = attribute.alt;
                        break;
                    }
                }
            }
            return new String[]{"STICKER", alt};
        }
        if (MessageObject.isGifMessage(message)) {
            return new String[]{"GIF", ""};
        }
        if (MessageObject.isVideoMessage(message)) {
            return new String[]{"VIDEO", ""};
        }
        if (media instanceof TLRPC.TL_messageMediaGeoLive) {
            return new String[]{"GEOLIVE", ""};
        }
        if (media instanceof TLRPC.TL_messageMediaGeo || media instanceof TLRPC.TL_messageMediaVenue) {
            return new String[]{"GEO", ""};
        }
        if (media instanceof TLRPC.TL_messageMediaContact) {
            final TLRPC.TL_messageMediaContact contact = (TLRPC.TL_messageMediaContact) media;
            return new String[]{"CONTACT", ContactsController.formatName(contact.first_name, contact.last_name)};
        }
        if (media instanceof TLRPC.TL_messageMediaPoll) {
            final TLRPC.Poll poll = ((TLRPC.TL_messageMediaPoll) media).poll;
            final String question = poll != null && poll.question != null && poll.question.text != null ? poll.question.text : "";
            return new String[]{poll != null && poll.quiz ? "QUIZ" : "POLL", question};
        }
        if (media instanceof TLRPC.TL_messageMediaDocument) {
            return new String[]{"DOC", ""};
        }
        if (!TextUtils.isEmpty(message.message)) {
            String text = message.message.replace('\n', ' ').trim();
            if (text.length() > 60) {
                text = text.substring(0, 60).trim() + "\u2026";
            }
            return new String[]{"TEXT", text};
        }
        return new String[]{"NOTEXT", ""};
    }
}
