/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipDescription;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v13.view.inputmethod.EditorInfoCompat;
import android.support.v13.view.inputmethod.InputConnectionCompat;
import android.support.v13.view.inputmethod.InputContentInfoCompat;
import android.support.v4.os.BuildCompat;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.query.DraftQuery;
import org.telegram.messenger.query.MessagesQuery;
import org.telegram.messenger.query.StickersQuery;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.GroupStickersActivity;
import org.telegram.ui.StickersActivity;

import java.util.ArrayList;
import java.util.Locale;

public class ChatActivityEnterView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate, SizeNotifierFrameLayout.SizeNotifierFrameLayoutDelegate, StickersAlert.StickersAlertDelegate {

    public interface ChatActivityEnterViewDelegate {
        void onMessageSend(CharSequence message);
        void needSendTyping();
        void onTextChanged(CharSequence text, boolean bigChange);
        void onAttachButtonHidden();
        void onAttachButtonShow();
        void onWindowSizeChanged(int size);
        void onStickersTab(boolean opened);
        void onMessageEditEnd(boolean loading);
        void didPressedAttachButton();
    }


    private EditTextCaption messageEditText;
    private ImageView sendButton;
    private ImageView cancelBotButton;
    private ImageView emojiButton;
    private ImageView expandStickersButton;
    private EmojiView emojiView;


    private SizeNotifierFrameLayout sizeNotifierLayout;
    private LinearLayout attachLayout;
    private ImageView attachButton;
    private ImageView botButton;
    private LinearLayout textFieldContainer;
    private FrameLayout sendButtonContainer;
    private FrameLayout doneButtonContainer;
    private ImageView doneButtonImage;
    private AnimatorSet doneButtonAnimation;
    private ContextProgressView doneButtonProgress;
    private View topView;
    private PopupWindow botKeyboardPopup;
    private BotKeyboardView botKeyboardView;
    private ImageView notifyButton;

    private CloseProgressDrawable2 progressDrawable;
    private Paint dotPaint;
    //private Drawable playDrawable;
   // private Drawable pauseDrawable;

    private MessageObject editingMessageObject;
    private int editingMessageReqId;
    private boolean editingCaption;

    private TLRPC.ChatFull info;

    private boolean hasRecordVideo;

    private int currentPopupContentType = -1;

    private boolean silent;
    private boolean canWriteToChannel;

    private boolean isPaused = true;
    private boolean showKeyboardOnResume;

    private MessageObject botButtonsMessageObject;
    private TLRPC.TL_replyKeyboardMarkup botReplyMarkup;
    private int botCount;
    private boolean hasBotCommands;

    private PowerManager.WakeLock wakeLock;
    private AnimatorSet runningAnimation;
    private AnimatorSet runningAnimation2;
    private AnimatorSet runningAnimationAudio;
    private int runningAnimationType;
    private int recordInterfaceState;

    private int keyboardHeight;
    private int keyboardHeightLand;
    private boolean keyboardVisible;
    private int emojiPadding;
    private boolean sendByEnter;
    private long lastTypingTimeSend;
    private String lastTimeString;
    private long lastTypingSendTime;
    private float startedDraggingX = -1;
    private float distCanMove = AndroidUtilities.dp(80);
    private boolean recordingAudioVideo;
    private boolean forceShowSendButton;
    private boolean allowStickers;
    private boolean allowGifs;

    private int lastSizeChangeValue1;
    private boolean lastSizeChangeValue2;

    private Activity parentActivity;
    private ChatActivity parentFragment;
    private long dialog_id;
    private boolean ignoreTextChange;
    private int innerTextChange;
    private MessageObject replyingMessageObject;
    private MessageObject botMessageObject;
    private TLRPC.WebPage messageWebPage;
    private boolean messageWebPageSearch = true;
    private ChatActivityEnterViewDelegate delegate;

    private TLRPC.TL_document audioToSend;
    private String audioToSendPath;
    private MessageObject audioToSendMessageObject;
    private VideoEditedInfo videoToSendMessageObject;

    private boolean topViewShowed;
    private boolean needShowTopView;
    private boolean allowShowTopView;
    private AnimatorSet currentTopViewAnimation;

    private MessageObject pendingMessageObject;
    private TLRPC.KeyboardButton pendingLocationButton;

    private boolean waitingForKeyboardOpen;
    private Runnable openKeyboardRunnable = new Runnable() {
        @Override
        public void run() {
            if (messageEditText != null && waitingForKeyboardOpen && !keyboardVisible && !AndroidUtilities.usingHardwareInput && !AndroidUtilities.isInMultiwindow) {
                messageEditText.requestFocus();
                AndroidUtilities.showKeyboard(messageEditText);
                AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
                AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
            }
        }
    };
    private Runnable updateExpandabilityRunnable = new Runnable() {
        private int lastKnownPage = -1;

        @Override
        public void run() {
            if (emojiView != null) {
                int curPage = emojiView.getCurrentPage();
                if (curPage != lastKnownPage) {
                    lastKnownPage = curPage;
                    boolean prevOpen = stickersTabOpen;
                    stickersTabOpen = curPage == 1 || curPage == 2;
                    if (prevOpen != stickersTabOpen) {
                        checkSendButton(true);
                    }
                    if (!stickersTabOpen && stickersExpanded) {
                        setStickersExpanded(false, true);
                    }
                }
            }
        }
    };

    private Property<View, Integer> roundedTranslationYProperty = new Property<View, Integer>(Integer.class, "translationY") {
        @Override
        public Integer get(View object) {
            return Math.round(object.getTranslationY());
        }

        @Override
        public void set(View object, Integer value) {
            object.setTranslationY(value);
        }
    };

    private Paint redDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean stickersTabOpen;
    private boolean gifsTabOpen;
    private boolean stickersExpanded;
    private Animator stickersExpansionAnim;
    private float stickersExpansionProgress;
    private int stickersExpandedHeight;
    private boolean stickersDragging;
    private AnimatedArrowDrawable stickersArrow;

    private boolean recordAudioVideoRunnableStarted;
    private boolean calledRecordRunnable;



    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint paintRecord = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Drawable sendDrawable;

    private RectF rect = new RectF();

    public ChatActivityEnterView(Activity context, SizeNotifierFrameLayout parent, ChatActivity fragment, final boolean isChat) {
        super(context);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(Theme.getColor(Theme.key_chat_emojiPanelNewTrending));
        setFocusable(true);
        setFocusableInTouchMode(true);
        setWillNotDraw(false);

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.featuredStickersDidLoaded);
        parentActivity = context;
        parentFragment = fragment;
        sizeNotifierLayout = parent;
        sizeNotifierLayout.setDelegate(this);
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        sendByEnter = preferences.getBoolean("send_by_enter", false);

        textFieldContainer = new LinearLayout(context);
        textFieldContainer.setOrientation(LinearLayout.HORIZONTAL);

        addView(textFieldContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 2, 0, 0));

        FrameLayout frameLayout = new FrameLayout(context);
        textFieldContainer.addView(frameLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        emojiButton = new ImageView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (attachLayout != null && (emojiView == null || emojiView.getVisibility() != VISIBLE) && !StickersQuery.getUnreadStickerSets().isEmpty() && dotPaint != null) {
                    int x = canvas.getWidth() / 2 + AndroidUtilities.dp(4 + 5);
                    int y = canvas.getHeight() / 2 - AndroidUtilities.dp(13 - 5);
                    canvas.drawCircle(x, y, AndroidUtilities.dp(5), dotPaint);
                }
            }
        };
        emojiButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        emojiButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        emojiButton.setPadding(0, AndroidUtilities.dp(1), 0, 0);
