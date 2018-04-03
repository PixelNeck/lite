/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.TextureView;

import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.messenger.video.InputSurface;
import org.telegram.messenger.video.MP4Builder;
import org.telegram.messenger.video.Mp4Movie;
import org.telegram.messenger.video.OutputSurface;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MediaController implements  NotificationCenter.NotificationCenterDelegate {

    public interface FileDownloadProgressListener {
        void onFailedDownload(String fileName);
        void onSuccessDownload(String fileName);
        void onProgressDownload(String fileName, float progress);
        void onProgressUpload(String fileName, float progress, boolean isEncrypted);
        int getObserverTag();
    }

    private class AudioBuffer {
        public AudioBuffer(int capacity) {
            buffer = ByteBuffer.allocateDirect(capacity);
            bufferBytes = new byte[capacity];
        }

        ByteBuffer buffer;
        byte[] bufferBytes;
        int size;
        int finished;
        long pcmOffset;
    }



        public final static String MIME_TYPE = "video/avc";
        private final static int PROCESSOR_TYPE_OTHER = 0;
        private final static int PROCESSOR_TYPE_QCOM = 1;
        private final static int PROCESSOR_TYPE_INTEL = 2;
        private final static int PROCESSOR_TYPE_MTK = 3;
        private final static int PROCESSOR_TYPE_SEC = 4;
        private final static int PROCESSOR_TYPE_TI = 5;
        private final Object videoConvertSync = new Object();

        private HashMap<Long, Long> typingTimes = new HashMap<>();

        private SensorManager sensorManager;
        private boolean ignoreProximity;
        private PowerManager.WakeLock proximityWakeLock;
        private Sensor proximitySensor;
        private Sensor accelerometerSensor;
        private Sensor linearSensor;
        private Sensor gravitySensor;
        private boolean raiseToEarRecord;
        private ChatActivity raiseChat;
        private boolean accelerometerVertical;
        private int raisedToTop;
        private int raisedToBack;
        private int countLess;
        private long timeSinceRaise;
        private long lastTimestamp = 0;
        private boolean proximityTouched;
        private boolean proximityHasDifferentValues;
        private float lastProximityValue = -100;
        private boolean useFrontSpeaker;
        private boolean inputFieldHasText;
        private boolean allowStartRecord;
        private boolean ignoreOnPause;
        private boolean sensorsStarted;
        private float previousAccValue;
        private float[] gravity = new float[3];
        private float[] gravityFast = new float[3];
        private float[] linearAcceleration = new float[3];

        private int hasAudioFocus;
        private boolean callInProgress;
        private int audioFocus = AUDIO_NO_FOCUS_NO_DUCK;
        private boolean resumeAudioOnFocusGain;

        private static final float VOLUME_DUCK = 0.2f;
        private static final float VOLUME_NORMAL = 1.0f;
        private static final int AUDIO_NO_FOCUS_NO_DUCK = 0;
        private static final int AUDIO_NO_FOCUS_CAN_DUCK = 1;
        private static final int AUDIO_FOCUSED = 2;

        private ArrayList<MessageObject> videoConvertQueue = new ArrayList<>();
        private final Object videoQueueSync = new Object();
        private boolean cancelCurrentVideoConversion = false;
        private boolean videoConvertFirstWrite = true;
        private HashMap<String, MessageObject> generatingWaveform = new HashMap<>();

        private boolean voiceMessagesPlaylistUnread;
        private ArrayList<MessageObject> voiceMessagesPlaylist;
        private HashMap<Integer, MessageObject> voiceMessagesPlaylistMap;

        public static final int AUTODOWNLOAD_MASK_PHOTO = 1;
        public static final int AUTODOWNLOAD_MASK_AUDIO = 2;
        public static final int AUTODOWNLOAD_MASK_VIDEO = 4;
        public static final int AUTODOWNLOAD_MASK_DOCUMENT = 8;
        public static final int AUTODOWNLOAD_MASK_MUSIC = 16;
        public static final int AUTODOWNLOAD_MASK_GIF = 32;
        public static final int AUTODOWNLOAD_MASK_VIDEOMESSAGE = 64;
        public boolean globalAutodownloadEnabled;
        public int mobileDataDownloadMask[] = new int[4];
        public int wifiDownloadMask[] = new int[4];
        public int roamingDownloadMask[] = new int[4];
        public int mobileMaxFileSize[] = new int[7];
        public int wifiMaxFileSize[] = new int[7];
        public int roamingMaxFileSize[] = new int[7];
        private int lastCheckMask = 0;
        private ArrayList<DownloadObject> photoDownloadQueue = new ArrayList<>();
        private ArrayList<DownloadObject> audioDownloadQueue = new ArrayList<>();
        private ArrayList<DownloadObject> videoMessageDownloadQueue = new ArrayList<>();
        private ArrayList<DownloadObject> documentDownloadQueue = new ArrayList<>();
        private ArrayList<DownloadObject> musicDownloadQueue = new ArrayList<>();
        private ArrayList<DownloadObject> gifDownloadQueue = new ArrayList<>();
        private ArrayList<DownloadObject> videoDownloadQueue = new ArrayList<>();
        private HashMap<String, DownloadObject> downloadQueueKeys = new HashMap<>();

        private boolean saveToGallery = true;
        private boolean autoplayGifs = true;
        private boolean raiseToSpeak = true;
        private boolean customTabs = true;
        private boolean directShare = true;
        private boolean inappCamera = true;
        private boolean roundCamera16to9 = true;
        private boolean groupPhotosEnabled = true;
        private boolean shuffleMusic;
        private boolean playOrderReversed;
        private int repeatMode;


        private HashMap<String, ArrayList<WeakReference<FileDownloadProgressListener>>> loadingFileObservers = new HashMap<>();
        private HashMap<String, ArrayList<MessageObject>> loadingFileMessagesObservers = new HashMap<>();
        private HashMap<Integer, String> observersByTag = new HashMap<>();
        private boolean listenerInProgress = false;
        private HashMap<String, FileDownloadProgressListener> addLaterArray = new HashMap<>();
        private ArrayList<FileDownloadProgressListener> deleteLaterArray = new ArrayList<>();
        private int lastTag = 0;

        private boolean isPaused = false;
        private MediaPlayer audioPlayer = null;
        private AudioTrack audioTrackPlayer = null;
        private long lastProgress = 0;
        private MessageObject playingMessageObject;
        private int playerBufferSize = 0;
        private boolean decodingFinished = false;
        private long currentTotalPcmDuration;
        private long lastPlayPcm;
        private int ignoreFirstProgress = 0;
        private Timer progressTimer = null;
        private final Object progressTimerSync = new Object();
        private int buffersWrited;
        private ArrayList<MessageObject> playlist = new ArrayList<>();
        private ArrayList<MessageObject> shuffledPlaylist = new ArrayList<>();
        private int currentPlaylistNum;
        private boolean forceLoopCurrentPlaylist;

        private AudioInfo audioInfo;

        private TextureView currentTextureView;

        private int pipSwitchingState;
        private Activity baseActivity;

        private boolean isDrawingWasReady;

        private AudioRecord audioRecorder;
        private TLRPC.TL_document recordingAudio;
        private File recordingAudioFile;
        private long recordStartTime;
        private long recordTimeCount;
        private long recordDialogId;
        private MessageObject recordReplyingMessageObject;
        private DispatchQueue fileDecodingQueue;
        private DispatchQueue playerQueue;
        private ArrayList<AudioBuffer> usedPlayerBuffers = new ArrayList<>();
        private ArrayList<AudioBuffer> freePlayerBuffers = new ArrayList<>();
        private final Object playerSync = new Object();
        private final Object playerObjectSync = new Object();
        private short[] recordSamples = new short[1024];
        private long samplesCount;

        private final Object sync = new Object();

        private ArrayList<ByteBuffer> recordBuffers = new ArrayList<>();
        private ByteBuffer fileBuffer;
        private int recordBufferSize;

        private DispatchQueue fileEncodingQueue;


        private class SmsObserver extends ContentObserver {
            public SmsObserver() {
                super(null);
            }

            @Override
            public void onChange(boolean selfChange) {
                readSms();
            }
        }

        private void readSms() {
        Cursor cursor = null;
        try {
            cursor = ApplicationLoader.applicationContext.getContentResolver().query(Uri.parse("content://sms/sent"), null, null, null, null);
            while (cursor.moveToNext()) {
                String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                long data = cursor.getLong(cursor.getColumnIndexOrThrow("date"));
                String smsBody = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                FileLog.d(address + " body = " + smsBody);
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        }

        private class InternalObserver extends ContentObserver {
            public InternalObserver() {
                super(null);
            }

            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                processMediaObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI);
            }
        }

        private class ExternalObserver extends ContentObserver {
            public ExternalObserver() {
                super(null);
            }

            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                processMediaObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            }
        }


        public static int maskToIndex(int mask) {
            if (mask == AUTODOWNLOAD_MASK_PHOTO) {
                return 0;
            } else if (mask == AUTODOWNLOAD_MASK_AUDIO) {
                return 1;
            } else if (mask == AUTODOWNLOAD_MASK_VIDEO) {
                return 2;
            } else if (mask == AUTODOWNLOAD_MASK_DOCUMENT) {
                return 3;
            } else if (mask == AUTODOWNLOAD_MASK_MUSIC) {
                return 4;
            } else if (mask == AUTODOWNLOAD_MASK_GIF) {
                return 5;
            } else if (mask == AUTODOWNLOAD_MASK_VIDEOMESSAGE) {
                return 6;
            }
            return 0;
        }


        private ExternalObserver externalObserver;
        private InternalObserver internalObserver;
        private SmsObserver smsObserver;
        private long lastChatEnterTime;
        private long lastChatLeaveTime;
        private long lastMediaCheckTime;
        private TLRPC.EncryptedChat lastSecretChat;
        private TLRPC.User lastUser;
        private int lastMessageId;
        private ArrayList<Long> lastChatVisibleMessages;
        private int startObserverToken;
        private StopMediaObserverRunnable stopMediaObserverRunnable;

        private final class StopMediaObserverRunnable implements Runnable {
            public int currentObserverToken = 0;

            @Override
            public void run() {
                if (currentObserverToken == startObserverToken) {
                    try {
                        if (internalObserver != null) {
                            ApplicationLoader.applicationContext.getContentResolver().unregisterContentObserver(internalObserver);
                            internalObserver = null;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    try {
                        if (externalObserver != null) {
                            ApplicationLoader.applicationContext.getContentResolver().unregisterContentObserver(externalObserver);
                            externalObserver = null;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        }

        private String[] mediaProjections = null;

        private static volatile MediaController Instance = null;

        public static MediaController getInstance() {
            MediaController localInstance = Instance;
            if (localInstance == null) {
                synchronized (MediaController.class) {
                    localInstance = Instance;
                    if (localInstance == null) {
                        Instance = localInstance = new MediaController();
                    }
                }
            }
            return localInstance;
        }

        public MediaController() {

            fileBuffer = ByteBuffer.allocateDirect(1920);

            fileEncodingQueue = new DispatchQueue("fileEncodingQueue");
            fileEncodingQueue.setPriority(Thread.MAX_PRIORITY);
            playerQueue = new DispatchQueue("playerQueue");
            fileDecodingQueue = new DispatchQueue("fileDecodingQueue");

            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            for (int a = 0; a < 4; a++) {
                String key = "mobileDataDownloadMask" + (a == 0 ? "" : a);
                if (a == 0 || preferences.contains(key)) {
                    mobileDataDownloadMask[a] = preferences.getInt(key, AUTODOWNLOAD_MASK_PHOTO | AUTODOWNLOAD_MASK_AUDIO | AUTODOWNLOAD_MASK_MUSIC | AUTODOWNLOAD_MASK_GIF | AUTODOWNLOAD_MASK_VIDEOMESSAGE);
                    wifiDownloadMask[a] = preferences.getInt("wifiDownloadMask" + (a == 0 ? "" : a), AUTODOWNLOAD_MASK_PHOTO | AUTODOWNLOAD_MASK_AUDIO | AUTODOWNLOAD_MASK_MUSIC | AUTODOWNLOAD_MASK_GIF | AUTODOWNLOAD_MASK_VIDEOMESSAGE);
                    roamingDownloadMask[a] = preferences.getInt("roamingDownloadMask" + (a == 0 ? "" : a), 0);
                } else {
                    mobileDataDownloadMask[a] = mobileDataDownloadMask[0];
                    wifiDownloadMask[a] = wifiDownloadMask[0];
                    roamingDownloadMask[a] = roamingDownloadMask[0];
                }
            }
            for (int a = 0; a < 7; a++) {
                int sdefault;
                if (a == 1) {
                    sdefault = 2 * 1024 * 1024;
                } else if (a == 6) {
                    sdefault = 5 * 1024 * 1024;
                } else {
                    sdefault = 10 * 1024 * 1024;
                }
                mobileMaxFileSize[a] = preferences.getInt("mobileMaxDownloadSize" + a, sdefault);
                wifiMaxFileSize[a] = preferences.getInt("wifiMaxDownloadSize" + a, sdefault);
                roamingMaxFileSize[a] = preferences.getInt("roamingMaxDownloadSize" + a, sdefault);
            }
            globalAutodownloadEnabled = preferences.getBoolean("globalAutodownloadEnabled", true);
            saveToGallery = preferences.getBoolean("save_gallery", false);
            autoplayGifs = preferences.getBoolean("autoplay_gif", true);
            raiseToSpeak = preferences.getBoolean("raise_to_speak", true);
            customTabs = preferences.getBoolean("custom_tabs", true);
            directShare = preferences.getBoolean("direct_share", true);
            shuffleMusic = preferences.getBoolean("shuffleMusic", false);
            playOrderReversed = preferences.getBoolean("playOrderReversed", false);
            inappCamera = preferences.getBoolean("inappCamera", true);
            roundCamera16to9 = preferences.getBoolean("roundCamera16to9", true);
            groupPhotosEnabled = preferences.getBoolean("groupPhotosEnabled", true);
            repeatMode = preferences.getInt("repeatMode", 0);

            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.FileDidFailedLoad);
                    NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.didReceivedNewMessages);
                    NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.messagesDeleted);
                    NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.FileDidLoaded);
                    NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.FileLoadProgressChanged);
                    NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.FileUploadProgressChanged);
                    NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.removeAllMessagesFromDialog);
                    //NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.musicDidLoaded);
                    NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.httpFileDidLoaded);
                    NotificationCenter.getInstance().addObserver(MediaController.this, NotificationCenter.httpFileDidFailedLoad);
                }
            });

            BroadcastReceiver networkStateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    checkAutodownloadSettings();
                }
            };
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            ApplicationLoader.applicationContext.registerReceiver(networkStateReceiver, filter);

            if (UserConfig.isClientActivated()) {
                checkAutodownloadSettings();
            }

            mediaProjections = new String[]{
                    MediaStore.Images.ImageColumns.DATA,
                    MediaStore.Images.ImageColumns.DISPLAY_NAME,
                    MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.ImageColumns.DATE_TAKEN,
                    MediaStore.Images.ImageColumns.TITLE,
                    MediaStore.Images.ImageColumns.WIDTH,
                    MediaStore.Images.ImageColumns.HEIGHT
            };


        }


        public void cleanup() {
            //cleanupPlayer(false, true);
            audioInfo = null;

            photoDownloadQueue.clear();
            audioDownloadQueue.clear();
            videoMessageDownloadQueue.clear();
            documentDownloadQueue.clear();
            videoDownloadQueue.clear();
            musicDownloadQueue.clear();
            gifDownloadQueue.clear();
            downloadQueueKeys.clear();
            videoConvertQueue.clear();
            playlist.clear();
            shuffledPlaylist.clear();
            generatingWaveform.clear();
            typingTimes.clear();
            voiceMessagesPlaylist = null;
            voiceMessagesPlaylistMap = null;
            cancelVideoConvert(null);
        }

        protected int getAutodownloadMask() {
            if (!globalAutodownloadEnabled) {
                return 0;
            }
            int result = 0;
            int masksArray[];
            if (ConnectionsManager.isConnectedToWiFi()) {
                masksArray = wifiDownloadMask;
            } else if (ConnectionsManager.isRoaming()) {
                masksArray = roamingDownloadMask;
            } else {
                masksArray = mobileDataDownloadMask;
            }
            for (int a = 0; a < 4; a++) {
                int mask = 0;
                if ((masksArray[a] & AUTODOWNLOAD_MASK_PHOTO) != 0) {
                    mask |= AUTODOWNLOAD_MASK_PHOTO;
                }
                if ((masksArray[a] & AUTODOWNLOAD_MASK_AUDIO) != 0) {
                    mask |= AUTODOWNLOAD_MASK_AUDIO;
                }
                if ((masksArray[a] & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0) {
                    mask |= AUTODOWNLOAD_MASK_VIDEOMESSAGE;
                }
                if ((masksArray[a] & AUTODOWNLOAD_MASK_VIDEO) != 0) {
                    mask |= AUTODOWNLOAD_MASK_VIDEO;
                }
                if ((masksArray[a] & AUTODOWNLOAD_MASK_DOCUMENT) != 0) {
                    mask |= AUTODOWNLOAD_MASK_DOCUMENT;
                }
                if ((masksArray[a] & AUTODOWNLOAD_MASK_MUSIC) != 0) {
                    mask |= AUTODOWNLOAD_MASK_MUSIC;
                }
                if ((masksArray[a] & AUTODOWNLOAD_MASK_GIF) != 0) {
                    mask |= AUTODOWNLOAD_MASK_GIF;
                }
                result |= mask << (a * 8);
            }
            return result;
        }

        protected int getAutodownloadMaskAll() {
            if (!globalAutodownloadEnabled) {
                return 0;
            }
            int mask = 0;
            for (int a = 0; a < 4; a++) {
                if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_PHOTO) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_PHOTO) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_PHOTO) != 0) {
                    mask |= AUTODOWNLOAD_MASK_PHOTO;
                }
                if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_AUDIO) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_AUDIO) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_AUDIO) != 0) {
                    mask |= AUTODOWNLOAD_MASK_AUDIO;
                }
                if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0) {
                    mask |= AUTODOWNLOAD_MASK_VIDEOMESSAGE;
                }
                if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEO) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEO) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_VIDEO) != 0) {
                    mask |= AUTODOWNLOAD_MASK_VIDEO;
                }
                if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_DOCUMENT) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_DOCUMENT) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_DOCUMENT) != 0) {
                    mask |= AUTODOWNLOAD_MASK_DOCUMENT;
                }
                if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_MUSIC) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_MUSIC) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_MUSIC) != 0) {
                    mask |= AUTODOWNLOAD_MASK_MUSIC;
                }
                if ((mobileDataDownloadMask[a] & AUTODOWNLOAD_MASK_GIF) != 0 || (wifiDownloadMask[a] & AUTODOWNLOAD_MASK_GIF) != 0 || (roamingDownloadMask[a] & AUTODOWNLOAD_MASK_GIF) != 0) {
                    mask |= AUTODOWNLOAD_MASK_GIF;
                }
            }
            return mask;
        }

        public void checkAutodownloadSettings() {
            int currentMask = getCurrentDownloadMask();
            if (currentMask == lastCheckMask) {
                return;
            }
            lastCheckMask = currentMask;
            if ((currentMask & AUTODOWNLOAD_MASK_PHOTO) != 0) {
                if (photoDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_PHOTO);
                }
            } else {
                for (int a = 0; a < photoDownloadQueue.size(); a++) {
                    DownloadObject downloadObject = photoDownloadQueue.get(a);
                    FileLoader.getInstance().cancelLoadFile((TLRPC.PhotoSize) downloadObject.object);
                }
                photoDownloadQueue.clear();
            }
            if ((currentMask & AUTODOWNLOAD_MASK_AUDIO) != 0) {
                if (audioDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_AUDIO);
                }
            } else {
                for (int a = 0; a < audioDownloadQueue.size(); a++) {
                    DownloadObject downloadObject = audioDownloadQueue.get(a);
                    FileLoader.getInstance().cancelLoadFile((TLRPC.Document) downloadObject.object);
                }
                audioDownloadQueue.clear();
            }
            if ((currentMask & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0) {
                if (videoMessageDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_VIDEOMESSAGE);
                }
            } else {
                for (int a = 0; a < videoMessageDownloadQueue.size(); a++) {
                    DownloadObject downloadObject = videoMessageDownloadQueue.get(a);
                    FileLoader.getInstance().cancelLoadFile((TLRPC.Document) downloadObject.object);
                }
                videoMessageDownloadQueue.clear();
            }
            if ((currentMask & AUTODOWNLOAD_MASK_DOCUMENT) != 0) {
                if (documentDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_DOCUMENT);
                }
            } else {
                for (int a = 0; a < documentDownloadQueue.size(); a++) {
                    DownloadObject downloadObject = documentDownloadQueue.get(a);
                    TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                    FileLoader.getInstance().cancelLoadFile(document);
                }
                documentDownloadQueue.clear();
            }
            if ((currentMask & AUTODOWNLOAD_MASK_VIDEO) != 0) {
                if (videoDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_VIDEO);
                }
            } else {
                for (int a = 0; a < videoDownloadQueue.size(); a++) {
                    DownloadObject downloadObject = videoDownloadQueue.get(a);
                    FileLoader.getInstance().cancelLoadFile((TLRPC.Document) downloadObject.object);
                }
                videoDownloadQueue.clear();
            }
            if ((currentMask & AUTODOWNLOAD_MASK_MUSIC) != 0) {
                if (musicDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_MUSIC);
                }
            } else {
                for (int a = 0; a < musicDownloadQueue.size(); a++) {
                    DownloadObject downloadObject = musicDownloadQueue.get(a);
                    TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                    FileLoader.getInstance().cancelLoadFile(document);
                }
                musicDownloadQueue.clear();
            }
            if ((currentMask & AUTODOWNLOAD_MASK_GIF) != 0) {
                if (gifDownloadQueue.isEmpty()) {
                    newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_GIF);
                }
            } else {
                for (int a = 0; a < gifDownloadQueue.size(); a++) {
                    DownloadObject downloadObject = gifDownloadQueue.get(a);
                    TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                    FileLoader.getInstance().cancelLoadFile(document);
                }
                gifDownloadQueue.clear();
            }

            int mask = getAutodownloadMaskAll();
            if (mask == 0) {
                MessagesStorage.getInstance().clearDownloadQueue(0);
            } else {
                if ((mask & AUTODOWNLOAD_MASK_PHOTO) == 0) {
                    MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_PHOTO);
                }
                if ((mask & AUTODOWNLOAD_MASK_AUDIO) == 0) {
                    MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_AUDIO);
                }
                if ((mask & AUTODOWNLOAD_MASK_VIDEOMESSAGE) == 0) {
                    MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_VIDEOMESSAGE);
                }
                if ((mask & AUTODOWNLOAD_MASK_VIDEO) == 0) {
                    MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_VIDEO);
                }
                if ((mask & AUTODOWNLOAD_MASK_DOCUMENT) == 0) {
                    MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_DOCUMENT);
                }
                if ((mask & AUTODOWNLOAD_MASK_MUSIC) == 0) {
                    MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_MUSIC);
                }
                if ((mask & AUTODOWNLOAD_MASK_GIF) == 0) {
                    MessagesStorage.getInstance().clearDownloadQueue(AUTODOWNLOAD_MASK_GIF);
                }
            }
        }

        public boolean canDownloadMedia(MessageObject messageObject) {
            return canDownloadMedia(messageObject.messageOwner);
        }

        public boolean canDownloadMedia(TLRPC.Message message) {
            if (!globalAutodownloadEnabled) {
                return false;
            }
            int type;
            if (MessageObject.isPhoto(message)) {
                type = MediaController.AUTODOWNLOAD_MASK_PHOTO;
            } else if (MessageObject.isVoiceMessage(message)) {
                type = MediaController.AUTODOWNLOAD_MASK_AUDIO;
            } else if (MessageObject.isRoundVideoMessage(message)) {
                type = MediaController.AUTODOWNLOAD_MASK_VIDEOMESSAGE;
            } else if (MessageObject.isVideoMessage(message)) {
                type = MediaController.AUTODOWNLOAD_MASK_VIDEO;
            } else if (MessageObject.isMusicMessage(message)) {
                type = MediaController.AUTODOWNLOAD_MASK_MUSIC;
            } else if (MessageObject.isGifMessage(message)) {
                type = MediaController.AUTODOWNLOAD_MASK_GIF;
            } else {
                type = MediaController.AUTODOWNLOAD_MASK_DOCUMENT;
            }
            int mask;
            int index;
            int maxSize;
            TLRPC.Peer peer = message.to_id;
            if (peer != null) {
                if (peer.user_id != 0) {
                    if (ContactsController.getInstance().contactsDict.containsKey(peer.user_id)) {
                        index = 0;
                    } else {
                        index = 1;
                    }
                } else if (peer.chat_id != 0) {
                    index = 2;
                } else {
                    if (MessageObject.isMegagroup(message)) {
                        index = 2;
                    } else {
                        index = 3;
                    }
                }
            } else {
                index = 1;
            }
            if (ConnectionsManager.isConnectedToWiFi()) {
                mask = wifiDownloadMask[index];
                maxSize = wifiMaxFileSize[maskToIndex(type)];
            } else if (ConnectionsManager.isRoaming()) {
                mask = roamingDownloadMask[index];
                maxSize = roamingMaxFileSize[maskToIndex(type)];
            } else {
                mask = mobileDataDownloadMask[index];
                maxSize = mobileMaxFileSize[maskToIndex(type)];
            }
            return (type == MediaController.AUTODOWNLOAD_MASK_PHOTO || MessageObject.getMessageSize(message) <= maxSize) && (mask & type) != 0;
        }

        private int getCurrentDownloadMask() {
            if (!globalAutodownloadEnabled) {
                return 0;
            }
            if (ConnectionsManager.isConnectedToWiFi()) {
                int mask = 0;
                for (int a = 0; a < 4; a++) {
                    mask |= wifiDownloadMask[a];
                }
                return mask;
            } else if (ConnectionsManager.isRoaming()) {
                int mask = 0;
                for (int a = 0; a < 4; a++) {
                    mask |= roamingDownloadMask[a];
                }
                return mask;
            } else {
                int mask = 0;
                for (int a = 0; a < 4; a++) {
                    mask |= mobileDataDownloadMask[a];
                }
                return mask;
            }
        }

        protected void processDownloadObjects(int type, ArrayList<DownloadObject> objects) {
            if (objects.isEmpty()) {
                return;
            }
            ArrayList<DownloadObject> queue = null;
            if (type == AUTODOWNLOAD_MASK_PHOTO) {
                queue = photoDownloadQueue;
            } else if (type == AUTODOWNLOAD_MASK_AUDIO) {
                queue = audioDownloadQueue;
            } else if (type == AUTODOWNLOAD_MASK_VIDEOMESSAGE) {
                queue = videoMessageDownloadQueue;
            } else if (type == AUTODOWNLOAD_MASK_VIDEO) {
                queue = videoDownloadQueue;
            } else if (type == AUTODOWNLOAD_MASK_DOCUMENT) {
                queue = documentDownloadQueue;
            } else if (type == AUTODOWNLOAD_MASK_MUSIC) {
                queue = musicDownloadQueue;
            } else if (type == AUTODOWNLOAD_MASK_GIF) {
                queue = gifDownloadQueue;
            }
            for (int a = 0; a < objects.size(); a++) {
                DownloadObject downloadObject = objects.get(a);
                String path;
                if (downloadObject.object instanceof TLRPC.Document) {
                    TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                    path = FileLoader.getAttachFileName(document);
                } else {
                    path = FileLoader.getAttachFileName(downloadObject.object);
                }
                if (downloadQueueKeys.containsKey(path)) {
                    continue;
                }

                boolean added = true;
                if (downloadObject.object instanceof TLRPC.PhotoSize) {
                    FileLoader.getInstance().loadFile((TLRPC.PhotoSize) downloadObject.object, null, downloadObject.secret ? 2 : 0);
                } else if (downloadObject.object instanceof TLRPC.Document) {
                    TLRPC.Document document = (TLRPC.Document) downloadObject.object;
                    FileLoader.getInstance().loadFile(document, false, downloadObject.secret ? 2 : 0);
                } else {
                    added = false;
                }
                if (added) {
                    queue.add(downloadObject);
                    downloadQueueKeys.put(path, downloadObject);
                }
            }
        }

        protected void newDownloadObjectsAvailable(int downloadMask) {
            int mask = getCurrentDownloadMask();
            if ((mask & AUTODOWNLOAD_MASK_PHOTO) != 0 && (downloadMask & AUTODOWNLOAD_MASK_PHOTO) != 0 && photoDownloadQueue.isEmpty()) {
                MessagesStorage.getInstance().getDownloadQueue(AUTODOWNLOAD_MASK_PHOTO);
            }
            if ((mask & AUTODOWNLOAD_MASK_AUDIO) != 0 && (downloadMask & AUTODOWNLOAD_MASK_AUDIO) != 0 && audioDownloadQueue.isEmpty()) {
                MessagesStorage.getInstance().getDownloadQueue(AUTODOWNLOAD_MASK_AUDIO);
            }
            if ((mask & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0 && (downloadMask & AUTODOWNLOAD_MASK_VIDEOMESSAGE) != 0 && videoMessageDownloadQueue.isEmpty()) {
                MessagesStorage.getInstance().getDownloadQueue(AUTODOWNLOAD_MASK_VIDEOMESSAGE);
            }
            if ((mask & AUTODOWNLOAD_MASK_VIDEO) != 0 && (downloadMask & AUTODOWNLOAD_MASK_VIDEO) != 0 && videoDownloadQueue.isEmpty()) {
                MessagesStorage.getInstance().getDownloadQueue(AUTODOWNLOAD_MASK_VIDEO);
            }
            if ((mask & AUTODOWNLOAD_MASK_DOCUMENT) != 0 && (downloadMask & AUTODOWNLOAD_MASK_DOCUMENT) != 0 && documentDownloadQueue.isEmpty()) {
                MessagesStorage.getInstance().getDownloadQueue(AUTODOWNLOAD_MASK_DOCUMENT);
            }
            if ((mask & AUTODOWNLOAD_MASK_MUSIC) != 0 && (downloadMask & AUTODOWNLOAD_MASK_MUSIC) != 0 && musicDownloadQueue.isEmpty()) {
                MessagesStorage.getInstance().getDownloadQueue(AUTODOWNLOAD_MASK_MUSIC);
            }
            if ((mask & AUTODOWNLOAD_MASK_GIF) != 0 && (downloadMask & AUTODOWNLOAD_MASK_GIF) != 0 && gifDownloadQueue.isEmpty()) {
                MessagesStorage.getInstance().getDownloadQueue(AUTODOWNLOAD_MASK_GIF);
            }
        }

        private void checkDownloadFinished(String fileName, int state) {
            DownloadObject downloadObject = downloadQueueKeys.get(fileName);
            if (downloadObject != null) {
                downloadQueueKeys.remove(fileName);
                if (state == 0 || state == 2) {
                    MessagesStorage.getInstance().removeFromDownloadQueue(downloadObject.id, downloadObject.type, false /*state != 0*/);
                }
                if (downloadObject.type == AUTODOWNLOAD_MASK_PHOTO) {
                    photoDownloadQueue.remove(downloadObject);
                    if (photoDownloadQueue.isEmpty()) {
                        newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_PHOTO);
                    }
                } else if (downloadObject.type == AUTODOWNLOAD_MASK_AUDIO) {
                    audioDownloadQueue.remove(downloadObject);
                    if (audioDownloadQueue.isEmpty()) {
                        newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_AUDIO);
                    }
                } else if (downloadObject.type == AUTODOWNLOAD_MASK_VIDEOMESSAGE) {
                    videoMessageDownloadQueue.remove(downloadObject);
                    if (videoMessageDownloadQueue.isEmpty()) {
                        newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_VIDEOMESSAGE);
                    }
                } else if (downloadObject.type == AUTODOWNLOAD_MASK_VIDEO) {
                    videoDownloadQueue.remove(downloadObject);
                    if (videoDownloadQueue.isEmpty()) {
                        newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_VIDEO);
                    }
                } else if (downloadObject.type == AUTODOWNLOAD_MASK_DOCUMENT) {
                    documentDownloadQueue.remove(downloadObject);
                    if (documentDownloadQueue.isEmpty()) {
                        newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_DOCUMENT);
                    }
                } else if (downloadObject.type == AUTODOWNLOAD_MASK_MUSIC) {
                    musicDownloadQueue.remove(downloadObject);
                    if (musicDownloadQueue.isEmpty()) {
                        newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_MUSIC);
                    }
                } else if (downloadObject.type == AUTODOWNLOAD_MASK_GIF) {
                    gifDownloadQueue.remove(downloadObject);
                    if (gifDownloadQueue.isEmpty()) {
                        newDownloadObjectsAvailable(AUTODOWNLOAD_MASK_GIF);
                    }
                }
            }
        }

        public void startMediaObserver() {
            ApplicationLoader.applicationHandler.removeCallbacks(stopMediaObserverRunnable);
            startObserverToken++;
            try {
                if (internalObserver == null) {
                    ApplicationLoader.applicationContext.getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, externalObserver = new ExternalObserver());
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                if (externalObserver == null) {
                    ApplicationLoader.applicationContext.getContentResolver().registerContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI, false, internalObserver = new InternalObserver());
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        public void startSmsObserver() {
            try {
                if (smsObserver == null) {
                    ApplicationLoader.applicationContext.getContentResolver().registerContentObserver(Uri.parse("content://sms"), false, smsObserver = new SmsObserver());
                }
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (smsObserver != null) {
                                ApplicationLoader.applicationContext.getContentResolver().unregisterContentObserver(smsObserver);
                                smsObserver = null;
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }, 5 * 60 * 1000);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        public void stopMediaObserver() {
            if (stopMediaObserverRunnable == null) {
                stopMediaObserverRunnable = new StopMediaObserverRunnable();
            }
            stopMediaObserverRunnable.currentObserverToken = startObserverToken;
            ApplicationLoader.applicationHandler.postDelayed(stopMediaObserverRunnable, 5000);
        }

        private void processMediaObserver(Uri uri) {
            try {
                Point size = AndroidUtilities.getRealScreenSize();

                Cursor cursor = ApplicationLoader.applicationContext.getContentResolver().query(uri, mediaProjections, null, null, "date_added DESC LIMIT 1");
                final ArrayList<Long> screenshotDates = new ArrayList<>();
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String val = "";
                        String data = cursor.getString(0);
                        String display_name = cursor.getString(1);
                        String album_name = cursor.getString(2);
                        long date = cursor.getLong(3);
                        String title = cursor.getString(4);
                        int photoW = cursor.getInt(5);
                        int photoH = cursor.getInt(6);
                        if (data != null && data.toLowerCase().contains("screenshot") ||
                                display_name != null && display_name.toLowerCase().contains("screenshot") ||
                                album_name != null && album_name.toLowerCase().contains("screenshot") ||
                                title != null && title.toLowerCase().contains("screenshot")) {
                            try {
                                if (photoW == 0 || photoH == 0) {
                                    BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                                    bmOptions.inJustDecodeBounds = true;
                                    BitmapFactory.decodeFile(data, bmOptions);
                                    photoW = bmOptions.outWidth;
                                    photoH = bmOptions.outHeight;
                                }
                                if (photoW <= 0 || photoH <= 0 || (photoW == size.x && photoH == size.y || photoH == size.x && photoW == size.y)) {
                                    screenshotDates.add(date);
                                }
                            } catch (Exception e) {
                                screenshotDates.add(date);
                            }
                        }
                    }
                    cursor.close();
                }
                if (!screenshotDates.isEmpty()) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.screenshotTook);
                            checkScreenshots(screenshotDates);
                        }
                    });
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        private void checkScreenshots(ArrayList<Long> dates) {
            if (dates == null || dates.isEmpty() || lastChatEnterTime == 0 || (lastUser == null && !(lastSecretChat instanceof TLRPC.TL_encryptedChat))) {
                return;
            }
            long dt = 2000;
            boolean send = false;
            for (int a = 0; a < dates.size(); a++) {
                Long date = dates.get(a);
                if (lastMediaCheckTime != 0 && date <= lastMediaCheckTime) {
                    continue;
                }

                if (date >= lastChatEnterTime) {
                    if (lastChatLeaveTime == 0 || date <= lastChatLeaveTime + dt) {
                        lastMediaCheckTime = Math.max(lastMediaCheckTime, date);
                        send = true;
                    }
                }
            }
            if (send) {
                if (lastSecretChat != null) {
                    SecretChatHelper.getInstance().sendScreenshotMessage(lastSecretChat, lastChatVisibleMessages, null);
                } else {
                    SendMessagesHelper.getInstance().sendScreenshotMessage(lastUser, lastMessageId, null);
                }
            }
        }

        public void setLastVisibleMessageIds(long enterTime, long leaveTime, TLRPC.User user, TLRPC.EncryptedChat encryptedChat, ArrayList<Long> visibleMessages, int visibleMessage) {
            lastChatEnterTime = enterTime;
            lastChatLeaveTime = leaveTime;
            lastSecretChat = encryptedChat;
            lastUser = user;
            lastMessageId = visibleMessage;
            lastChatVisibleMessages = visibleMessages;
        }

        public int generateObserverTag() {
            return lastTag++;
        }

        public void addLoadingFileObserver(String fileName, FileDownloadProgressListener observer) {
            addLoadingFileObserver(fileName, null, observer);
        }

        public void addLoadingFileObserver(String fileName, MessageObject messageObject, FileDownloadProgressListener observer) {
            if (listenerInProgress) {
                addLaterArray.put(fileName, observer);
                return;
            }
            removeLoadingFileObserver(observer);

            ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
            if (arrayList == null) {
                arrayList = new ArrayList<>();
                loadingFileObservers.put(fileName, arrayList);
            }
            arrayList.add(new WeakReference<>(observer));
            if (messageObject != null) {
                ArrayList<MessageObject> messageObjects = loadingFileMessagesObservers.get(fileName);
                if (messageObjects == null) {
                    messageObjects = new ArrayList<>();
                    loadingFileMessagesObservers.put(fileName, messageObjects);
                }
                messageObjects.add(messageObject);
            }

            observersByTag.put(observer.getObserverTag(), fileName);
        }

        public void removeLoadingFileObserver(FileDownloadProgressListener observer) {
            if (listenerInProgress) {
                deleteLaterArray.add(observer);
                return;
            }
            String fileName = observersByTag.get(observer.getObserverTag());
            if (fileName != null) {
                ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
                if (arrayList != null) {
                    for (int a = 0; a < arrayList.size(); a++) {
                        WeakReference<FileDownloadProgressListener> reference = arrayList.get(a);
                        if (reference.get() == null || reference.get() == observer) {
                            arrayList.remove(a);
                            a--;
                        }
                    }
                    if (arrayList.isEmpty()) {
                        loadingFileObservers.remove(fileName);
                    }
                }
                observersByTag.remove(observer.getObserverTag());
            }
        }

        private void processLaterArrays() {
            for (HashMap.Entry<String, FileDownloadProgressListener> listener : addLaterArray.entrySet()) {
                addLoadingFileObserver(listener.getKey(), listener.getValue());
            }
            addLaterArray.clear();
            for (FileDownloadProgressListener listener : deleteLaterArray) {
                removeLoadingFileObserver(listener);
            }
            deleteLaterArray.clear();
        }

        @SuppressWarnings("unchecked")
        @Override
        public void didReceivedNotification(int id, Object... args) {
            if (id == NotificationCenter.FileDidFailedLoad || id == NotificationCenter.httpFileDidFailedLoad) {
                listenerInProgress = true;
                String fileName = (String) args[0];
                ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
                if (arrayList != null) {
                    for (int a = 0; a < arrayList.size(); a++) {
                        WeakReference<FileDownloadProgressListener> reference = arrayList.get(a);
                        if (reference.get() != null) {
                            reference.get().onFailedDownload(fileName);
                            observersByTag.remove(reference.get().getObserverTag());
                        }
                    }
                    loadingFileObservers.remove(fileName);
                }
                listenerInProgress = false;
                processLaterArrays();
                checkDownloadFinished(fileName, (Integer) args[1]);
            } else if (id == NotificationCenter.FileDidLoaded || id == NotificationCenter.httpFileDidLoaded) {
                listenerInProgress = true;
                String fileName = (String) args[0];

                ArrayList<MessageObject> messageObjects = loadingFileMessagesObservers.get(fileName);
                if (messageObjects != null) {
                    for (int a = 0; a < messageObjects.size(); a++) {
                        MessageObject messageObject = messageObjects.get(a);
                        messageObject.mediaExists = true;
                    }
                    loadingFileMessagesObservers.remove(fileName);
                }
                ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
                if (arrayList != null) {
                    for (int a = 0; a < arrayList.size(); a++) {
                        WeakReference<FileDownloadProgressListener> reference = arrayList.get(a);
                        if (reference.get() != null) {
                            reference.get().onSuccessDownload(fileName);
                            observersByTag.remove(reference.get().getObserverTag());
                        }
                    }
                    loadingFileObservers.remove(fileName);
                }
                listenerInProgress = false;
                processLaterArrays();
                checkDownloadFinished(fileName, 0);
            } else if (id == NotificationCenter.FileLoadProgressChanged) {
                listenerInProgress = true;
                String fileName = (String) args[0];
                ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
                if (arrayList != null) {
                    Float progress = (Float) args[1];
                    for (WeakReference<FileDownloadProgressListener> reference : arrayList) {
                        if (reference.get() != null) {
                            reference.get().onProgressDownload(fileName, progress);
                        }
                    }
                }
                listenerInProgress = false;
                processLaterArrays();
            } else if (id == NotificationCenter.FileUploadProgressChanged) {
                listenerInProgress = true;
                String fileName = (String) args[0];
                ArrayList<WeakReference<FileDownloadProgressListener>> arrayList = loadingFileObservers.get(fileName);
                if (arrayList != null) {
                    Float progress = (Float) args[1];
                    Boolean enc = (Boolean) args[2];
                    for (WeakReference<FileDownloadProgressListener> reference : arrayList) {
                        if (reference.get() != null) {
                            reference.get().onProgressUpload(fileName, progress, enc);
                        }
                    }
                }
                listenerInProgress = false;
                processLaterArrays();
                try {
                    ArrayList<SendMessagesHelper.DelayedMessage> delayedMessages = SendMessagesHelper.getInstance().getDelayedMessages(fileName);
                    if (delayedMessages != null) {
                        for (int a = 0; a < delayedMessages.size(); a++) {
                            SendMessagesHelper.DelayedMessage delayedMessage = delayedMessages.get(a);
                            if (delayedMessage.encryptedChat == null) {
                                long dialog_id = delayedMessage.peer;
                                if (delayedMessage.type == 4) {
                                    Long lastTime = typingTimes.get(dialog_id);
                                    if (lastTime == null || lastTime + 4000 < System.currentTimeMillis()) {
                                        MessagesController.getInstance().sendTyping(dialog_id, 4, 0);
                                        typingTimes.put(dialog_id, System.currentTimeMillis());
                                    }
                                } else {
                                    Long lastTime = typingTimes.get(dialog_id);
                                    TLRPC.Document document = delayedMessage.obj.getDocument();
                                    if (lastTime == null || lastTime + 4000 < System.currentTimeMillis()) {
                                        if (delayedMessage.obj.isRoundVideo()) {
                                            MessagesController.getInstance().sendTyping(dialog_id, 8, 0);
                                        } else if (delayedMessage.obj.isVideo()) {
                                            MessagesController.getInstance().sendTyping(dialog_id, 5, 0);
                                        } else if (delayedMessage.obj.getDocument() != null) {
                                            MessagesController.getInstance().sendTyping(dialog_id, 3, 0);
                                        } else if (delayedMessage.location != null) {
                                            MessagesController.getInstance().sendTyping(dialog_id, 4, 0);
                                        }
                                        typingTimes.put(dialog_id, System.currentTimeMillis());
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (id == NotificationCenter.messagesDeleted) {
                int channelId = (Integer) args[1];
                ArrayList<Integer> markAsDeletedMessages = (ArrayList<Integer>) args[0];
                if (playingMessageObject != null) {
                    if (channelId == playingMessageObject.messageOwner.to_id.channel_id) {
                        if (markAsDeletedMessages.contains(playingMessageObject.getId())) {
                            ///cleanupPlayer(true, true);
                        }
                    }
                }
                if (voiceMessagesPlaylist != null && !voiceMessagesPlaylist.isEmpty()) {
                    MessageObject messageObject = voiceMessagesPlaylist.get(0);
                    if (channelId == messageObject.messageOwner.to_id.channel_id) {
                        for (int a = 0; a < markAsDeletedMessages.size(); a++) {
                            messageObject = voiceMessagesPlaylistMap.remove(markAsDeletedMessages.get(a));
                            if (messageObject != null) {
                                voiceMessagesPlaylist.remove(messageObject);
                            }
                        }
                    }
                }
            } else if (id == NotificationCenter.removeAllMessagesFromDialog) {
                long did = (Long) args[0];
                if (playingMessageObject != null && playingMessageObject.getDialogId() == did) {
                    // cleanupPlayer(false, true);
                }
            } else if (id == NotificationCenter.musicDidLoaded) {
                long did = (Long) args[0];
                if (playingMessageObject != null && playingMessageObject.isMusic() && playingMessageObject.getDialogId() == did) {
                    ArrayList<MessageObject> arrayList = (ArrayList<MessageObject>) args[1];
                    playlist.addAll(0, arrayList);
                    if (shuffleMusic) {
                        buildShuffledPlayList();
                        currentPlaylistNum = 0;
                    } else {
                        currentPlaylistNum += arrayList.size();
                    }
                }
            } else if (id == NotificationCenter.didReceivedNewMessages) {
                if (voiceMessagesPlaylist != null && !voiceMessagesPlaylist.isEmpty()) {
                    MessageObject messageObject = voiceMessagesPlaylist.get(0);
                    long did = (Long) args[0];
                    if (did == messageObject.getDialogId()) {
                        ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
                        for (int a = 0; a < arr.size(); a++) {
                            messageObject = arr.get(a);
                            if ((messageObject.isVoice() || messageObject.isRoundVideo()) && (!voiceMessagesPlaylistUnread || messageObject.isContentUnread() && !messageObject.isOut())) {
                                voiceMessagesPlaylist.add(messageObject);
                                voiceMessagesPlaylistMap.put(messageObject.getId(), messageObject);
                            }
                        }
                    }
                }
            }
        }




        public void setInputFieldHasText(boolean value) {
            inputFieldHasText = value;
        }

        public MessageObject getPlayingMessageObject() {
            return playingMessageObject;
        }



        private void buildShuffledPlayList() {
            if (playlist.isEmpty()) {
                return;
            }
            ArrayList<MessageObject> all = new ArrayList<>(playlist);
            shuffledPlaylist.clear();

            MessageObject messageObject = playlist.get(currentPlaylistNum);
            all.remove(currentPlaylistNum);
            shuffledPlaylist.add(messageObject);

            int count = all.size();
            for (int a = 0; a < count; a++) {
                int index = Utilities.random.nextInt(all.size());
                shuffledPlaylist.add(all.get(index));
                all.remove(index);
            }
        }



        public void setBaseActivity(Activity activity, boolean set) {
            if (set) {
                baseActivity = activity;
            } else if (baseActivity == activity) {
                baseActivity = null;
            }
        }


        public ArrayList<MessageObject> getPlaylist() {
            return playlist;
        }


        public static void saveFile(String fullPath, Context context, final int type, final String name, final String mime) {
            if (fullPath == null) {
                return;
            }

            File file = null;
            if (fullPath != null && fullPath.length() != 0) {
                file = new File(fullPath);
                if (!file.exists()) {
                    file = null;
                }
            }

            if (file == null) {
                return;
            }

            final File sourceFile = file;
            final boolean[] cancelled = new boolean[]{false};
            if (sourceFile.exists()) {
                AlertDialog progressDialog = null;
                if (context != null && type != 0) {
                    try {
                        progressDialog = new AlertDialog(context, 2);
                        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
                        progressDialog.setCanceledOnTouchOutside(false);
                        progressDialog.setCancelable(true);
                        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                cancelled[0] = true;
                            }
                        });
                        progressDialog.show();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }

                final AlertDialog finalProgress = progressDialog;

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            File destFile;
                            if (type == 0) {
                                destFile = AndroidUtilities.generatePicturePath();
                            } else if (type == 1) {
                                destFile = AndroidUtilities.generateVideoPath();
                            } else {
                                File dir;
                                if (type == 2) {
                                    dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                                } else {
                                    dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                                }
                                dir.mkdir();
                                destFile = new File(dir, name);
                                if (destFile.exists()) {
                                    int idx = name.lastIndexOf('.');
                                    for (int a = 0; a < 10; a++) {
                                        String newName;
                                        if (idx != -1) {
                                            newName = name.substring(0, idx) + "(" + (a + 1) + ")" + name.substring(idx);
                                        } else {
                                            newName = name + "(" + (a + 1) + ")";
                                        }
                                        destFile = new File(dir, newName);
                                        if (!destFile.exists()) {
                                            break;
                                        }
                                    }
                                }
                            }
                            if (!destFile.exists()) {
                                destFile.createNewFile();
                            }
                            FileChannel source = null;
                            FileChannel destination = null;
                            boolean result = true;
                            long lastProgress = System.currentTimeMillis() - 500;
                            try {
                                source = new FileInputStream(sourceFile).getChannel();
                                destination = new FileOutputStream(destFile).getChannel();
                                long size = source.size();
                                for (long a = 0; a < size; a += 4096) {
                                    if (cancelled[0]) {
                                        break;
                                    }
                                    destination.transferFrom(source, a, Math.min(4096, size - a));
                                    if (finalProgress != null) {
                                        if (lastProgress <= System.currentTimeMillis() - 500) {
                                            lastProgress = System.currentTimeMillis();
                                            final int progress = (int) ((float) a / (float) size * 100);
                                            AndroidUtilities.runOnUIThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        finalProgress.setProgress(progress);
                                                    } catch (Exception e) {
                                                        FileLog.e(e);
                                                    }
                                                }
                                            });
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                                result = false;
                            } finally {
                                try {
                                    if (source != null) {
                                        source.close();
                                    }
                                } catch (Exception e) {
                                    //
                                }
                                try {
                                    if (destination != null) {
                                        destination.close();
                                    }
                                } catch (Exception e) {
                                    //
                                }
                            }
                            if (cancelled[0]) {
                                destFile.delete();
                                result = false;
                            }

                            if (result) {
                                if (type == 2) {
                                    DownloadManager downloadManager = (DownloadManager) ApplicationLoader.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE);
                                    downloadManager.addCompletedDownload(destFile.getName(), destFile.getName(), false, mime, destFile.getAbsolutePath(), destFile.length(), true);
                                } else {
                                    AndroidUtilities.addMediaToGallery(Uri.fromFile(destFile));
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        if (finalProgress != null) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        finalProgress.dismiss();
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                }
                            });
                        }
                    }
                }).start();
            }
        }

        public static boolean isWebp(Uri uri) {
            InputStream inputStream = null;
            try {
                inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                byte[] header = new byte[12];
                if (inputStream.read(header, 0, 12) == 12) {
                    String str = new String(header);
                    if (str != null) {
                        str = str.toLowerCase();
                        if (str.startsWith("riff") && str.endsWith("webp")) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (Exception e2) {
                    FileLog.e(e2);
                }
            }
            return false;
        }

        public static boolean isGif(Uri uri) {
            InputStream inputStream = null;
            try {
                inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                byte[] header = new byte[3];
                if (inputStream.read(header, 0, 3) == 3) {
                    String str = new String(header);
                    if (str != null && str.equalsIgnoreCase("gif")) {
                        return true;
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (Exception e2) {
                    FileLog.e(e2);
                }
            }
            return false;
        }

        public static String getFileName(Uri uri) {
            String result = null;
            if (uri.getScheme().equals("content")) {
                Cursor cursor = null;
                try {
                    cursor = ApplicationLoader.applicationContext.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                    if (cursor.moveToFirst()) {
                        result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
            if (result == null) {
                result = uri.getPath();
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
            return result;
        }

        public static String copyFileToCache(Uri uri, String ext) {
            InputStream inputStream = null;
            FileOutputStream output = null;
            try {
                String name = getFileName(uri);
                if (name == null) {
                    int id = UserConfig.lastLocalId;
                    UserConfig.lastLocalId--;
                    UserConfig.saveConfig(false);
                    name = String.format(Locale.US, "%d.%s", id, ext);
                }
                inputStream = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                File f = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), "sharing/");
                f.mkdirs();
                f = new File(f, name);
                output = new FileOutputStream(f);
                byte[] buffer = new byte[1024 * 20];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
                return f.getAbsolutePath();
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (Exception e2) {
                    FileLog.e(e2);
                }
                try {
                    if (output != null) {
                        output.close();
                    }
                } catch (Exception e2) {
                    FileLog.e(e2);
                }
            }
            return null;
        }

        public void toggleSaveToGallery() {
            saveToGallery = !saveToGallery;
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("save_gallery", saveToGallery);
            editor.commit();
            checkSaveToGalleryFiles();
        }

        public void toggleAutoplayGifs() {
            autoplayGifs = !autoplayGifs;
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("autoplay_gif", autoplayGifs);
            editor.commit();
        }


        public void toggleInappCamera() {
            inappCamera = !inappCamera;
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("direct_share", inappCamera);
            editor.commit();
        }

        public void toggleRoundCamera16to9() {
            roundCamera16to9 = !roundCamera16to9;
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("roundCamera16to9", roundCamera16to9);
            editor.commit();
        }


        public void checkSaveToGalleryFiles() {
            try {
                File telegramPath = new File(Environment.getExternalStorageDirectory(), "Telegram");
                File imagePath = new File(telegramPath, "Telegram Images");
                imagePath.mkdir();
                File videoPath = new File(telegramPath, "Telegram Video");
                videoPath.mkdir();

                if (saveToGallery) {
                    if (imagePath.isDirectory()) {
                        new File(imagePath, ".nomedia").delete();
                    }
                    if (videoPath.isDirectory()) {
                        new File(videoPath, ".nomedia").delete();
                    }
                } else {
                    if (imagePath.isDirectory()) {
                        new File(imagePath, ".nomedia").createNewFile();
                    }
                    if (videoPath.isDirectory()) {
                        new File(videoPath, ".nomedia").createNewFile();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        public boolean canSaveToGallery() {
            return saveToGallery;
        }

        public boolean canAutoplayGifs() {
            return autoplayGifs;
        }

        public boolean canRaiseToSpeak() {
            return raiseToSpeak;
        }

        public boolean canCustomTabs() {
            return customTabs;
        }

        public boolean canDirectShare() {
            return directShare;
        }

        public boolean canInAppCamera() {
            return inappCamera;
        }

        public boolean canRoundCamera16to9() {
            return roundCamera16to9;
        }


        public void scheduleVideoConvert(MessageObject messageObject) {
            scheduleVideoConvert(messageObject, false);
        }

        public boolean scheduleVideoConvert(MessageObject messageObject, boolean isEmpty) {
            if (isEmpty && !videoConvertQueue.isEmpty()) {
                return false;
            } else if (isEmpty) {
                new File(messageObject.messageOwner.attachPath).delete();
            }
            videoConvertQueue.add(messageObject);
            if (videoConvertQueue.size() == 1) {
                startVideoConvertFromQueue();
            }
            return true;
        }

        public void cancelVideoConvert(MessageObject messageObject) {
            if (messageObject == null) {
                synchronized (videoConvertSync) {
                    cancelCurrentVideoConversion = true;
                }
            } else {
                if (!videoConvertQueue.isEmpty()) {
                    if (videoConvertQueue.get(0) == messageObject) {
                        synchronized (videoConvertSync) {
                            cancelCurrentVideoConversion = true;
                        }
                    } else {
                        videoConvertQueue.remove(messageObject);
                    }
                }
            }
        }
    public static class SearchImage {
        public String id;
        public String imageUrl;
        public String thumbUrl;
        public String localUrl;
        public int width;
        public int height;
        public int size;
        public int type;
        public int date;
        public CharSequence caption;
        public TLRPC.Document document;

        public int ttl;
        public ArrayList<TLRPC.InputDocument> stickers = new ArrayList<>();
    }


    private boolean startVideoConvertFromQueue() {
            if (!videoConvertQueue.isEmpty()) {
                synchronized (videoConvertSync) {
                    cancelCurrentVideoConversion = false;
                }
                MessageObject messageObject = videoConvertQueue.get(0);
                Intent intent = new Intent(ApplicationLoader.applicationContext, VideoEncodingService.class);
                intent.putExtra("path", messageObject.messageOwner.attachPath);
                if (messageObject.messageOwner.media.document != null) {
                    for (int a = 0; a < messageObject.messageOwner.media.document.attributes.size(); a++) {
                        TLRPC.DocumentAttribute documentAttribute = messageObject.messageOwner.media.document.attributes.get(a);
                        if (documentAttribute instanceof TLRPC.TL_documentAttributeAnimated) {
                            intent.putExtra("gif", true);
                            break;
                        }
                    }
                }
                if (messageObject.getId() != 0) {
                    ApplicationLoader.applicationContext.startService(intent);
                }
                VideoConvertRunnable.runConversion(messageObject);
                return true;
            }
            return false;
        }

        @SuppressLint("NewApi")
        public static MediaCodecInfo selectCodec(String mimeType) {
            int numCodecs = MediaCodecList.getCodecCount();
            MediaCodecInfo lastCodecInfo = null;
            for (int i = 0; i < numCodecs; i++) {
                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
                if (!codecInfo.isEncoder()) {
                    continue;
                }
                String[] types = codecInfo.getSupportedTypes();
                for (String type : types) {
                    if (type.equalsIgnoreCase(mimeType)) {
                        lastCodecInfo = codecInfo;
                        if (!lastCodecInfo.getName().equals("OMX.SEC.avc.enc")) {
                            return lastCodecInfo;
                        } else if (lastCodecInfo.getName().equals("OMX.SEC.AVC.Encoder")) {
                            return lastCodecInfo;
                        }
                    }
                }
            }
            return lastCodecInfo;
        }

        private static boolean isRecognizedFormat(int colorFormat) {
            switch (colorFormat) {
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
                case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                    return true;
                default:
                    return false;
            }
        }

        @SuppressLint("NewApi")
        public static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
            MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
            int lastColorFormat = 0;
            for (int i = 0; i < capabilities.colorFormats.length; i++) {
                int colorFormat = capabilities.colorFormats[i];
                if (isRecognizedFormat(colorFormat)) {
                    lastColorFormat = colorFormat;
                    if (!(codecInfo.getName().equals("OMX.SEC.AVC.Encoder") && colorFormat == 19)) {
                        return colorFormat;
                    }
                }
            }
            return lastColorFormat;
        }

        private int selectTrack(MediaExtractor extractor, boolean audio) {
            int numTracks = extractor.getTrackCount();
            for (int i = 0; i < numTracks; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (audio) {
                    if (mime.startsWith("audio/")) {
                        return i;
                    }
                } else {
                    if (mime.startsWith("video/")) {
                        return i;
                    }
                }
            }
            return -5;
        }

        private void didWriteData(final MessageObject messageObject, final File file, final boolean last, final boolean error) {
            final boolean firstWrite = videoConvertFirstWrite;
            if (firstWrite) {
                videoConvertFirstWrite = false;
            }
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (error || last) {
                        synchronized (videoConvertSync) {
                            cancelCurrentVideoConversion = false;
                        }
                        videoConvertQueue.remove(messageObject);
                        startVideoConvertFromQueue();
                    }
                    if (error) {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.FilePreparingFailed, messageObject, file.toString());
                    } else {
                        if (firstWrite) {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.FilePreparingStarted, messageObject, file.toString());
                        }
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.FileNewChunkAvailable, messageObject, file.toString(), last ? file.length() : 0);
                    }
                }
            });
        }

        private long readAndWriteTrack(final MessageObject messageObject, MediaExtractor extractor, MP4Builder mediaMuxer, MediaCodec.BufferInfo info, long start, long end, File file, boolean isAudio) throws Exception {
            int trackIndex = selectTrack(extractor, isAudio);
            if (trackIndex >= 0) {
                extractor.selectTrack(trackIndex);
                MediaFormat trackFormat = extractor.getTrackFormat(trackIndex);
                int muxerTrackIndex = mediaMuxer.addTrack(trackFormat, isAudio);
                int maxBufferSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                boolean inputDone = false;
                if (start > 0) {
                    extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                } else {
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                }
                ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
                long startTime = -1;

                checkConversionCanceled();

                while (!inputDone) {
                    checkConversionCanceled();

                    boolean eof = false;
                    int index = extractor.getSampleTrackIndex();
                    if (index == trackIndex) {
                        info.size = extractor.readSampleData(buffer, 0);
                        if (Build.VERSION.SDK_INT < 21) {
                            buffer.position(0);
                            buffer.limit(info.size);
                        }
                        if (!isAudio) {
                            byte[] array = buffer.array();
                            if (array != null) {
                                int offset = buffer.arrayOffset();
                                int len = offset + buffer.limit();
                                int writeStart = -1;
                                for (int a = offset; a <= len - 4; a++) {
                                    if (array[a] == 0 && array[a + 1] == 0 && array[a + 2] == 0 && array[a + 3] == 1 || a == len - 4) {
                                        if (writeStart != -1) {
                                            int l = a - writeStart - (a != len - 4 ? 4 : 0);
                                            array[writeStart] = (byte) (l >> 24);
                                            array[writeStart + 1] = (byte) (l >> 16);
                                            array[writeStart + 2] = (byte) (l >> 8);
                                            array[writeStart + 3] = (byte) l;
                                            writeStart = a;
                                        } else {
                                            writeStart = a;
                                        }
                                    }
                                }
                            }
                        }
                        if (info.size >= 0) {
                            info.presentationTimeUs = extractor.getSampleTime();
                        } else {
                            info.size = 0;
                            eof = true;
                        }

                        if (info.size > 0 && !eof) {
                            if (start > 0 && startTime == -1) {
                                startTime = info.presentationTimeUs;
                            }
                            if (end < 0 || info.presentationTimeUs < end) {
                                info.offset = 0;
                                info.flags = extractor.getSampleFlags();
                                if (mediaMuxer.writeSampleData(muxerTrackIndex, buffer, info, false)) {
                                    didWriteData(messageObject, file, false, false);
                                }
                            } else {
                                eof = true;
                            }
                        }
                        if (!eof) {
                            extractor.advance();
                        }
                    } else if (index == -1) {
                        eof = true;
                    } else {
                        extractor.advance();
                    }
                    if (eof) {
                        inputDone = true;
                    }
                }

                extractor.unselectTrack(trackIndex);
                return startTime;
            }
            return -1;
        }

        private static class VideoConvertRunnable implements Runnable {

            private MessageObject messageObject;

            private VideoConvertRunnable(MessageObject message) {
                messageObject = message;
            }

            @Override
            public void run() {
                MediaController.getInstance().convertVideo(messageObject);
            }

            public static void runConversion(final MessageObject obj) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            VideoConvertRunnable wrapper = new VideoConvertRunnable(obj);
                            Thread th = new Thread(wrapper, "VideoConvertRunnable");
                            th.start();
                            th.join();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }).start();
            }
        }

        private void checkConversionCanceled() throws Exception {
            boolean cancelConversion;
            synchronized (videoConvertSync) {
                cancelConversion = cancelCurrentVideoConversion;
            }
            if (cancelConversion) {
                throw new RuntimeException("canceled conversion");
            }
        }

        private boolean convertVideo(final MessageObject messageObject) {
            String videoPath = messageObject.videoEditedInfo.originalPath;
            long startTime = messageObject.videoEditedInfo.startTime;
            long endTime = messageObject.videoEditedInfo.endTime;
            int resultWidth = messageObject.videoEditedInfo.resultWidth;
            int resultHeight = messageObject.videoEditedInfo.resultHeight;
            int rotationValue = messageObject.videoEditedInfo.rotationValue;
            int originalWidth = messageObject.videoEditedInfo.originalWidth;
            int originalHeight = messageObject.videoEditedInfo.originalHeight;
            int bitrate = messageObject.videoEditedInfo.bitrate;
            int rotateRender = 0;
            File cacheFile = new File(messageObject.messageOwner.attachPath);

            if (Build.VERSION.SDK_INT < 18 && resultHeight > resultWidth && resultWidth != originalWidth && resultHeight != originalHeight) {
                int temp = resultHeight;
                resultHeight = resultWidth;
                resultWidth = temp;
                rotationValue = 90;
                rotateRender = 270;
            } else if (Build.VERSION.SDK_INT > 20) {
                if (rotationValue == 90) {
                    int temp = resultHeight;
                    resultHeight = resultWidth;
                    resultWidth = temp;
                    rotationValue = 0;
                    rotateRender = 270;
                } else if (rotationValue == 180) {
                    rotateRender = 180;
                    rotationValue = 0;
                } else if (rotationValue == 270) {
                    int temp = resultHeight;
                    resultHeight = resultWidth;
                    resultWidth = temp;
                    rotationValue = 0;
                    rotateRender = 90;
                }
            }

            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("videoconvert", Activity.MODE_PRIVATE);
            File inputFile = new File(videoPath);
            if (messageObject.getId() != 0) {
                boolean isPreviousOk = preferences.getBoolean("isPreviousOk", true);
                preferences.edit().putBoolean("isPreviousOk", false).commit();
                if (!inputFile.canRead() || !isPreviousOk) {
                    didWriteData(messageObject, cacheFile, true, true);
                    preferences.edit().putBoolean("isPreviousOk", true).commit();
                    return false;
                }
            }

            videoConvertFirstWrite = true;
            boolean error = false;
            long videoStartTime = startTime;

            long time = System.currentTimeMillis();

            if (resultWidth != 0 && resultHeight != 0) {
                MP4Builder mediaMuxer = null;
                MediaExtractor extractor = null;

                try {
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    Mp4Movie movie = new Mp4Movie();
                    movie.setCacheFile(cacheFile);
                    movie.setRotation(rotationValue);
                    movie.setSize(resultWidth, resultHeight);
                    mediaMuxer = new MP4Builder().createMovie(movie);
                    extractor = new MediaExtractor();
                    extractor.setDataSource(videoPath);

                    checkConversionCanceled();

                    if (resultWidth != originalWidth || resultHeight != originalHeight || rotateRender != 0 || messageObject.videoEditedInfo.roundVideo) {
                        int videoIndex;
                        videoIndex = selectTrack(extractor, false);
                        if (videoIndex >= 0) {
                            MediaCodec decoder = null;
                            MediaCodec encoder = null;
                            InputSurface inputSurface = null;
                            OutputSurface outputSurface = null;

                            try {
                                long videoTime = -1;
                                boolean outputDone = false;
                                boolean inputDone = false;
                                boolean decoderDone = false;
                                int swapUV = 0;
                                int videoTrackIndex = -5;

                                int colorFormat;
                                int processorType = PROCESSOR_TYPE_OTHER;
                                String manufacturer = Build.MANUFACTURER.toLowerCase();
                                if (Build.VERSION.SDK_INT < 18) {
                                    MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
                                    colorFormat = selectColorFormat(codecInfo, MIME_TYPE);
                                    if (colorFormat == 0) {
                                        throw new RuntimeException("no supported color format");
                                    }
                                    String codecName = codecInfo.getName();
                                    if (codecName.contains("OMX.qcom.")) {
                                        processorType = PROCESSOR_TYPE_QCOM;
                                        if (Build.VERSION.SDK_INT == 16) {
                                            if (manufacturer.equals("lge") || manufacturer.equals("nokia")) {
                                                swapUV = 1;
                                            }
                                        }
                                    } else if (codecName.contains("OMX.Intel.")) {
                                        processorType = PROCESSOR_TYPE_INTEL;
                                    } else if (codecName.equals("OMX.MTK.VIDEO.ENCODER.AVC")) {
                                        processorType = PROCESSOR_TYPE_MTK;
                                    } else if (codecName.equals("OMX.SEC.AVC.Encoder")) {
                                        processorType = PROCESSOR_TYPE_SEC;
                                        swapUV = 1;
                                    } else if (codecName.equals("OMX.TI.DUCATI1.VIDEO.H264E")) {
                                        processorType = PROCESSOR_TYPE_TI;
                                    }
                                    FileLog.e("codec = " + codecInfo.getName() + " manufacturer = " + manufacturer + "device = " + Build.MODEL);
                                } else {
                                    colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
                                }
                                FileLog.e("colorFormat = " + colorFormat);

                                int resultHeightAligned = resultHeight;
                                int padding = 0;
                                int bufferSize = resultWidth * resultHeight * 3 / 2;
                                if (processorType == PROCESSOR_TYPE_OTHER) {
                                    if (resultHeight % 16 != 0) {
                                        resultHeightAligned += (16 - (resultHeight % 16));
                                        padding = resultWidth * (resultHeightAligned - resultHeight);
                                        bufferSize += padding * 5 / 4;
                                    }
                                } else if (processorType == PROCESSOR_TYPE_QCOM) {
                                    if (!manufacturer.toLowerCase().equals("lge")) {
                                        int uvoffset = (resultWidth * resultHeight + 2047) & ~2047;
                                        padding = uvoffset - (resultWidth * resultHeight);
                                        bufferSize += padding;
                                    }
                                } else if (processorType == PROCESSOR_TYPE_TI) {
                                    //resultHeightAligned = 368;
                                    //bufferSize = resultWidth * resultHeightAligned * 3 / 2;
                                    //resultHeightAligned += (16 - (resultHeight % 16));
                                    //padding = resultWidth * (resultHeightAligned - resultHeight);
                                    //bufferSize += padding * 5 / 4;
                                } else if (processorType == PROCESSOR_TYPE_MTK) {
                                    if (manufacturer.equals("baidu")) {
                                        resultHeightAligned += (16 - (resultHeight % 16));
                                        padding = resultWidth * (resultHeightAligned - resultHeight);
                                        bufferSize += padding * 5 / 4;
                                    }
                                }

                                extractor.selectTrack(videoIndex);
                                if (startTime > 0) {
                                    extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                                } else {
                                    extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                                }
                                MediaFormat inputFormat = extractor.getTrackFormat(videoIndex);

                                MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
                                outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
                                outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate > 0 ? bitrate : 921600);
                                outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
                                outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
                                if (Build.VERSION.SDK_INT < 18) {
                                    outputFormat.setInteger("stride", resultWidth + 32);
                                    outputFormat.setInteger("slice-height", resultHeight);
                                }

                                encoder = MediaCodec.createEncoderByType(MIME_TYPE);
                                encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                                if (Build.VERSION.SDK_INT >= 18) {
                                    inputSurface = new InputSurface(encoder.createInputSurface());
                                    inputSurface.makeCurrent();
                                }
                                encoder.start();

                                decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
                                if (Build.VERSION.SDK_INT >= 18) {
                                    outputSurface = new OutputSurface();
                                } else {
                                    outputSurface = new OutputSurface(resultWidth, resultHeight, rotateRender);
                                }
                                decoder.configure(inputFormat, outputSurface.getSurface(), null, 0);
                                decoder.start();

                                final int TIMEOUT_USEC = 2500;
                                ByteBuffer[] decoderInputBuffers = null;
                                ByteBuffer[] encoderOutputBuffers = null;
                                ByteBuffer[] encoderInputBuffers = null;
                                if (Build.VERSION.SDK_INT < 21) {
                                    decoderInputBuffers = decoder.getInputBuffers();
                                    encoderOutputBuffers = encoder.getOutputBuffers();
                                    if (Build.VERSION.SDK_INT < 18) {
                                        encoderInputBuffers = encoder.getInputBuffers();
                                    }
                                }

                                checkConversionCanceled();

                                while (!outputDone) {
                                    checkConversionCanceled();
                                    if (!inputDone) {
                                        boolean eof = false;
                                        int index = extractor.getSampleTrackIndex();
                                        if (index == videoIndex) {
                                            int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                                            if (inputBufIndex >= 0) {
                                                ByteBuffer inputBuf;
                                                if (Build.VERSION.SDK_INT < 21) {
                                                    inputBuf = decoderInputBuffers[inputBufIndex];
                                                } else {
                                                    inputBuf = decoder.getInputBuffer(inputBufIndex);
                                                }
                                                int chunkSize = extractor.readSampleData(inputBuf, 0);
                                                if (chunkSize < 0) {
                                                    decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                                    inputDone = true;
                                                } else {
                                                    decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, extractor.getSampleTime(), 0);
                                                    extractor.advance();
                                                }
                                            }
                                        } else if (index == -1) {
                                            eof = true;
                                        }
                                        if (eof) {
                                            int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                                            if (inputBufIndex >= 0) {
                                                decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                                inputDone = true;
                                            }
                                        }
                                    }

                                    boolean decoderOutputAvailable = !decoderDone;
                                    boolean encoderOutputAvailable = true;
                                    while (decoderOutputAvailable || encoderOutputAvailable) {
                                        checkConversionCanceled();
                                        int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                                        if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                            encoderOutputAvailable = false;
                                        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                            if (Build.VERSION.SDK_INT < 21) {
                                                encoderOutputBuffers = encoder.getOutputBuffers();
                                            }
                                        } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                            MediaFormat newFormat = encoder.getOutputFormat();
                                            if (videoTrackIndex == -5) {
                                                videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                                            }
                                        } else if (encoderStatus < 0) {
                                            throw new RuntimeException("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                                        } else {
                                            ByteBuffer encodedData;
                                            if (Build.VERSION.SDK_INT < 21) {
                                                encodedData = encoderOutputBuffers[encoderStatus];
                                            } else {
                                                encodedData = encoder.getOutputBuffer(encoderStatus);
                                            }
                                            if (encodedData == null) {
                                                throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                                            }
                                            if (info.size > 1) {
                                                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                                    if (mediaMuxer.writeSampleData(videoTrackIndex, encodedData, info, true)) {
                                                        didWriteData(messageObject, cacheFile, false, false);
                                                    }
                                                } else if (videoTrackIndex == -5) {
                                                    byte[] csd = new byte[info.size];
                                                    encodedData.limit(info.offset + info.size);
                                                    encodedData.position(info.offset);
                                                    encodedData.get(csd);
                                                    ByteBuffer sps = null;
                                                    ByteBuffer pps = null;
                                                    for (int a = info.size - 1; a >= 0; a--) {
                                                        if (a > 3) {
                                                            if (csd[a] == 1 && csd[a - 1] == 0 && csd[a - 2] == 0 && csd[a - 3] == 0) {
                                                                sps = ByteBuffer.allocate(a - 3);
                                                                pps = ByteBuffer.allocate(info.size - (a - 3));
                                                                sps.put(csd, 0, a - 3).position(0);
                                                                pps.put(csd, a - 3, info.size - (a - 3)).position(0);
                                                                break;
                                                            }
                                                        } else {
                                                            break;
                                                        }
                                                    }

                                                    MediaFormat newFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
                                                    if (sps != null && pps != null) {
                                                        newFormat.setByteBuffer("csd-0", sps);
                                                        newFormat.setByteBuffer("csd-1", pps);
                                                    }
                                                    videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                                                }
                                            }
                                            outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                                            encoder.releaseOutputBuffer(encoderStatus, false);
                                        }
                                        if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                                            continue;
                                        }

                                        if (!decoderDone) {
                                            int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                                            if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                                decoderOutputAvailable = false;
                                            } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                                            } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                                MediaFormat newFormat = decoder.getOutputFormat();
                                                FileLog.e("newFormat = " + newFormat);
                                            } else if (decoderStatus < 0) {
                                                throw new RuntimeException("unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                                            } else {
                                                boolean doRender;
                                                if (Build.VERSION.SDK_INT >= 18) {
                                                    doRender = info.size != 0;
                                                } else {
                                                    doRender = info.size != 0 || info.presentationTimeUs != 0;
                                                }
                                                if (endTime > 0 && info.presentationTimeUs >= endTime) {
                                                    inputDone = true;
                                                    decoderDone = true;
                                                    doRender = false;
                                                    info.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                                                }
                                                if (startTime > 0 && videoTime == -1) {
                                                    if (info.presentationTimeUs < startTime) {
                                                        doRender = false;
                                                        FileLog.e("drop frame startTime = " + startTime + " present time = " + info.presentationTimeUs);
                                                    } else {
                                                        videoTime = info.presentationTimeUs;
                                                    }
                                                }
                                                decoder.releaseOutputBuffer(decoderStatus, doRender);
                                                if (doRender) {
                                                    boolean errorWait = false;
                                                    try {
                                                        outputSurface.awaitNewImage();
                                                    } catch (Exception e) {
                                                        errorWait = true;
                                                        FileLog.e(e);
                                                    }
                                                    if (!errorWait) {
                                                        if (Build.VERSION.SDK_INT >= 18) {
                                                            outputSurface.drawImage(false);
                                                            inputSurface.setPresentationTime(info.presentationTimeUs * 1000);
                                                            inputSurface.swapBuffers();
                                                        } else {
                                                            int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                                                            if (inputBufIndex >= 0) {
                                                                outputSurface.drawImage(true);
                                                                ByteBuffer rgbBuf = outputSurface.getFrame();
                                                                ByteBuffer yuvBuf = encoderInputBuffers[inputBufIndex];
                                                                yuvBuf.clear();
                                                                Utilities.convertVideoFrame(rgbBuf, yuvBuf, colorFormat, resultWidth, resultHeight, padding, swapUV);
                                                                encoder.queueInputBuffer(inputBufIndex, 0, bufferSize, info.presentationTimeUs, 0);
                                                            } else {
                                                                FileLog.e("input buffer not available");
                                                            }
                                                        }
                                                    }
                                                }
                                                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                                    decoderOutputAvailable = false;
                                                    FileLog.e("decoder stream end");
                                                    if (Build.VERSION.SDK_INT >= 18) {
                                                        encoder.signalEndOfInputStream();
                                                    } else {
                                                        int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                                                        if (inputBufIndex >= 0) {
                                                            encoder.queueInputBuffer(inputBufIndex, 0, 1, info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (videoTime != -1) {
                                    videoStartTime = videoTime;
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                                error = true;
                            }

                            extractor.unselectTrack(videoIndex);

                            if (outputSurface != null) {
                                outputSurface.release();
                            }
                            if (inputSurface != null) {
                                inputSurface.release();
                            }
                            if (decoder != null) {
                                decoder.stop();
                                decoder.release();
                            }
                            if (encoder != null) {
                                encoder.stop();
                                encoder.release();
                            }

                            checkConversionCanceled();
                        }
                    } else {
                        long videoTime = readAndWriteTrack(messageObject, extractor, mediaMuxer, info, startTime, endTime, cacheFile, false);
                        if (videoTime != -1) {
                            videoStartTime = videoTime;
                        }
                    }
                    if (!error && bitrate != -1) {
                        readAndWriteTrack(messageObject, extractor, mediaMuxer, info, videoStartTime, endTime, cacheFile, true);
                    }
                } catch (Exception e) {
                    error = true;
                    FileLog.e(e);
                } finally {
                    if (extractor != null) {
                        extractor.release();
                    }
                    if (mediaMuxer != null) {
                        try {
                            mediaMuxer.finishMovie();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    FileLog.e("time = " + (System.currentTimeMillis() - time));
                }
            } else {
                preferences.edit().putBoolean("isPreviousOk", true).commit();
                didWriteData(messageObject, cacheFile, true, true);
                return false;
            }
            preferences.edit().putBoolean("isPreviousOk", true).commit();
            didWriteData(messageObject, cacheFile, true, error);
            return true;
        }


}
