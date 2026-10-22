package com.xstreaming;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.UiThreadUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ConfigTransferModule extends ReactContextBaseJavaModule {
    private static final String MODULE_NAME = "ConfigTransferModule";
    private static final int IMPORT_CONFIG_REQUEST_CODE = 7301;

    private final ReactApplicationContext reactContext;
    private Promise importPromise;

    private final ActivityEventListener activityEventListener = new BaseActivityEventListener() {
        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, @Nullable Intent data) {
            if (requestCode != IMPORT_CONFIG_REQUEST_CODE) {
                return;
            }

            Promise promise = importPromise;
            importPromise = null;
            if (promise == null) {
                return;
            }

            if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
                promise.reject("USER_CANCEL", "User canceled");
                return;
            }

            new Thread(() -> {
                try {
                    String content = readUriContent(data.getData());
                    UiThreadUtil.runOnUiThread(() -> promise.resolve(content));
                } catch (Exception e) {
                    UiThreadUtil.runOnUiThread(() ->
                            promise.reject("IMPORT_CONFIG_FAILED", e.getMessage(), e));
                }
            }).start();
        }
    };

    public ConfigTransferModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.reactContext.addActivityEventListener(activityEventListener);
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @ReactMethod
    public void saveConfigFile(String fileName, String content, Promise promise) {
        if (content == null || content.trim().isEmpty()) {
            promise.reject("EMPTY_CONFIG", "Config content is empty");
            return;
        }

        new Thread(() -> {
            try {
                String safeName = buildSafeJsonName(fileName);
                String savedPath = writeConfigToDownloads(safeName, content);
                UiThreadUtil.runOnUiThread(() -> promise.resolve(savedPath));
            } catch (Exception e) {
                UiThreadUtil.runOnUiThread(() ->
                        promise.reject("EXPORT_CONFIG_FAILED", e.getMessage(), e));
            }
        }).start();
    }

    private String writeConfigToDownloads(String safeName, String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = reactContext.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            values.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/XStreaming");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri item = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (item == null) {
                values.remove(MediaStore.MediaColumns.RELATIVE_PATH);
                item = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            }
            if (item == null) {
                throw new IllegalStateException("Unable to create file in Downloads");
            }

            try (OutputStream outputStream = resolver.openOutputStream(item)) {
                if (outputStream == null) {
                    throw new IllegalStateException("Unable to open Downloads file");
                }
                outputStream.write(bytes);
            }

            ContentValues published = new ContentValues();
            published.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(item, published, null, null);
            return Environment.DIRECTORY_DOWNLOADS + "/XStreaming/" + safeName;
        }

        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File exportDir = new File(downloads, "XStreaming");
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            File appDownloads = reactContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (appDownloads == null) {
                throw new IllegalStateException("Unable to create Downloads directory");
            }
            exportDir = new File(appDownloads, "XStreaming");
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                throw new IllegalStateException("Unable to create Downloads directory");
            }
        }

        File configFile = new File(exportDir, safeName);
        try (FileOutputStream outputStream = new FileOutputStream(configFile)) {
            outputStream.write(bytes);
        }
        return configFile.getAbsolutePath();
    }

    @ReactMethod
    public void importConfigFile(Promise promise) {
        Activity activity = getCurrentActivity();
        if (activity == null || activity.isFinishing()) {
            promise.reject("NO_ACTIVITY", "Current activity is not available");
            return;
        }

        if (importPromise != null) {
            promise.reject("IMPORT_IN_PROGRESS", "Another import is already in progress");
            return;
        }

        importPromise = promise;
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/json", "text/plain"});
            activity.startActivityForResult(intent, IMPORT_CONFIG_REQUEST_CODE);
        } catch (Exception e) {
            importPromise = null;
            promise.reject("OPEN_DOCUMENT_FAILED", e.getMessage(), e);
        }
    }

    private String readUriContent(Uri uri) throws Exception {
        try (InputStream inputStream = reactContext.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IllegalStateException("Unable to open selected file");
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString("UTF-8");
        }
    }

    private String buildSafeJsonName(String fileName) {
        String name = fileName == null ? "" : fileName.trim();
        if (name.isEmpty()) {
            name = "xstreaming_export.json";
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!name.toLowerCase().endsWith(".json")) {
            name = name + ".json";
        }
        return name;
    }
}