//        if (Build.VERSION.SDK_INT >= 21) {
//            emojiButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
//        }
        setEmojiButtonImage();
        frameLayout.addView(emojiButton, LayoutHelper.createFrame(48, 48, Gravity.BOTTOM | Gravity.LEFT, 3, 0, 0, 0));
        emojiButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isPopupShowing() || currentPopupContentType != 0) {
                    showPopup(1, 0);
                    emojiView.onOpen(messageEditText.length() > 0 && !messageEditText.getText().toString().startsWith("@gif"));
                } else {
                    openKeyboardInternal();
                    removeGifFromInputField();
                }
            }
        });

        messageEditText = new EditTextCaption(context) {
            @Override
            public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
                final InputConnection ic = super.onCreateInputConnection(editorInfo);
                EditorInfoCompat.setContentMimeTypes(editorInfo, new String[]{"image/gif", "image/*", "image/jpg", "image/png"});

                final InputConnectionCompat.OnCommitContentListener callback = new InputConnectionCompat.OnCommitContentListener() {
                    @Override
                    public boolean onCommitContent(InputContentInfoCompat inputContentInfo, int flags, Bundle opts) {
                        if (BuildCompat.isAtLeastNMR1() && (flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                            try {
                                inputContentInfo.requestPermission();
                            } catch (Exception e) {
                                return false;
                            }
                        }
                        ClipDescription description = inputContentInfo.getDescription();
                        if (description.hasMimeType("image/gif")) {
                            SendMessagesHelper.prepareSendingDocument(null, null, inputContentInfo.getContentUri(), "image/gif", dialog_id, replyingMessageObject, inputContentInfo);
                        } else {
                            SendMessagesHelper.prepareSendingPhoto(null, inputContentInfo.getContentUri(), dialog_id, replyingMessageObject, null, null, inputContentInfo, 0);
                        }
                        if (delegate != null) {
                            delegate.onMessageSend(null);
                        }
                        return true;
                    }
                };
                return InputConnectionCompat.createWrapper(ic, editorInfo, callback);
            }

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (isPopupShowing() && event.getAction() == MotionEvent.ACTION_DOWN) {
                    showPopup(AndroidUtilities.usingHardwareInput ? 0 : 2, 0);
                    openKeyboardInternal();
                }
                try {
                    return super.onTouchEvent(event);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                return false;
            }
        };
        updateFieldHint();
        messageEditText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        messageEditText.setInputType(messageEditText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
        messageEditText.setSingleLine(false);
        messageEditText.setMaxLines(4);
        messageEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        messageEditText.setGravity(Gravity.BOTTOM);
        messageEditText.setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(12));
        messageEditText.setBackgroundDrawable(null);
        messageEditText.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText));
        messageEditText.setHintColor(Theme.getColor(Theme.key_chat_messagePanelHint));
        messageEditText.setHintTextColor(Theme.getColor(Theme.key_chat_messagePanelHint));
        frameLayout.addView(messageEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 52, 0, isChat ? 50 : 2, 0));
        messageEditText.setOnKeyListener(new OnKeyListener() {
            boolean ctrlPressed = false;
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (i == KeyEvent.KEYCODE_BACK && !keyboardVisible && isPopupShowing()) {
                    if (keyEvent.getAction() == 1) {
                        if (currentPopupContentType == 1 && botButtonsMessageObject != null) {
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                            preferences.edit().putInt("hidekeyboard_" + dialog_id, botButtonsMessageObject.getId()).commit();
                        }
                        showPopup(0, 0);
                        removeGifFromInputField();
                    }
                    return true;
                } else if (i == KeyEvent.KEYCODE_ENTER && (ctrlPressed || sendByEnter) && keyEvent.getAction() == KeyEvent.ACTION_DOWN && editingMessageObject == null) {
                    sendMessage();
                    return true;
                } else if (i == KeyEvent.KEYCODE_CTRL_LEFT || i == KeyEvent.KEYCODE_CTRL_RIGHT) {
                    ctrlPressed = keyEvent.getAction() == KeyEvent.ACTION_DOWN;
                    return true;
                }
                return false;
            }
        });
        messageEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            boolean ctrlPressed = false;

            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i == EditorInfo.IME_ACTION_SEND) {
                    sendMessage();
                    return true;
                } else if (keyEvent != null && i == EditorInfo.IME_NULL) {
                    if ((ctrlPressed || sendByEnter) && keyEvent.getAction() == KeyEvent.ACTION_DOWN && editingMessageObject == null) {
                        sendMessage();
                        return true;
                    } else if (i == KeyEvent.KEYCODE_CTRL_LEFT || i == KeyEvent.KEYCODE_CTRL_RIGHT) {
                        ctrlPressed = keyEvent.getAction() == KeyEvent.ACTION_DOWN;
                        return true;
                    }
                }
                return false;
            }
        });
        messageEditText.addTextChangedListener(new TextWatcher() {
            boolean processChange = false;

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                if (innerTextChange == 1) {
                    return;
                }
                checkSendButton(false);
                CharSequence message = AndroidUtilities.getTrimmedString(charSequence.toString());
                if (delegate != null) {
                    if (!ignoreTextChange) {
                        if (count > 2 || charSequence == null || charSequence.length() == 0) {
                            messageWebPageSearch = true;
                        }
                        delegate.onTextChanged(charSequence, before > count + 1 || (count - before) > 2);
                    }
                }
                if (innerTextChange != 2 && before != count && (count - before) > 1) {
                    processChange = true;
                }
                if (editingMessageObject == null && !canWriteToChannel && message.length() != 0 && lastTypingTimeSend < System.currentTimeMillis() - 5000 && !ignoreTextChange) {
                    int currentTime = ConnectionsManager.getInstance().getCurrentTime();
                    TLRPC.User currentUser = null;
                    if ((int) dialog_id > 0) {
                        currentUser = MessagesController.getInstance().getUser((int) dialog_id);
                    }
                    if (currentUser != null && (currentUser.id == UserConfig.getClientUserId() || currentUser.status != null && currentUser.status.expires < currentTime && !MessagesController.getInstance().onlinePrivacy.containsKey(currentUser.id))) {
                        return;
                    }
                    lastTypingTimeSend = System.currentTimeMillis();
                    if (delegate != null) {
                        delegate.needSendTyping();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (innerTextChange != 0) {
                    return;
                }
                if (sendByEnter && editable.length() > 0 && editable.charAt(editable.length() - 1) == '\n' && editingMessageObject == null) {
                    sendMessage();
                }
                if (processChange) {
                    ImageSpan[] spans = editable.getSpans(0, editable.length(), ImageSpan.class);
                    for (int i = 0; i < spans.length; i++) {
                        editable.removeSpan(spans[i]);
                    }
                    Emoji.replaceEmoji(editable, messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
                    processChange = false;
                }
            }
        });

        if (isChat) {
            attachLayout = new LinearLayout(context);
            attachLayout.setOrientation(LinearLayout.HORIZONTAL);
            attachLayout.setEnabled(false);
            attachLayout.setPivotX(AndroidUtilities.dp(48));
            frameLayout.addView(attachLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.BOTTOM | Gravity.RIGHT));

            botButton = new ImageView(context);
            botButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            botButton.setImageResource(R.drawable.bot_keyboard2);
            botButton.setScaleType(ImageView.ScaleType.CENTER);
            botButton.setVisibility(GONE);
//            if (Build.VERSION.SDK_INT >= 21) {
//                botButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
//            }
            attachLayout.addView(botButton, LayoutHelper.createLinear(48, 48));
            botButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (botReplyMarkup != null) {
                        if (!isPopupShowing() || currentPopupContentType != 1) {
                            showPopup(1, 1);
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                            preferences.edit().remove("hidekeyboard_" + dialog_id).commit();
                        } else {
                            if (currentPopupContentType == 1 && botButtonsMessageObject != null) {
                                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                preferences.edit().putInt("hidekeyboard_" + dialog_id, botButtonsMessageObject.getId()).commit();
                            }
                            openKeyboardInternal();
                        }
                    } else if (hasBotCommands) {
                        setFieldText("/");
                        messageEditText.requestFocus();
                        openKeyboard();
                    }
                }
            });

            notifyButton = new ImageView(context);
            notifyButton.setImageResource(silent ? R.drawable.notify_members_off : R.drawable.notify_members_on);
            notifyButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            notifyButton.setScaleType(ImageView.ScaleType.CENTER);
            notifyButton.setVisibility(canWriteToChannel ? VISIBLE : GONE);
//            if (Build.VERSION.SDK_INT >= 21) {
//                notifyButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
//            }
            attachLayout.addView(notifyButton, LayoutHelper.createLinear(48, 48));
            notifyButton.setOnClickListener(new OnClickListener() {

                private Toast visibleToast;

                @Override
                public void onClick(View v) {
                    silent = !silent;
                    notifyButton.setImageResource(silent ? R.drawable.notify_members_off : R.drawable.notify_members_on);
                    ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).edit().putBoolean("silent_" + dialog_id, silent).commit();
                    NotificationsController.updateServerNotificationsSettings(dialog_id);
                    try {
                        if (visibleToast != null) {
                            visibleToast.cancel();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (silent) {
                        visibleToast = Toast.makeText(parentActivity, LocaleController.getString("ChannelNotifyMembersInfoOff", R.string.ChannelNotifyMembersInfoOff), Toast.LENGTH_SHORT);
                    } else {
                        visibleToast = Toast.makeText(parentActivity, LocaleController.getString("ChannelNotifyMembersInfoOn", R.string.ChannelNotifyMembersInfoOn), Toast.LENGTH_SHORT);
                    }
                    visibleToast.show();
                    updateFieldHint();
                }
            });

            attachButton = new ImageView(context);
            attachButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            attachButton.setImageResource(R.drawable.ic_ab_attach);
            attachButton.setScaleType(ImageView.ScaleType.CENTER);
//            if (Build.VERSION.SDK_INT >= 21) {
//                attachButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
//            }
            attachLayout.addView(attachButton, LayoutHelper.createLinear(48, 48));
            attachButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    delegate.didPressedAttachButton();
                }
            });
        }

        sendButtonContainer = new FrameLayout(context);
        textFieldContainer.addView(sendButtonContainer, LayoutHelper.createLinear(48, 48, Gravity.BOTTOM));

        cancelBotButton = new ImageView(context);
        cancelBotButton.setVisibility(GONE);
        cancelBotButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        cancelBotButton.setImageDrawable(progressDrawable = new CloseProgressDrawable2());
        progressDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelCancelInlineBot), PorterDuff.Mode.MULTIPLY));
        cancelBotButton.setSoundEffectsEnabled(false);
        cancelBotButton.setScaleX(0.1f);
        cancelBotButton.setScaleY(0.1f);
        cancelBotButton.setAlpha(0.0f);
        sendButtonContainer.addView(cancelBotButton, LayoutHelper.createFrame(48, 48));
        cancelBotButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                String text = messageEditText.getText().toString();
                int idx = text.indexOf(' ');
                if (idx == -1 || idx == text.length() - 1) {
                    setFieldText("");
                } else {
                    setFieldText(text.substring(0, idx + 1));
                }
            }
        });

        sendButton = new ImageView(context);
        sendButton.setVisibility(GONE);
        sendButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sendButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelSend), PorterDuff.Mode.MULTIPLY));
        sendButton.setImageResource(R.drawable.ic_send);
        sendButton.setSoundEffectsEnabled(false);
        sendButton.setScaleX(0.1f);
        sendButton.setScaleY(0.1f);
        sendButton.setAlpha(0.0f);
        sendButtonContainer.addView(sendButton, LayoutHelper.createFrame(48, 48));
        sendButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });

        expandStickersButton = new ImageView(context);
        expandStickersButton.setScaleType(ImageView.ScaleType.CENTER);
        expandStickersButton.setImageDrawable(stickersArrow = new AnimatedArrowDrawable(Theme.getColor(Theme.key_chat_messagePanelIcons)));
        expandStickersButton.setVisibility(GONE);
        expandStickersButton.setScaleX(0.1f);
        expandStickersButton.setScaleY(0.1f);
        expandStickersButton.setAlpha(0.0f);
        sendButtonContainer.addView(expandStickersButton, LayoutHelper.createFrame(48, 48));
        expandStickersButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (expandStickersButton.getVisibility() != VISIBLE || expandStickersButton.getAlpha() != 1.0f) {
                    return;
                }
                if (!stickersDragging) {
                    setStickersExpanded(!stickersExpanded, true);
                }
            }
        });


        doneButtonContainer = new FrameLayout(context);
        doneButtonContainer.setVisibility(GONE);
        textFieldContainer.addView(doneButtonContainer, LayoutHelper.createLinear(48, 48, Gravity.BOTTOM));
