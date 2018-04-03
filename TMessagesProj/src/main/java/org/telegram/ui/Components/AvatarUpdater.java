/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

          package org.telegram.ui.Components;

          import android.Manifest;
          import android.app.Activity;
          import android.content.Intent;
          import android.content.pm.PackageManager;
          import android.graphics.Bitmap;
          import android.net.Uri;
          import android.os.Build;
          import android.provider.MediaStore;
          import android.support.v4.content.FileProvider;

          import org.telegram.messenger.AndroidUtilities;
          import org.telegram.messenger.BuildConfig;
          import org.telegram.messenger.FileLoader;
          import org.telegram.messenger.FileLog;
          import org.telegram.messenger.ImageLoader;
          import org.telegram.messenger.NotificationCenter;
          import org.telegram.messenger.SendMessagesHelper;
          import org.telegram.messenger.UserConfig;
          import org.telegram.messenger.query.DraftQuery;
          import org.telegram.tgnet.ConnectionsManager;
          import org.telegram.tgnet.TLRPC;
          import org.telegram.ui.ActionBar.BaseFragment;
          import org.telegram.ui.DocumentSelectActivity;

          import java.io.File;
          import java.util.ArrayList;

public class AvatarUpdater implements NotificationCenter.NotificationCenterDelegate {

    public String currentPicturePath;
    private TLRPC.PhotoSize smallPhoto;
    private TLRPC.PhotoSize bigPhoto;
    public String uploadingAvatar = null;
    File picturePath = null;
    public BaseFragment parentFragment = null;
    public AvatarUpdaterDelegate delegate;
    private boolean clearAfterUpdate = false;
    public boolean returnOnly = false;

    public interface AvatarUpdaterDelegate {
        void didUploadedPhoto(TLRPC.InputFile file, TLRPC.PhotoSize small, TLRPC.PhotoSize big);
    }

    public void clear() {
        if (uploadingAvatar != null) {
            clearAfterUpdate = true;
        } else {
            parentFragment = null;
            delegate = null;
        }
    }

    public void openCamera() {
        if (parentFragment == null || parentFragment.getParentActivity() == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 23 && parentFragment.getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                parentFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, 19);
                return;
            }
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File image = AndroidUtilities.generatePicturePath();
            if (image != null) {
                if (Build.VERSION.SDK_INT >= 24) {
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(parentFragment.getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", image));
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
                }
                currentPicturePath = image.getAbsolutePath();
            }
            parentFragment.startActivityForResult(takePictureIntent, 13);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void openGallery(final BaseFragment s) {
        if (Build.VERSION.SDK_INT >= 23 && s.getParentActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            s.getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 4);
            return;
        }
        Intent photoPickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
        photoPickerIntent.setType("image/*");
        s.startActivityForResult(photoPickerIntent, 21);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 14) {
                if (data == null || data.getData() == null) {
                    return;
                }
            }else if(requestCode == 21){
                if(data!=null){
                    processBitmap(ImageLoader.loadBitmap(null,data.getData(),800,800,true));
                }
            }
        }
    }

    public void processBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return;
        }
        smallPhoto = ImageLoader.scaleAndSaveImage(bitmap, 100, 100, 80, false);
        bigPhoto = ImageLoader.scaleAndSaveImage(bitmap, 800, 800, 80, false, 320, 320);
        bitmap.recycle();
        if (bigPhoto != null && smallPhoto != null) {
            if (returnOnly) {
                if (delegate != null) {
                    delegate.didUploadedPhoto(null, smallPhoto, bigPhoto);
                }
            } else {
                UserConfig.saveConfig(false);
                uploadingAvatar = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + bigPhoto.location.volume_id + "_" + bigPhoto.location.local_id + ".jpg";
                NotificationCenter.getInstance().addObserver(AvatarUpdater.this, NotificationCenter.FileDidUpload);
                NotificationCenter.getInstance().addObserver(AvatarUpdater.this, NotificationCenter.FileDidFailUpload);
                FileLoader.getInstance().uploadFile(uploadingAvatar, false, true, ConnectionsManager.FileTypePhoto);
            }
        }
    }


    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == NotificationCenter.FileDidUpload) {
            String location = (String) args[0];
            if (uploadingAvatar != null && location.equals(uploadingAvatar)) {
                NotificationCenter.getInstance().removeObserver(AvatarUpdater.this, NotificationCenter.FileDidUpload);
                NotificationCenter.getInstance().removeObserver(AvatarUpdater.this, NotificationCenter.FileDidFailUpload);
                if (delegate != null) {
                    delegate.didUploadedPhoto((TLRPC.InputFile) args[1], smallPhoto, bigPhoto);
                }
                uploadingAvatar = null;
                if (clearAfterUpdate) {
                    parentFragment = null;
                    delegate = null;
                }
            }
        } else if (id == NotificationCenter.FileDidFailUpload) {
            String location = (String) args[0];
            if (uploadingAvatar != null && location.equals(uploadingAvatar)) {
                NotificationCenter.getInstance().removeObserver(AvatarUpdater.this, NotificationCenter.FileDidUpload);
                NotificationCenter.getInstance().removeObserver(AvatarUpdater.this, NotificationCenter.FileDidFailUpload);
                uploadingAvatar = null;
                if (clearAfterUpdate) {
                    parentFragment = null;
                    delegate = null;
                }
            }
        }
    }
}