//        if (Build.VERSION.SDK_INT >= 21) {
//            doneButtonContainer.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
//        }
        doneButtonContainer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                doneEditingMessage();
            }
        });

        doneButtonImage = new ImageView(context);
        doneButtonImage.setScaleType(ImageView.ScaleType.CENTER);
        doneButtonImage.setImageResource(R.drawable.edit_done);
        doneButtonImage.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_editDoneIcon), PorterDuff.Mode.MULTIPLY));
        doneButtonContainer.addView(doneButtonImage, LayoutHelper.createFrame(48, 48));

        doneButtonProgress = new ContextProgressView(context, 0);
        doneButtonProgress.setVisibility(View.INVISIBLE);
        doneButtonContainer.addView(doneButtonProgress, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("emoji", Context.MODE_PRIVATE);
        keyboardHeight = sharedPreferences.getInt("kbd_height", AndroidUtilities.dp(200));
        keyboardHeightLand = sharedPreferences.getInt("kbd_height_land3", AndroidUtilities.dp(200));

        //setRecordVideoButtonVisible(false, false);
        checkSendButton(false);
        updateFieldRight(1);
        checkChannelRights();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == topView) {
            canvas.save();
            canvas.clipRect(0, 0, getMeasuredWidth(), child.getLayoutParams().height + AndroidUtilities.dp(2));
        }
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (child == topView) {
            canvas.restore();
        }
        return result;
    }
    public void showEdit(boolean s){

        //if(s) textFieldContainer.addView(doneButtonContainer, LayoutHelper.createLinear(48, 48, Gravity.BOTTOM));
        //else   textFieldContainer.removeView(doneButtonContainer);
    }
    @Override
    protected void onDraw(Canvas canvas) {
        int top = topView != null && topView.getVisibility() == VISIBLE ? (int) topView.getTranslationY() : 0;
        int bottom = top + Theme.chat_composeShadowDrawable.getIntrinsicHeight();
        Theme.chat_composeShadowDrawable.setBounds(0, top, getMeasuredWidth(), bottom);
        Theme.chat_composeShadowDrawable.draw(canvas);
        canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public boolean isSendButtonVisible() {
        return sendButton.getVisibility() == VISIBLE;
    }




    public void showContextProgress(boolean show) {
        if (progressDrawable == null) {
            return;
        }
        if (show) {
            progressDrawable.startAnimation();
        } else {
            progressDrawable.stopAnimation();
        }
    }

    public void setCaption(String caption) {
        if (messageEditText != null) {
            messageEditText.setCaption(caption);
            checkSendButton(true);
        }
    }

    public void addTopView(View view, int height) {
        if (view == null) {
            return;
        }
        topView = view;
        topView.setVisibility(GONE);
        topView.setTranslationY(height);
        addView(topView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, height, Gravity.TOP | Gravity.LEFT, 0, 2, 0, 0));
        needShowTopView = false;
    }

    public void setForceShowSendButton(boolean value, boolean animated) {
        forceShowSendButton = value;
        checkSendButton(animated);
    }

    public void setAllowStickersAndGifs(boolean value, boolean value2) {
        if ((allowStickers != value || allowGifs != value2) && emojiView != null) {
            if (emojiView.getVisibility() == VISIBLE) {
                hidePopup(false);
            }
            sizeNotifierLayout.removeView(emojiView);
            emojiView = null;
        }
        allowStickers = value;
        allowGifs = value2;
        setEmojiButtonImage();
    }

    public void addEmojiToRecent(String code) {
        createEmojiView();
        emojiView.addEmojiToRecent(code);
    }

    public void setOpenGifsTabFirst() {
        createEmojiView();
        StickersQuery.loadRecents(StickersQuery.TYPE_IMAGE, true, true, false);
        emojiView.switchToGifRecent();
    }

    public void showTopView(boolean animated, final boolean openKeyboard) {
        if (topView == null || topViewShowed || getVisibility() != VISIBLE) {
            return;
        }
        needShowTopView = true;
        topViewShowed = true;
        if (allowShowTopView) {
            topView.setVisibility(VISIBLE);
            if (currentTopViewAnimation != null) {
                currentTopViewAnimation.cancel();
                currentTopViewAnimation = null;
            }
            resizeForTopView(true);
            if (animated) {
                if (keyboardVisible || isPopupShowing()) {
                    currentTopViewAnimation = new AnimatorSet();
                    currentTopViewAnimation.playTogether(ObjectAnimator.ofFloat(topView, "translationY", 0));
                    currentTopViewAnimation.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (currentTopViewAnimation != null && currentTopViewAnimation.equals(animation)) {
                                if ((!forceShowSendButton || openKeyboard)) {
                                    openKeyboard();
                                }
                                currentTopViewAnimation = null;
                            }
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            if (currentTopViewAnimation != null && currentTopViewAnimation.equals(animation)) {
                                currentTopViewAnimation = null;
                            }
                        }
                    });
                    currentTopViewAnimation.setDuration(200);
                    currentTopViewAnimation.start();
                } else {
                    topView.setTranslationY(0);
                    if ( (!forceShowSendButton || openKeyboard)) {
                        openKeyboard();
                    }
                }
            } else {
                topView.setTranslationY(0);
                if ( (!forceShowSendButton || openKeyboard)) {
                    openKeyboard();
                }
            }
        }
    }

    public void onEditTimeExpired() {
        doneButtonContainer.setVisibility(View.GONE);
    }

    public void showEditDoneProgress(final boolean show, boolean animated) {
        if (doneButtonAnimation != null) {
            doneButtonAnimation.cancel();
        }
        if (!animated) {
            if (show) {
                doneButtonImage.setScaleX(0.1f);
                doneButtonImage.setScaleY(0.1f);
                doneButtonImage.setAlpha(0.0f);
                doneButtonProgress.setScaleX(1.0f);
                doneButtonProgress.setScaleY(1.0f);
                doneButtonProgress.setAlpha(1.0f);
                doneButtonImage.setVisibility(View.INVISIBLE);
                doneButtonProgress.setVisibility(View.VISIBLE);
                doneButtonContainer.setEnabled(false);
            } else {
                doneButtonProgress.setScaleX(0.1f);
                doneButtonProgress.setScaleY(0.1f);
                doneButtonProgress.setAlpha(0.0f);
                doneButtonImage.setScaleX(1.0f);
                doneButtonImage.setScaleY(1.0f);
                doneButtonImage.setAlpha(1.0f);
                doneButtonImage.setVisibility(View.VISIBLE);
                doneButtonProgress.setVisibility(View.INVISIBLE);
                doneButtonContainer.setEnabled(true);
            }
        } else {
            doneButtonAnimation = new AnimatorSet();
            if (show) {
                doneButtonProgress.setVisibility(View.VISIBLE);
                doneButtonContainer.setEnabled(false);
                doneButtonAnimation.playTogether(
                        ObjectAnimator.ofFloat(doneButtonImage, "scaleX", 0.1f),
                        ObjectAnimator.ofFloat(doneButtonImage, "scaleY", 0.1f),
                        ObjectAnimator.ofFloat(doneButtonImage, "alpha", 0.0f),
                        ObjectAnimator.ofFloat(doneButtonProgress, "scaleX", 1.0f),
                        ObjectAnimator.ofFloat(doneButtonProgress, "scaleY", 1.0f),
                        ObjectAnimator.ofFloat(doneButtonProgress, "alpha", 1.0f));
            } else {
                doneButtonImage.setVisibility(View.VISIBLE);
                doneButtonContainer.setEnabled(true);
                doneButtonAnimation.playTogether(
                        ObjectAnimator.ofFloat(doneButtonProgress, "scaleX", 0.1f),
                        ObjectAnimator.ofFloat(doneButtonProgress, "scaleY", 0.1f),
                        ObjectAnimator.ofFloat(doneButtonProgress, "alpha", 0.0f),
                        ObjectAnimator.ofFloat(doneButtonImage, "scaleX", 1.0f),
                        ObjectAnimator.ofFloat(doneButtonImage, "scaleY", 1.0f),
                        ObjectAnimator.ofFloat(doneButtonImage, "alpha", 1.0f));

            }
            doneButtonAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (doneButtonAnimation != null && doneButtonAnimation.equals(animation)) {
                        if (!show) {
                            doneButtonProgress.setVisibility(View.INVISIBLE);
                        } else {
                            doneButtonImage.setVisibility(View.INVISIBLE);
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (doneButtonAnimation != null && doneButtonAnimation.equals(animation)) {
                        doneButtonAnimation = null;
                    }
                }
            });
            doneButtonAnimation.setDuration(150);
            doneButtonAnimation.start();
        }
    }

    public void hideTopView(final boolean animated) {
        if (topView == null || !topViewShowed) {
            return;
        }

        topViewShowed = false;
        needShowTopView = false;
        if (allowShowTopView) {
            if (currentTopViewAnimation != null) {
                currentTopViewAnimation.cancel();
                currentTopViewAnimation = null;
            }
            if (animated) {
                currentTopViewAnimation = new AnimatorSet();
                currentTopViewAnimation.playTogether(ObjectAnimator.ofFloat(topView, "translationY", topView.getLayoutParams().height));
                currentTopViewAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (currentTopViewAnimation != null && currentTopViewAnimation.equals(animation)) {
                            topView.setVisibility(GONE);
                            resizeForTopView(false);
                            currentTopViewAnimation = null;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (currentTopViewAnimation != null && currentTopViewAnimation.equals(animation)) {
                            currentTopViewAnimation = null;
                        }
                    }
                });
                currentTopViewAnimation.setDuration(200);
                currentTopViewAnimation.start();
            } else {
                topView.setVisibility(GONE);
                resizeForTopView(false);
                topView.setTranslationY(topView.getLayoutParams().height);
            }
        }
    }

    public boolean isTopViewVisible() {
        return topView != null && topView.getVisibility() == VISIBLE;
    }

    private void onWindowSizeChanged() {
        int size = sizeNotifierLayout.getHeight();
        if (!keyboardVisible) {
            size -= emojiPadding;
        }
        if (delegate != null) {
            delegate.onWindowSizeChanged(size);
        }
        if (topView != null) {
            if (size < AndroidUtilities.dp(72) + ActionBar.getCurrentActionBarHeight()) {
                if (allowShowTopView) {
                    allowShowTopView = false;
                    if (needShowTopView) {
                        topView.setVisibility(GONE);
                        resizeForTopView(false);
                        topView.setTranslationY(topView.getLayoutParams().height);
                    }
                }
            } else {
                if (!allowShowTopView) {
                    allowShowTopView = true;
                    if (needShowTopView) {
                        topView.setVisibility(VISIBLE);
                        resizeForTopView(true);
                        topView.setTranslationY(0);
                    }
                }
            }
        }
    }

    private void resizeForTopView(boolean show) {
        LayoutParams layoutParams = (LayoutParams) textFieldContainer.getLayoutParams();
        layoutParams.topMargin = AndroidUtilities.dp(2) + (show ? topView.getLayoutParams().height : 0);
        textFieldContainer.setLayoutParams(layoutParams);
        if(stickersExpanded)
            setStickersExpanded(false, true);
    }

    public void onDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.featuredStickersDidLoaded);
        if (emojiView != null) {
            emojiView.onDestroy();
        }
        if (wakeLock != null) {
            try {
                wakeLock.release();
                wakeLock = null;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (sizeNotifierLayout != null) {
            sizeNotifierLayout.setDelegate(null);
        }
    }

    public void checkChannelRights() {
        if (parentFragment == null) {
            return;
        }
        TLRPC.Chat chat = parentFragment.getCurrentChat();
        if (ChatObject.isChannel(chat)) {
            //audioVideoButtonContainer.setAlpha(chat.banned_rights == null || !chat.banned_rights.send_media ? 1.0f : 0.5f);
            if (emojiView != null) {
                emojiView.setStickersBanned(chat.banned_rights != null && chat.banned_rights.send_stickers, chat.id);
            }
        }
    }

    public void onPause() {
        isPaused = true;
        closeKeyboard();
    }

    public void onResume() {
        isPaused = false;
        if (showKeyboardOnResume) {
            showKeyboardOnResume = false;
            messageEditText.requestFocus();
            AndroidUtilities.showKeyboard(messageEditText);
            if (!AndroidUtilities.usingHardwareInput && !keyboardVisible && !AndroidUtilities.isInMultiwindow) {
                waitingForKeyboardOpen = true;
                AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
                AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
            }
        }

    }

    public void setDialogId(long id) {
        dialog_id = id;
        int lower_id = (int) dialog_id;
        int high_id = (int) (dialog_id >> 32);
        if ((int) dialog_id < 0) {
            TLRPC.Chat currentChat = MessagesController.getInstance().getChat(-(int) dialog_id);
            silent = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).getBoolean("silent_" + dialog_id, false);
            canWriteToChannel = ChatObject.isChannel(currentChat) && (currentChat.creator || currentChat.admin_rights != null && currentChat.admin_rights.post_messages) && !currentChat.megagroup;
            if (notifyButton != null) {
                notifyButton.setVisibility(canWriteToChannel ? VISIBLE : GONE);
                notifyButton.setImageResource(silent ? R.drawable.notify_members_off : R.drawable.notify_members_on);
                attachLayout.setPivotX(AndroidUtilities.dp((botButton == null || botButton.getVisibility() == GONE) && (notifyButton == null || notifyButton.getVisibility() == GONE) ? 48 : 96));
            }
            if (attachLayout != null) {
                updateFieldRight(attachLayout.getVisibility() == VISIBLE ? 1 : 0);
            }
        }
        //checkRoundVideo();
        updateFieldHint();
    }

    public void setChatInfo(TLRPC.ChatFull chatInfo) {
        info = chatInfo;
        if (emojiView != null) {
            emojiView.setChatInfo(info);
        }
    }


    private void updateFieldHint() {
        boolean isChannel = false;
        if ((int) dialog_id < 0) {
            TLRPC.Chat chat = MessagesController.getInstance().getChat(-(int) dialog_id);
            isChannel = ChatObject.isChannel(chat) && !chat.megagroup;
        }
        if (isChannel) {
            if (editingMessageObject != null) {
                messageEditText.setHintText(editingCaption ? LocaleController.getString("Caption", R.string.Caption) : LocaleController.getString("TypeMessage", R.string.TypeMessage));
            } else {
                if (silent) {
                    messageEditText.setHintText(LocaleController.getString("ChannelSilentBroadcast", R.string.ChannelSilentBroadcast));
                } else {
                    messageEditText.setHintText(LocaleController.getString("ChannelBroadcast", R.string.ChannelBroadcast));
                }
            }
        } else {
            messageEditText.setHintText(LocaleController.getString("TypeMessage", R.string.TypeMessage));
        }
    }

    public void setReplyingMessageObject(MessageObject messageObject) {
        if (messageObject != null) {
            if (botMessageObject == null && botButtonsMessageObject != replyingMessageObject) {
                botMessageObject = botButtonsMessageObject;
            }
            replyingMessageObject = messageObject;
            setButtons(replyingMessageObject, true);
        } else if (messageObject == null && replyingMessageObject == botButtonsMessageObject) {
            replyingMessageObject = null;
            setButtons(botMessageObject, false);
            botMessageObject = null;
        } else {
            replyingMessageObject = messageObject;
        }
    }

    public void setWebPage(TLRPC.WebPage webPage, boolean searchWebPages) {
        messageWebPage = webPage;
        messageWebPageSearch = searchWebPages;
    }

    public boolean isMessageWebPageSearchEnabled() {
        return messageWebPageSearch;
    }


    private void sendMessage() {
        if (parentFragment != null) {
            String action;
            TLRPC.Chat currentChat;
            if ((int) dialog_id < 0) {
                currentChat = MessagesController.getInstance().getChat(-(int) dialog_id);
                if (currentChat != null && currentChat.participants_count > MessagesController.getInstance().groupBigSize) {
                    action = "bigchat_message";
                } else {
                    action = "chat_message";
                }
            } else {
                action = "pm_message";
            }
            if (!MessagesController.isFeatureEnabled(action, parentFragment)) {
                return;
            }
        }

        CharSequence message = messageEditText.getText();
        if (processSendingText(message)) {
            messageEditText.setText("");
            lastTypingTimeSend = 0;
            if (delegate != null) {
                delegate.onMessageSend(message);
            }
        } else if (forceShowSendButton) {
            if (delegate != null) {
                delegate.onMessageSend(null);
            }
        }
    }

    public void doneEditingMessage() {
        showEdit(false);
        if (editingMessageObject != null) {
            delegate.onMessageEditEnd(true);
            showEditDoneProgress(true, true);
            CharSequence[] message = new CharSequence[]{messageEditText.getText()};
            ArrayList<TLRPC.MessageEntity> entities = MessagesQuery.getEntities(message);
            editingMessageReqId = SendMessagesHelper.getInstance().editMessage(editingMessageObject, message[0].toString(), messageWebPageSearch, parentFragment, entities, new Runnable() {
                @Override
                public void run() {
                    editingMessageReqId = 0;
                    setEditingMessageObject(null, false);
                }
            });
        }
    }

    public boolean processSendingText(CharSequence text) {
        text = AndroidUtilities.getTrimmedString(text);
        if (text.length() != 0) {
            int count = (int) Math.ceil(text.length() / 4096.0f);
            for (int a = 0; a < count; a++) {
                CharSequence[] message = new CharSequence[]{text.subSequence(a * 4096, Math.min((a + 1) * 4096, text.length()))};
                ArrayList<TLRPC.MessageEntity> entities = MessagesQuery.getEntities(message);
                SendMessagesHelper.getInstance().sendMessage(message[0].toString(), dialog_id, replyingMessageObject, messageWebPage, messageWebPageSearch, entities, null, null);
            }
            return true;
        }
        return false;
    }

    private void checkSendButton(boolean animated) {

        if (editingMessageObject != null) {
            return;
        }
        if (isPaused) {
            animated = false;
        }
        CharSequence message = AndroidUtilities.getTrimmedString(messageEditText.getText());

        if (message.length() > 0 || forceShowSendButton || audioToSend != null || videoToSendMessageObject != null) {
            final String caption = messageEditText.getCaption();
            Log.d("XXX",(editingMessageObject==null)+","+isPaused+","+cancelBotButton.getVisibility()+"."+(expandStickersButton.getVisibility()));
            boolean showBotButton = caption != null && (sendButton.getVisibility() == VISIBLE || expandStickersButton.getVisibility() == VISIBLE);
            boolean showSendButton = caption == null && (true || cancelBotButton.getVisibility() == VISIBLE || expandStickersButton.getVisibility() == VISIBLE);
            if (showBotButton || showSendButton) {
                    //audioVideoButtonContainer.setScaleX(0.1f);
                    //audioVideoButtonContainer.setScaleY(0.1f);
                    //audioVideoButtonContainer.setAlpha(0.0f);
                    if (caption != null) {
                        sendButton.setScaleX(0.1f);
                        sendButton.setScaleY(0.1f);
                        sendButton.setAlpha(0.0f);
                        cancelBotButton.setScaleX(1.0f);
                        cancelBotButton.setScaleY(1.0f);
                        cancelBotButton.setAlpha(1.0f);
                        cancelBotButton.setVisibility(VISIBLE);
                        sendButton.setVisibility(GONE);
                    } else {
                        cancelBotButton.setScaleX(0.1f);
                        cancelBotButton.setScaleY(0.1f);
                        cancelBotButton.setAlpha(0.0f);
                        sendButton.setScaleX(1.0f);
                        sendButton.setScaleY(1.0f);
                        sendButton.setAlpha(1.0f);
                        sendButton.setVisibility(VISIBLE);
                        cancelBotButton.setVisibility(GONE);
                    }
                   // audioVideoButtonContainer.setVisibility(GONE);
                    if (attachLayout != null) {
                        attachLayout.setVisibility(GONE);
                        if (delegate != null && getVisibility() == VISIBLE) {
                            delegate.onAttachButtonHidden();
                        }
                        updateFieldRight(0);
                    }
            }
        } else if (emojiView != null && emojiView.getVisibility() == VISIBLE && stickersTabOpen && !AndroidUtilities.isInMultiwindow) {
            if (animated) {
                if (runningAnimationType == 4) {
                    return;
                }

                if (runningAnimation != null) {
                    runningAnimation.cancel();
                    runningAnimation = null;
                }
                if (runningAnimation2 != null) {
                    runningAnimation2.cancel();
                    runningAnimation2 = null;
                }

                if (attachLayout != null) {
                    attachLayout.setVisibility(VISIBLE);
                    runningAnimation2 = new AnimatorSet();
                    runningAnimation2.playTogether(
                            ObjectAnimator.ofFloat(attachLayout, "alpha", 1.0f),
                            ObjectAnimator.ofFloat(attachLayout, "scaleX", 1.0f)
                    );
                    runningAnimation2.setDuration(100);
                    runningAnimation2.start();
                    updateFieldRight(1);
                    if (getVisibility() == VISIBLE) {
                        delegate.onAttachButtonShow();
                    }
                }

                expandStickersButton.setVisibility(VISIBLE);
                runningAnimation = new AnimatorSet();
                runningAnimationType = 4;

                ArrayList<Animator> animators = new ArrayList<>();
                animators.add(ObjectAnimator.ofFloat(expandStickersButton, "scaleX", 1.0f));
                animators.add(ObjectAnimator.ofFloat(expandStickersButton, "scaleY", 1.0f));
                animators.add(ObjectAnimator.ofFloat(expandStickersButton, "alpha", 1.0f));
                if (cancelBotButton.getVisibility() == VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, "scaleX", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, "scaleY", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, "alpha", 0.0f));
                } else {
                    animators.add(ObjectAnimator.ofFloat(sendButton, "scaleX", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(sendButton, "scaleY", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(sendButton, "alpha", 0.0f));
                }

                runningAnimation.playTogether(animators);
                runningAnimation.setDuration(150);
                runningAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (runningAnimation != null && runningAnimation.equals(animation)) {
                            sendButton.setVisibility(GONE);
                            cancelBotButton.setVisibility(GONE);
                           // audioVideoButtonContainer.setVisibility(GONE);
                            expandStickersButton.setVisibility(VISIBLE);
                            runningAnimation = null;
                            runningAnimationType = 0;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (runningAnimation != null && runningAnimation.equals(animation)) {
                            runningAnimation = null;
                        }
                    }
                });
                runningAnimation.start();
            } else {
                sendButton.setScaleX(0.1f);
                sendButton.setScaleY(0.1f);
                sendButton.setAlpha(0.0f);
                cancelBotButton.setScaleX(0.1f);
                cancelBotButton.setScaleY(0.1f);
                cancelBotButton.setAlpha(0.0f);
                //audioVideoButtonContainer.setScaleX(0.1f);
               // audioVideoButtonContainer.setScaleY(0.1f);
               // audioVideoButtonContainer.setAlpha(0.0f);
                expandStickersButton.setScaleX(1.0f);
                expandStickersButton.setScaleY(1.0f);
                expandStickersButton.setAlpha(1.0f);
                cancelBotButton.setVisibility(GONE);
                sendButton.setVisibility(GONE);
                //audioVideoButtonContainer.setVisibility(GONE);
                expandStickersButton.setVisibility(VISIBLE);
                if (attachLayout != null) {
                    if (getVisibility() == VISIBLE) {
                        delegate.onAttachButtonShow();
                    }
                    attachLayout.setVisibility(VISIBLE);
                    updateFieldRight(1);
                }
            }
            expandStickersButton.setAlpha(1f);
            expandStickersButton.setScaleX(1);
            expandStickersButton.setScaleY(1);
            expandStickersButton.setVisibility(VISIBLE);
            //audioVideoButtonContainer.setVisibility(GONE);*/
        } else if (sendButton.getVisibility() == VISIBLE || cancelBotButton.getVisibility() == VISIBLE || expandStickersButton.getVisibility() == VISIBLE) {
            if (animated) {
                if (runningAnimationType == 2) {
                    return;
                }

                if (runningAnimation != null) {
                    runningAnimation.cancel();
                    runningAnimation = null;
                }
                if (runningAnimation2 != null) {
                    runningAnimation2.cancel();
                    runningAnimation2 = null;
                }

                if (attachLayout != null) {
                    attachLayout.setVisibility(VISIBLE);
                    runningAnimation2 = new AnimatorSet();
                    runningAnimation2.playTogether(
                            ObjectAnimator.ofFloat(attachLayout, "alpha", 1.0f),
                            ObjectAnimator.ofFloat(attachLayout, "scaleX", 1.0f)
                    );
                    runningAnimation2.setDuration(100);
                    runningAnimation2.start();
                    updateFieldRight(1);
                    if (getVisibility() == VISIBLE) {
                        delegate.onAttachButtonShow();
                    }
                }

                //audioVideoButtonContainer.setVisibility(VISIBLE);
                runningAnimation = new AnimatorSet();
                runningAnimationType = 2;

                ArrayList<Animator> animators = new ArrayList<>();
               // animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, "scaleX", 1.0f));
                //animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, "scaleY", 1.0f));
               // animators.add(ObjectAnimator.ofFloat(audioVideoButtonContainer, "alpha", 1.0f));
                if (cancelBotButton.getVisibility() == VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, "scaleX", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, "scaleY", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(cancelBotButton, "alpha", 0.0f));
                } else if (expandStickersButton.getVisibility() == VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(expandStickersButton, "scaleX", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(expandStickersButton, "scaleY", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(expandStickersButton, "alpha", 0.0f));
                } else {
                    animators.add(ObjectAnimator.ofFloat(sendButton, "scaleX", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(sendButton, "scaleY", 0.1f));
                    animators.add(ObjectAnimator.ofFloat(sendButton, "alpha", 0.0f));
                }

                runningAnimation.playTogether(animators);
                runningAnimation.setDuration(150);
                runningAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (runningAnimation != null && runningAnimation.equals(animation)) {
                            sendButton.setVisibility(GONE);
                            cancelBotButton.setVisibility(GONE);
                            //audioVideoButtonContainer.setVisibility(VISIBLE);
                            runningAnimation = null;
                            runningAnimationType = 0;
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (runningAnimation != null && runningAnimation.equals(animation)) {
                            runningAnimation = null;
                        }
                    }
                });
                runningAnimation.start();
            } else {
                sendButton.setScaleX(0.1f);
                sendButton.setScaleY(0.1f);
                sendButton.setAlpha(0.0f);
                cancelBotButton.setScaleX(0.1f);
                cancelBotButton.setScaleY(0.1f);
                cancelBotButton.setAlpha(0.0f);
                expandStickersButton.setScaleX(0.1f);
                expandStickersButton.setScaleY(0.1f);
                expandStickersButton.setAlpha(0.0f);
                //audioVideoButtonContainer.setScaleX(1.0f);
                //audioVideoButtonContainer.setScaleY(1.0f);
                //audioVideoButtonContainer.setAlpha(1.0f);
                cancelBotButton.setVisibility(GONE);
                sendButton.setVisibility(GONE);
                expandStickersButton.setVisibility(GONE);
                //audioVideoButtonContainer.setVisibility(VISIBLE);
                if (attachLayout != null) {
                    if (getVisibility() == VISIBLE) {
                        delegate.onAttachButtonShow();
                    }
                    attachLayout.setVisibility(VISIBLE);
                    updateFieldRight(1);
                }
            }
        }
    }
    private void updateFieldRight(int attachVisible) {
        if (messageEditText == null || editingMessageObject != null) {
            return;
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messageEditText.getLayoutParams();
        if (attachVisible == 1) {
            if (botButton != null && botButton.getVisibility() == VISIBLE || notifyButton != null && notifyButton.getVisibility() == VISIBLE) {
                layoutParams.rightMargin = AndroidUtilities.dp(98);
            } else {
                layoutParams.rightMargin = AndroidUtilities.dp(50);
            }
        } else if (attachVisible == 2) {
            if (layoutParams.rightMargin != AndroidUtilities.dp(2)) {
                if (botButton != null && botButton.getVisibility() == VISIBLE || notifyButton != null && notifyButton.getVisibility() == VISIBLE) {
                    layoutParams.rightMargin = AndroidUtilities.dp(98);
                } else {
                    layoutParams.rightMargin = AndroidUtilities.dp(50);
                }
            }
        } else {
            layoutParams.rightMargin = AndroidUtilities.dp(2);
        }
        messageEditText.setLayoutParams(layoutParams);
    }



    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (recordingAudioVideo) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return super.onInterceptTouchEvent(ev);
    }

    public void setDelegate(ChatActivityEnterViewDelegate chatActivityEnterViewDelegate) {
        delegate = chatActivityEnterViewDelegate;
    }

    public void setCommand(MessageObject messageObject, String command, boolean longPress, boolean username) {
        if (command == null || getVisibility() != VISIBLE) {
            return;
        }
        if (longPress) {
            String text = messageEditText.getText().toString();
            TLRPC.User user = messageObject != null && (int) dialog_id < 0 ? MessagesController.getInstance().getUser(messageObject.messageOwner.from_id) : null;
            if ((botCount != 1 || username) && user != null && user.bot && !command.contains("@")) {
                text = String.format(Locale.US, "%s@%s", command, user.username) + " " + text.replaceFirst("^/[a-zA-Z@\\d_]{1,255}(\\s|$)", "");
            } else {
                text = command + " " + text.replaceFirst("^/[a-zA-Z@\\d_]{1,255}(\\s|$)", "");
            }
            ignoreTextChange = true;
            messageEditText.setText(text);
            messageEditText.setSelection(messageEditText.getText().length());
            ignoreTextChange = false;
            if (delegate != null) {
                delegate.onTextChanged(messageEditText.getText(), true);
            }
            if (!keyboardVisible && currentPopupContentType == -1) {
                openKeyboard();
            }
        } else {
            TLRPC.User user = messageObject != null && (int) dialog_id < 0 ? MessagesController.getInstance().getUser(messageObject.messageOwner.from_id) : null;
            if ((botCount != 1 || username) && user != null && user.bot && !command.contains("@")) {
                SendMessagesHelper.getInstance().sendMessage(String.format(Locale.US, "%s@%s", command, user.username), dialog_id, replyingMessageObject, null, false, null, null, null);
            } else {
                SendMessagesHelper.getInstance().sendMessage(command, dialog_id, replyingMessageObject, null, false, null, null, null);
            }
        }
    }

    public void setEditingMessageObject(MessageObject messageObject, boolean caption) {
        if (audioToSend != null || videoToSendMessageObject != null || editingMessageObject == messageObject) {
            return;
        }
        if (editingMessageReqId != 0) {
            ConnectionsManager.getInstance().cancelRequest(editingMessageReqId, true);
            editingMessageReqId = 0;
        }
        editingMessageObject = messageObject;
        editingCaption = caption;
        if (editingMessageObject != null) {
            if (doneButtonAnimation != null) {
                doneButtonAnimation.cancel();
                doneButtonAnimation = null;
            }
            doneButtonContainer.setVisibility(View.VISIBLE);
            showEditDoneProgress(true, false);

            InputFilter[] inputFilters = new InputFilter[1];
            if (caption) {
                inputFilters[0] = new InputFilter.LengthFilter(200);
                if (editingMessageObject.caption != null) {
                    setFieldText(Emoji.replaceEmoji(new SpannableStringBuilder(editingMessageObject.caption.toString()), messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false));
                } else {
                    setFieldText("");
                }
            } else {
                inputFilters[0] = new InputFilter.LengthFilter(4096);
                if (editingMessageObject.messageText != null) {
                    ArrayList<TLRPC.MessageEntity> entities = editingMessageObject.messageOwner.entities;//MessagesQuery.getEntities(message);
                    MessagesQuery.sortEntities(entities);
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(editingMessageObject.messageText);
                    Object spansToRemove[] = stringBuilder.getSpans(0, stringBuilder.length(), Object.class);
                    if (spansToRemove != null && spansToRemove.length > 0) {
                        for (int a = 0; a < spansToRemove.length; a++) {
                            stringBuilder.removeSpan(spansToRemove[a]);
                        }
                    }
                    if (entities != null) {
                        int addToOffset = 0;
                        try {
                            for (int a = 0; a < entities.size(); a++) {
                                TLRPC.MessageEntity entity = entities.get(a);
                                if (entity.offset + entity.length + addToOffset > stringBuilder.length()) {
                                    continue;
                                }
                                if (entity instanceof TLRPC.TL_inputMessageEntityMentionName) {
                                    if (entity.offset + entity.length + addToOffset < stringBuilder.length() && stringBuilder.charAt(entity.offset + entity.length + addToOffset) == ' ') {
                                        entity.length++;
                                    }
                                    stringBuilder.setSpan(new URLSpanUserMention("" + ((TLRPC.TL_inputMessageEntityMentionName) entity).user_id.user_id, true), entity.offset + addToOffset, entity.offset + entity.length + addToOffset, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                } else if (entity instanceof TLRPC.TL_messageEntityCode) {
                                    stringBuilder.insert(entity.offset + entity.length + addToOffset, "`");
                                    stringBuilder.insert(entity.offset + addToOffset, "`");
                                    addToOffset += 2;
                                } else if (entity instanceof TLRPC.TL_messageEntityPre) {
                                    stringBuilder.insert(entity.offset + entity.length + addToOffset, "```");
                                    stringBuilder.insert(entity.offset + addToOffset, "```");
                                    addToOffset += 6;
                                } else if (entity instanceof TLRPC.TL_messageEntityBold) {
                                    stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), entity.offset + addToOffset, entity.offset + entity.length + addToOffset, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                } else if (entity instanceof TLRPC.TL_messageEntityItalic) {
                                    stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/ritalic.ttf")), entity.offset + addToOffset, entity.offset + entity.length + addToOffset, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    setFieldText(Emoji.replaceEmoji(stringBuilder, messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false));
                } else {
                    setFieldText("");
                }
            }
            messageEditText.setFilters(inputFilters);
            openKeyboard();
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messageEditText.getLayoutParams();
            layoutParams.rightMargin = AndroidUtilities.dp(4);
            messageEditText.setLayoutParams(layoutParams);
            sendButton.setVisibility(GONE);
            cancelBotButton.setVisibility(GONE);
            //audioVideoButtonContainer.setVisibility(GONE);
            attachLayout.setVisibility(GONE);
            sendButtonContainer.setVisibility(GONE);
        } else {
            doneButtonContainer.setVisibility(View.GONE);
            messageEditText.setFilters(new InputFilter[0]);
            delegate.onMessageEditEnd(false);
            //audioVideoButtonContainer.setVisibility(VISIBLE);
            attachLayout.setVisibility(VISIBLE);
            sendButtonContainer.setVisibility(VISIBLE);
            attachLayout.setScaleX(1.0f);
            attachLayout.setAlpha(1.0f);
            sendButton.setScaleX(0.1f);
            sendButton.setScaleY(0.1f);
            sendButton.setAlpha(0.0f);
            cancelBotButton.setScaleX(0.1f);
            cancelBotButton.setScaleY(0.1f);
            cancelBotButton.setAlpha(0.0f);
            //audioVideoButtonContainer.setScaleX(1.0f);
            //audioVideoButtonContainer.setScaleY(1.0f);
            //audioVideoButtonContainer.setAlpha(1.0f);
            sendButton.setVisibility(GONE);
            cancelBotButton.setVisibility(GONE);
            messageEditText.setText("");
            if (getVisibility() == VISIBLE) {
                delegate.onAttachButtonShow();
            }
            updateFieldRight(1);
        }
        updateFieldHint();
    }

    public ImageView getAttachButton() {
        return attachButton;
    }

    public ImageView getBotButton() {
        return botButton;
    }

    public ImageView getEmojiButton() {
        return emojiButton;
    }

    public ImageView getSendButton() {
        return sendButton;
    }

    public EmojiView getEmojiView() {
        return emojiView;
    }

    public void setFieldText(CharSequence text) {
        if (messageEditText == null) {
            return;
        }
        ignoreTextChange = true;
        messageEditText.setText(text);
        messageEditText.setSelection(messageEditText.getText().length());
        ignoreTextChange = false;
        if (delegate != null) {
            delegate.onTextChanged(messageEditText.getText(), true);
        }
    }

    public void setSelection(int start) {
        if (messageEditText == null) {
            return;
        }
        messageEditText.setSelection(start, messageEditText.length());
    }

    public int getCursorPosition() {
        if (messageEditText == null) {
            return 0;
        }
        return messageEditText.getSelectionStart();
    }

    public int getSelectionLength() {
        if (messageEditText == null) {
            return 0;
        }
        try {
            return messageEditText.getSelectionEnd() - messageEditText.getSelectionStart();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    public void replaceWithText(int start, int len, CharSequence text, boolean parseEmoji) {
        try {
            SpannableStringBuilder builder = new SpannableStringBuilder(messageEditText.getText());
            builder.replace(start, start + len, text);
            if (parseEmoji) {
                Emoji.replaceEmoji(builder, messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
            }
            messageEditText.setText(builder);
            messageEditText.setSelection(start + text.length());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void setFieldFocused() {
        if (messageEditText != null) {
            try {
                messageEditText.requestFocus();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public void setFieldFocused(boolean focus) {
        if (messageEditText == null) {
            return;
        }
        if (focus) {
            if (!messageEditText.isFocused()) {
                messageEditText.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (messageEditText != null) {
                            try {
                                messageEditText.requestFocus();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                }, 600);
            }
        } else {
            if (messageEditText.isFocused() && !keyboardVisible) {
                messageEditText.clearFocus();
            }
        }
    }

    public boolean hasText() {
        return messageEditText != null && messageEditText.length() > 0;
    }

    public CharSequence getFieldText() {
        if (messageEditText != null && messageEditText.length() > 0) {
            return messageEditText.getText();
        }
        return null;
    }

    private void updateBotButton() {
        if (botButton == null) {
            return;
        }
        if (hasBotCommands || botReplyMarkup != null) {
            if (botButton.getVisibility() != VISIBLE) {
                botButton.setVisibility(VISIBLE);
            }
            if (botReplyMarkup != null) {
                if (isPopupShowing() && currentPopupContentType == 1) {
                    botButton.setImageResource(R.drawable.ic_msg_panel_kb);
                } else {
                    botButton.setImageResource(R.drawable.bot_keyboard2);
                }
            } else {
                botButton.setImageResource(R.drawable.bot_keyboard);
            }
        } else {
            botButton.setVisibility(GONE);
        }
        updateFieldRight(2);
        attachLayout.setPivotX(AndroidUtilities.dp((botButton == null || botButton.getVisibility() == GONE) && (notifyButton == null || notifyButton.getVisibility() == GONE) ? 48 : 96));
    }

    public void setBotsCount(int count, boolean hasCommands) {
        botCount = count;
        if (hasBotCommands != hasCommands) {
            hasBotCommands = hasCommands;
            updateBotButton();
        }
    }

    public void setButtons(MessageObject messageObject) {
        setButtons(messageObject, true);
    }

    public void setButtons(MessageObject messageObject, boolean openKeyboard) {
        if (replyingMessageObject != null && replyingMessageObject == botButtonsMessageObject && replyingMessageObject != messageObject) {
            botMessageObject = messageObject;
            return;
        }
        if (botButton == null || botButtonsMessageObject != null && botButtonsMessageObject == messageObject || botButtonsMessageObject == null && messageObject == null) {
            return;
        }
        if (botKeyboardView == null) {
            botKeyboardView = new BotKeyboardView(parentActivity);
            botKeyboardView.setVisibility(GONE);
            botKeyboardView.setDelegate(new BotKeyboardView.BotKeyboardViewDelegate() {
                @Override
                public void didPressedButton(TLRPC.KeyboardButton button) {
                    MessageObject object = replyingMessageObject != null ? replyingMessageObject : ((int) dialog_id < 0 ? botButtonsMessageObject : null);
                    didPressedBotButton(button, object, replyingMessageObject != null ? replyingMessageObject : botButtonsMessageObject);
                    if (replyingMessageObject != null) {
                        openKeyboardInternal();
                        setButtons(botMessageObject, false);
                    } else if (botButtonsMessageObject.messageOwner.reply_markup.single_use) {
                        openKeyboardInternal();
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                        preferences.edit().putInt("answered_" + dialog_id, botButtonsMessageObject.getId()).commit();
                    }
                    if (delegate != null) {
                        delegate.onMessageSend(null);
                    }
                }
            });
            sizeNotifierLayout.addView(botKeyboardView);
        }
        botButtonsMessageObject = messageObject;
        botReplyMarkup = messageObject != null && messageObject.messageOwner.reply_markup instanceof TLRPC.TL_replyKeyboardMarkup ? (TLRPC.TL_replyKeyboardMarkup) messageObject.messageOwner.reply_markup : null;

        botKeyboardView.setPanelHeight(AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight);
        botKeyboardView.setButtons(botReplyMarkup);
        if (botReplyMarkup != null) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            boolean keyboardHidden = preferences.getInt("hidekeyboard_" + dialog_id, 0) == messageObject.getId();
            if (botButtonsMessageObject != replyingMessageObject && botReplyMarkup.single_use) {
                if (preferences.getInt("answered_" + dialog_id, 0) == messageObject.getId()) {
                    return;
                }
            }
            if (!keyboardHidden && messageEditText.length() == 0 && !isPopupShowing()) {
                showPopup(1, 1);
            }
        } else {
            if (isPopupShowing() && currentPopupContentType == 1) {
                if (openKeyboard) {
                    openKeyboardInternal();
                } else {
                    showPopup(0, 1);
                }
            }
        }
        updateBotButton();
    }

    public void didPressedBotButton(final TLRPC.KeyboardButton button, final MessageObject replyMessageObject, final MessageObject messageObject) {
        if (button == null || messageObject == null) {
            return;
        }
        if (button instanceof TLRPC.TL_keyboardButton) {
            SendMessagesHelper.getInstance().sendMessage(button.text, dialog_id, replyMessageObject, null, false, null, null, null);
        } else if (button instanceof TLRPC.TL_keyboardButtonUrl) {
            parentFragment.showOpenUrlAlert(button.url, true);
        } else if (button instanceof TLRPC.TL_keyboardButtonRequestPhone) {
            parentFragment.shareMyContact(messageObject);
        } else if (button instanceof TLRPC.TL_keyboardButtonRequestGeoLocation) {
            AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
            builder.setTitle(LocaleController.getString("ShareYouLocationTitle", R.string.ShareYouLocationTitle));
            builder.setMessage(LocaleController.getString("ShareYouLocationInfo", R.string.ShareYouLocationInfo));
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (Build.VERSION.SDK_INT >= 23 && parentActivity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        parentActivity.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 2);
                        pendingMessageObject = messageObject;
                        pendingLocationButton = button;
                        return;
                    }
                    SendMessagesHelper.getInstance().sendCurrentLocation(messageObject, button);
                }
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            parentFragment.showDialog(builder.create());
        } else if (button instanceof TLRPC.TL_keyboardButtonCallback || button instanceof TLRPC.TL_keyboardButtonGame || button instanceof TLRPC.TL_keyboardButtonBuy) {
            SendMessagesHelper.getInstance().sendCallback(true, messageObject, button, parentFragment);
        } else if (button instanceof TLRPC.TL_keyboardButtonSwitchInline) {
            if (parentFragment.processSwitchButton((TLRPC.TL_keyboardButtonSwitchInline) button)) {
                return;
            }
            if (button.same_peer) {
                int uid = messageObject.messageOwner.from_id;
                if (messageObject.messageOwner.via_bot_id != 0) {
                    uid = messageObject.messageOwner.via_bot_id;
                }
                TLRPC.User user = MessagesController.getInstance().getUser(uid);
                if (user == null) {
                    return;
                }
                setFieldText("@" + user.username + " " + button.query);
            } else {
                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putInt("dialogsType", 1);
                DialogsActivity fragment = new DialogsActivity(args);
                fragment.setDelegate(new DialogsActivity.DialogsActivityDelegate() {
                    @Override
                    public void didSelectDialogs(DialogsActivity fragment, ArrayList<Long> dids, CharSequence message, boolean param) {
                        int uid = messageObject.messageOwner.from_id;
                        if (messageObject.messageOwner.via_bot_id != 0) {
                            uid = messageObject.messageOwner.via_bot_id;
                        }
                        TLRPC.User user = MessagesController.getInstance().getUser(uid);
                        if (user == null) {
                            fragment.finishFragment();
                            return;
                        }
                        long did = dids.get(0);
                        DraftQuery.saveDraft(did, "@" + user.username + " " + button.query, null, null, true);
                        if (did != dialog_id) {
                            int lower_part = (int) did;
                            if (lower_part != 0) {
                                Bundle args = new Bundle();
                                if (lower_part > 0) {
                                    args.putInt("user_id", lower_part);
                                } else if (lower_part < 0) {
                                    args.putInt("chat_id", -lower_part);
                                }
                                if (!MessagesController.checkCanOpenChat(args, fragment)) {
                                    return;
                                }
                                ChatActivity chatActivity = new ChatActivity(args);
                                if (parentFragment.presentFragment(chatActivity, true)) {
                                    if (!AndroidUtilities.isTablet()) {
                                        parentFragment.removeSelfFromStack();
                                    }
                                } else {
                                    fragment.finishFragment();
                                }
                            } else {
                                fragment.finishFragment();
                            }
                        } else {
                            fragment.finishFragment();
                        }
                    }
                });
                parentFragment.presentFragment(fragment);
            }
        }
    }

    public boolean isPopupView(View view) {
        return view == botKeyboardView || view == emojiView;
    }



    private void createEmojiView() {
        if (emojiView != null) {
            return;
        }
        emojiView = new EmojiView(allowStickers, allowGifs, parentActivity, info);
        emojiView.setVisibility(GONE);
        emojiView.setListener(new EmojiView.Listener() {
            public boolean onBackspace() {
                if (messageEditText.length() == 0) {
                    return false;
                }
                messageEditText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                return true;
            }

            public void onEmojiSelected(String symbol) {
                int i = messageEditText.getSelectionEnd();
                if (i < 0) {
                    i = 0;
                }
                try {
                    innerTextChange = 2;
                    CharSequence localCharSequence = Emoji.replaceEmoji(symbol, messageEditText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
                    messageEditText.setText(messageEditText.getText().insert(i, localCharSequence));
                    int j = i + localCharSequence.length();
                    messageEditText.setSelection(j, j);
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    innerTextChange = 0;
                }
            }

            public void onStickerSelected(TLRPC.Document sticker) {
                if (stickersExpanded) {
                    setStickersExpanded(false, true);
                }
                ChatActivityEnterView.this.onStickerSelected(sticker);
                StickersQuery.addRecentSticker(StickersQuery.TYPE_IMAGE, sticker, (int) (System.currentTimeMillis() / 1000), false);
                if ((int) dialog_id == 0) {
                    MessagesController.getInstance().saveGif(sticker);
                }
            }

            @Override
            public void onStickersSettingsClick() {
                if (parentFragment != null) {
                    parentFragment.presentFragment(new StickersActivity(StickersQuery.TYPE_IMAGE));
                }
            }

            @Override
            public void onGifSelected(TLRPC.Document gif) {
                if (stickersExpanded) {
                    setStickersExpanded(false, true);
                }
                SendMessagesHelper.getInstance().sendSticker(gif, dialog_id, replyingMessageObject);
                StickersQuery.addRecentGif(gif, (int) (System.currentTimeMillis() / 1000));
                if ((int) dialog_id == 0) {
                    MessagesController.getInstance().saveGif(gif);
                }
                if (delegate != null) {
                    delegate.onMessageSend(null);
                }
            }

            @Override
            public void onGifTab(boolean opened) {
                post(updateExpandabilityRunnable);
                if (!AndroidUtilities.usingHardwareInput) {
                    if (opened) {
                        if (messageEditText.length() == 0) {
                            messageEditText.setText("@gif ");
                            messageEditText.setSelection(messageEditText.length());
                        }
                    } else if (messageEditText.getText().toString().equals("@gif ")) {
                        messageEditText.setText("");
                    }
                }
            }

            @Override
            public void onStickersTab(boolean opened) {
                delegate.onStickersTab(opened);
                post(updateExpandabilityRunnable);
            }

            @Override
            public void onClearEmojiRecent() {
                if (parentFragment == null || parentActivity == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(LocaleController.getString("ClearRecentEmoji", R.string.ClearRecentEmoji));
                builder.setPositiveButton(LocaleController.getString("ClearButton", R.string.ClearButton).toUpperCase(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        emojiView.clearRecentEmoji();
                    }
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                parentFragment.showDialog(builder.create());
            }

            @Override
            public void onShowStickerSet(TLRPC.StickerSet stickerSet, TLRPC.InputStickerSet inputStickerSet) {
                if (parentFragment == null || parentActivity == null) {
                    return;
                }
                if (stickerSet != null) {
                    inputStickerSet = new TLRPC.TL_inputStickerSetID();
                    inputStickerSet.access_hash = stickerSet.access_hash;
                    inputStickerSet.id = stickerSet.id;
                }
                parentFragment.showDialog(new StickersAlert(parentActivity, parentFragment, inputStickerSet, null, ChatActivityEnterView.this));
            }

            @Override
            public void onStickerSetAdd(TLRPC.StickerSetCovered stickerSet) {
                StickersQuery.removeStickersSet(parentActivity, stickerSet.set, 2, parentFragment, false);
            }

            @Override
            public void onStickerSetRemove(TLRPC.StickerSetCovered stickerSet) {
                StickersQuery.removeStickersSet(parentActivity, stickerSet.set, 0, parentFragment, false);
            }

            @Override
            public void onStickersGroupClick(int chatId) {
                if (parentFragment != null) {
                    if (AndroidUtilities.isTablet()) {
                        hidePopup(false);
                    }
                    GroupStickersActivity fragment = new GroupStickersActivity(chatId);
                    fragment.setInfo(info);
                    parentFragment.presentFragment(fragment);
                }
            }
        });
        emojiView.setDragListener(new EmojiView.DragListener() {

            boolean wasExpanded;
            int initialOffset;

            @Override
            public void onDragStart() {
                if (!allowDragging()) {
                    return;
                }
                if (stickersExpansionAnim != null)
                    stickersExpansionAnim.cancel();
                stickersDragging = true;
                wasExpanded = stickersExpanded;
                stickersExpanded = true;
                stickersExpandedHeight = sizeNotifierLayout.getHeight() - (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? AndroidUtilities.statusBarHeight : 0) - ActionBar.getCurrentActionBarHeight() - getHeight() + Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                emojiView.getLayoutParams().height = stickersExpandedHeight;
                emojiView.setLayerType(LAYER_TYPE_HARDWARE, null);
                sizeNotifierLayout.requestLayout();
                sizeNotifierLayout.setForeground(new ScrimDrawable());
                initialOffset = (int) getTranslationY();
            }

            @Override
            public void onDragEnd(float velocity) {
                if (!allowDragging())
                    return;
                stickersDragging = false;
                if ((wasExpanded && velocity >= AndroidUtilities.dp(200)) || (!wasExpanded && velocity <= AndroidUtilities.dp(-200)) || (wasExpanded && stickersExpansionProgress <= 0.6f) || (!wasExpanded && stickersExpansionProgress >= 0.4f)) {
                    setStickersExpanded(!wasExpanded, true);
                } else {
                    setStickersExpanded(wasExpanded, true);
                }
            }

            @Override
            public void onDragCancel() {
                if (!stickersTabOpen) {
                    return;
                }
                stickersDragging = false;
                setStickersExpanded(wasExpanded, true);
            }

            @Override
            public void onDrag(int offset) {
                if (!allowDragging()) {
                    return;
                }
                int origHeight = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight;
                offset += initialOffset;
                offset = Math.max(Math.min(offset, 0), -(stickersExpandedHeight - origHeight));
                emojiView.setTranslationY(offset);
                setTranslationY(offset);
                stickersExpansionProgress = (float) offset / (-(stickersExpandedHeight - origHeight));
                sizeNotifierLayout.invalidate();
            }

            private boolean allowDragging(){
                return stickersTabOpen && !(!stickersExpanded && messageEditText.length()>0) && emojiView.areThereAnyStickers();
            }
        });
        emojiView.setVisibility(GONE);
        sizeNotifierLayout.addView(emojiView);
        checkChannelRights();
    }

    @Override
    public void onStickerSelected(TLRPC.Document sticker) {
        SendMessagesHelper.getInstance().sendSticker(sticker, dialog_id, replyingMessageObject);
        if (delegate != null) {
            delegate.onMessageSend(null);
        }
    }

    public void addStickerToRecent(TLRPC.Document sticker) {
        createEmojiView();
        emojiView.addRecentSticker(sticker);
    }

    private void showPopup(int show, int contentType) {
        if (show == 1) {
            if (contentType == 0 && emojiView == null) {
                if (parentActivity == null) {
                    return;
                }
                createEmojiView();
            }

            View currentView = null;
            if (contentType == 0) {
                emojiView.setVisibility(VISIBLE);
                if (botKeyboardView != null && botKeyboardView.getVisibility() != GONE) {
                    botKeyboardView.setVisibility(GONE);
                }
                currentView = emojiView;
            } else if (contentType == 1) {
                if (emojiView != null && emojiView.getVisibility() != GONE) {
                    emojiView.setVisibility(GONE);
                }
                botKeyboardView.setVisibility(VISIBLE);
                currentView = botKeyboardView;
            }
            currentPopupContentType = contentType;

            if (keyboardHeight <= 0) {
                keyboardHeight = ApplicationLoader.applicationContext.getSharedPreferences("emoji", Context.MODE_PRIVATE).getInt("kbd_height", AndroidUtilities.dp(200));
            }
            if (keyboardHeightLand <= 0) {
                keyboardHeightLand = ApplicationLoader.applicationContext.getSharedPreferences("emoji", Context.MODE_PRIVATE).getInt("kbd_height_land3", AndroidUtilities.dp(200));
            }
            int currentHeight = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight;
            if (contentType == 1) {
                currentHeight = Math.min(botKeyboardView.getKeyboardHeight(), currentHeight);
            }
            if (botKeyboardView != null) {
                botKeyboardView.setPanelHeight(currentHeight);
            }

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) currentView.getLayoutParams();
            layoutParams.height = currentHeight;
            currentView.setLayoutParams(layoutParams);
            if (!AndroidUtilities.isInMultiwindow) {
                AndroidUtilities.hideKeyboard(messageEditText);
            }
            if (sizeNotifierLayout != null) {
                emojiPadding = currentHeight;
                sizeNotifierLayout.requestLayout();
                if (contentType == 0) {
                    emojiButton.setImageResource(R.drawable.ic_msg_panel_kb);
                } else if (contentType == 1) {
                    setEmojiButtonImage();
                }
                updateBotButton();
                onWindowSizeChanged();
            }
        } else {
            if (emojiButton != null) {
                setEmojiButtonImage();
            }
            currentPopupContentType = -1;
            if (emojiView != null) {
                emojiView.setVisibility(GONE);
            }
            if (botKeyboardView != null) {
                botKeyboardView.setVisibility(GONE);
            }
            if (sizeNotifierLayout != null) {
                if (show == 0) {
                    emojiPadding = 0;
                }
                sizeNotifierLayout.requestLayout();
                onWindowSizeChanged();
            }
            updateBotButton();
        }

        if (stickersTabOpen) {
            checkSendButton(true);
        }
        if (stickersExpanded && show != 1) {
            setStickersExpanded(false, false);
        }
    }

    private void setEmojiButtonImage() {
        int currentPage;
        if (emojiView == null) {
            currentPage = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE).getInt("selected_page", 0);
        } else {
            currentPage = emojiView.getCurrentPage();
        }
        if (currentPage == 0 || !allowStickers && !allowGifs) {
            emojiButton.setImageResource(R.drawable.ic_msg_panel_smiles);
        } else if (currentPage == 1) {
            emojiButton.setImageResource(R.drawable.ic_msg_panel_stickers);
        } else if (currentPage == 2) {
            emojiButton.setImageResource(R.drawable.ic_msg_panel_gif);
        }
    }

    public void hidePopup(boolean byBackButton) {
        if (isPopupShowing()) {
            if (currentPopupContentType == 1 && byBackButton && botButtonsMessageObject != null) {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                preferences.edit().putInt("hidekeyboard_" + dialog_id, botButtonsMessageObject.getId()).commit();
            }
            showPopup(0, 0);
            removeGifFromInputField();
        }
    }

    private void removeGifFromInputField() {
        if (!AndroidUtilities.usingHardwareInput) {
            if (messageEditText.getText().toString().equals("@gif ")) {
                messageEditText.setText("");
            }
        }
    }

    private void openKeyboardInternal() {
        showPopup(AndroidUtilities.usingHardwareInput || isPaused ? 0 : 2, 0);
        messageEditText.requestFocus();
        AndroidUtilities.showKeyboard(messageEditText);
        if (isPaused) {
            showKeyboardOnResume = true;
        } else if (!AndroidUtilities.usingHardwareInput && !keyboardVisible && !AndroidUtilities.isInMultiwindow) {
            waitingForKeyboardOpen = true;
            AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
            AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
        }
    }

    public boolean isEditingMessage() {
        return editingMessageObject != null;
    }

    public MessageObject getEditingMessageObject() {
        return editingMessageObject;
    }

    public boolean isEditingCaption() {
        return editingCaption;
    }

    public boolean hasAudioToSend() {
        return audioToSendMessageObject != null || videoToSendMessageObject != null;
    }

    public void openKeyboard() {
        AndroidUtilities.showKeyboard(messageEditText);
    }

    public void closeKeyboard() {
        AndroidUtilities.hideKeyboard(messageEditText);
    }

    public boolean isPopupShowing() {
        return emojiView != null && emojiView.getVisibility() == VISIBLE || botKeyboardView != null && botKeyboardView.getVisibility() == VISIBLE;
    }

    public boolean isKeyboardVisible() {
        return keyboardVisible;
    }

    public void addRecentGif(TLRPC.Document searchImage) {
        StickersQuery.addRecentGif(searchImage, (int) (System.currentTimeMillis() / 1000));
        if (emojiView != null) {
            emojiView.addRecentGif(searchImage);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw && stickersExpanded)
            setStickersExpanded(false, false);
       //videoTimelineView.clearFrames();
    }

    @Override
    public void onSizeChanged(int height, boolean isWidthGreater) {
        if (height > AndroidUtilities.dp(50) && keyboardVisible && !AndroidUtilities.isInMultiwindow) {
            if (isWidthGreater) {
                keyboardHeightLand = height;
                ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).edit().putInt("kbd_height_land3", keyboardHeightLand).commit();
            } else {
                keyboardHeight = height;
                ApplicationLoader.applicationContext.getSharedPreferences("emoji", 0).edit().putInt("kbd_height", keyboardHeight).commit();
            }
        }

        if (isPopupShowing()) {
            int newHeight = isWidthGreater ? keyboardHeightLand : keyboardHeight;
            if (currentPopupContentType == 1 && !botKeyboardView.isFullSize()) {
                newHeight = Math.min(botKeyboardView.getKeyboardHeight(), newHeight);
            }

            View currentView = null;
            if (currentPopupContentType == 0) {
                currentView = emojiView;
            } else if (currentPopupContentType == 1) {
                currentView = botKeyboardView;
            }
            if (botKeyboardView != null) {
                botKeyboardView.setPanelHeight(newHeight);
            }

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) currentView.getLayoutParams();
            if ((layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) && !stickersExpanded) {
                layoutParams.width = AndroidUtilities.displaySize.x;
                layoutParams.height = newHeight;
                currentView.setLayoutParams(layoutParams);
                if (sizeNotifierLayout != null) {
                    emojiPadding = layoutParams.height;
                    sizeNotifierLayout.requestLayout();
                    onWindowSizeChanged();
                }
            }
        }

        if (lastSizeChangeValue1 == height && lastSizeChangeValue2 == isWidthGreater) {
            onWindowSizeChanged();
            return;
        }
        lastSizeChangeValue1 = height;
        lastSizeChangeValue2 = isWidthGreater;

        boolean oldValue = keyboardVisible;
        keyboardVisible = height > 0;
        if (keyboardVisible && isPopupShowing()) {
            showPopup(0, currentPopupContentType);
        }
        if (emojiPadding != 0 && !keyboardVisible && keyboardVisible != oldValue && !isPopupShowing()) {
            emojiPadding = 0;
            sizeNotifierLayout.requestLayout();
        }
        if (keyboardVisible && waitingForKeyboardOpen) {
            waitingForKeyboardOpen = false;
            AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
        }
        onWindowSizeChanged();
    }

    public int getEmojiPadding() {
        return emojiPadding;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.emojiDidLoaded) {
            if (emojiView != null) {
                emojiView.invalidateViews();
            }
            if (botKeyboardView != null) {
                botKeyboardView.invalidateViews();
            }
        } else if (id == NotificationCenter.closeChats) {
            if (messageEditText != null && messageEditText.isFocused()) {
                AndroidUtilities.hideKeyboard(messageEditText);
            }
        }  else if (id == NotificationCenter.featuredStickersDidLoaded) {
            if (emojiButton != null) {
                emojiButton.invalidate();
            }
        }
    }

    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 4) {
            boolean showAlert = true;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ImageLoader.getInstance().checkMediaPaths();
            }
            if (showAlert) {
                AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));

                    builder.setMessage(LocaleController.getString("PermissionStorage", R.string.PermissionStorage));

                builder.setNegativeButton(LocaleController.getString("PermissionOpenSettings", R.string.PermissionOpenSettings), new DialogInterface.OnClickListener() {
                    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                            parentActivity.startActivity(intent);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                });
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                builder.show();
                return;
            }
        }
        else if (requestCode == 2) {
            if (pendingLocationButton != null) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    SendMessagesHelper.getInstance().sendCurrentLocation(pendingMessageObject, pendingLocationButton);
                }
                pendingLocationButton = null;
                pendingMessageObject = null;
            }
        }
    }

    private void setStickersExpanded(boolean expanded, boolean animated) {
        if (emojiView == null || expanded && !emojiView.areThereAnyStickers()) {
            return;
        }
        stickersExpanded = expanded;
        final int origHeight = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight;
        if (stickersExpansionAnim != null) {
            stickersExpansionAnim.cancel();
            stickersExpansionAnim = null;
        }
        if (stickersExpanded) {
            stickersExpandedHeight = sizeNotifierLayout.getHeight() - (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? AndroidUtilities.statusBarHeight : 0) - ActionBar.getCurrentActionBarHeight() - getHeight() + Theme.chat_composeShadowDrawable.getIntrinsicHeight();
            emojiView.getLayoutParams().height = stickersExpandedHeight;
            sizeNotifierLayout.requestLayout();
            sizeNotifierLayout.setForeground(new ScrimDrawable());
            messageEditText.setText(messageEditText.getText()); // dismiss action mode, if any
            if (animated) {
                AnimatorSet anims = new AnimatorSet();
                anims.playTogether(
                        ObjectAnimator.ofInt(this, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)),
                        ObjectAnimator.ofInt(emojiView, roundedTranslationYProperty, -(stickersExpandedHeight - origHeight)),
                        ObjectAnimator.ofFloat(stickersArrow, "animationProgress", 1)
                );
                anims.setDuration(400);
                anims.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                ((ObjectAnimator) anims.getChildAnimations().get(0)).addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        stickersExpansionProgress = getTranslationY() / (-(stickersExpandedHeight - origHeight));
                        sizeNotifierLayout.invalidate();
                    }
                });
                anims.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        stickersExpansionAnim = null;
                        emojiView.setLayerType(LAYER_TYPE_NONE, null);
                    }
                });
                stickersExpansionAnim = anims;
                emojiView.setLayerType(LAYER_TYPE_HARDWARE, null);
                anims.start();
            } else {
                stickersExpansionProgress = 1;
                setTranslationY(-(stickersExpandedHeight - origHeight));
                emojiView.setTranslationY(-(stickersExpandedHeight - origHeight));
                stickersArrow.setAnimationProgress(1);
            }
        } else {
            if (animated) {
                AnimatorSet anims = new AnimatorSet();
                anims.playTogether(
                        ObjectAnimator.ofInt(this, roundedTranslationYProperty, 0),
                        ObjectAnimator.ofInt(emojiView, roundedTranslationYProperty, 0),
                        ObjectAnimator.ofFloat(stickersArrow, "animationProgress", 0)
                );
                anims.setDuration(400);
                anims.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                ((ObjectAnimator) anims.getChildAnimations().get(0)).addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        stickersExpansionProgress = getTranslationY() / (-(stickersExpandedHeight - origHeight));
                        sizeNotifierLayout.invalidate();
                    }
                });
                anims.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        stickersExpansionAnim = null;
                        emojiView.getLayoutParams().height = origHeight;
                        sizeNotifierLayout.requestLayout();
                        emojiView.setLayerType(LAYER_TYPE_NONE, null);
                        sizeNotifierLayout.setForeground(null);
                        sizeNotifierLayout.setWillNotDraw(false);
                    }
                });
                stickersExpansionAnim = anims;
                emojiView.setLayerType(LAYER_TYPE_HARDWARE, null);
                anims.start();
            } else {
                stickersExpansionProgress = 0;
                setTranslationY(0);
                emojiView.setTranslationY(0);
                emojiView.getLayoutParams().height = origHeight;
                sizeNotifierLayout.requestLayout();
                sizeNotifierLayout.setForeground(null);
                sizeNotifierLayout.setWillNotDraw(false);
                stickersArrow.setAnimationProgress(0);
            }
        }
    }

    private class ScrimDrawable extends Drawable {

        private Paint paint;

        public ScrimDrawable() {
            paint = new Paint();
            paint.setColor(0);
        }

        @Override
        public void draw(Canvas canvas) {
            paint.setAlpha(Math.round(102 * stickersExpansionProgress));
            canvas.drawRect(0, 0, getWidth(), emojiView.getY() - getHeight() + Theme.chat_composeShadowDrawable.getIntrinsicHeight(), paint);
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return 0;
        }
    }

    private class AnimatedArrowDrawable extends Drawable {

        private Paint paint;
        private Path path = new Path();
        private float animProgress = 0;

        public AnimatedArrowDrawable(int color) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
            paint.setColor(color);

            updatePath();
        }

        @Override
        public void draw(Canvas c) {
            c.drawPath(path, paint);
        }

        private void updatePath() {
            path.reset();
            float p = animProgress * 2 - 1;
            path.moveTo(AndroidUtilities.dp(3), AndroidUtilities.dp(12) - AndroidUtilities.dp(4) * p);
            path.lineTo(AndroidUtilities.dp(13), AndroidUtilities.dp(12) + AndroidUtilities.dp(4) * p);
            path.lineTo(AndroidUtilities.dp(23), AndroidUtilities.dp(12) - AndroidUtilities.dp(4) * p);
        }

        public void setAnimationProgress(float progress) {
            animProgress = progress;
            updatePath();
            invalidateSelf();
        }

        public float getAnimationProgress() {
            return animProgress;
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return 0;
        }

        @Override
        public int getIntrinsicWidth() {
            return AndroidUtilities.dp(26);
        }

        @Override
        public int getIntrinsicHeight() {
            return AndroidUtilities.dp(26);
        }
    }
}