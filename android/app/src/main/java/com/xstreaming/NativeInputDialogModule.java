package com.xstreaming;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public class NativeInputDialogModule extends ReactContextBaseJavaModule {
    private Dialog currentDialog;
    private EditText currentInput;
    private Promise currentPromise;

    public NativeInputDialogModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "NativeInputDialog";
    }

    @ReactMethod
    public void showTextInput(ReadableMap options, Promise promise) {
        UiThreadUtil.runOnUiThread(() -> {
            Activity activity = getCurrentActivity();
            if (activity == null || activity.isFinishing()) {
                promise.reject("NO_ACTIVITY", "Current activity is not available");
                return;
            }

            dismissCurrent("cancel", "");
            currentPromise = promise;

            String title = getString(options, "title", "");
            String message = getString(options, "message", "");
            String text = getString(options, "text", "");
            String hint = getString(options, "hint", "");
            String confirmText = getString(options, "confirmText", "OK");
            String cancelText = getString(options, "cancelText", "Cancel");
            int inputScope = getInt(options, "inputScope", 0);
            int maxLength = getInt(options, "maxLength", 0);

            EditText input = new EditText(activity);
            input.setSingleLine(false);
            input.setMinLines(1);
            input.setMaxLines(4);
            input.setText(text);
            input.setHint(hint);
            input.setSelectAllOnFocus(false);
            input.setInputType(resolveInputType(inputScope));
            input.setFocusable(true);
            input.setFocusableInTouchMode(true);
            if (maxLength > 0) {
                input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
            }

            FrameLayout container = new FrameLayout(activity);
            int horizontalPadding = dp(activity, 24);
            int topPadding = dp(activity, 8);
            container.setPadding(horizontalPadding, topPadding, horizontalPadding, 0);
            container.addView(input, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            ));

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setView(container)
                    .setNegativeButton(cancelText, (d, which) -> resolveCurrent("cancel", input.getText().toString()))
                    .setPositiveButton(confirmText, (d, which) -> resolveCurrent("confirm", input.getText().toString()))
                    .create();

            currentDialog = dialog;
            currentInput = input;
            dialog.setOnCancelListener(d -> resolveCurrent("cancel", input.getText().toString()));
            dialog.setOnDismissListener(d -> {
                currentDialog = null;
                currentInput = null;
            });
            dialog.setOnShowListener(d -> {
                input.requestFocus();
                input.setSelection(input.getText().length());
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setFocusable(true);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setFocusable(true);

                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                }
            });
            dialog.show();
        });
    }

    @ReactMethod
    public void dismiss() {
        UiThreadUtil.runOnUiThread(() -> dismissCurrent("cancel", getCurrentText()));
    }

    @ReactMethod
    public void showOptions(ReadableMap options, Promise promise) {
        UiThreadUtil.runOnUiThread(() -> {
            Activity activity = getCurrentActivity();
            if (activity == null || activity.isFinishing()) {
                promise.reject("NO_ACTIVITY", "Current activity is not available");
                return;
            }

            dismissCurrent("cancel", "");
            currentPromise = promise;
            currentInput = null;

            ReadableArray items = options != null && options.hasKey("items") && !options.isNull("items")
                    ? options.getArray("items")
                    : null;

            if (items == null || items.size() == 0) {
                resolveCurrentOption("cancel", -1, "");
                return;
            }

            String[] labels = new String[items.size()];
            String[] ids = new String[items.size()];
            boolean[] isSwitch = new boolean[items.size()];
            boolean[] checked = new boolean[items.size()];
            boolean[] danger = new boolean[items.size()];
            for (int i = 0; i < items.size(); i++) {
                if (items.getType(i) == com.facebook.react.bridge.ReadableType.Map) {
                    ReadableMap item = items.getMap(i);
                    String id = getString(item, "id", String.valueOf(i));
                    String label = getString(item, "title", id);
                    ids[i] = id;
                    labels[i] = label;
                    isSwitch[i] = "switch".equals(getString(item, "type", ""));
                    checked[i] = getBoolean(item, "checked", false);
                    danger[i] = getBoolean(item, "danger", false);
                } else {
                    String label = items.getString(i);
                    ids[i] = String.valueOf(i);
                    labels[i] = label;
                }
            }

            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            int targetWidth = Math.min(dp(activity, 320), Math.round(screenWidth * 0.5f));
            boolean showAudioGainControl = getBoolean(options, "showAudioGainControl", false);
            int audioGain = Math.max(0, Math.min(10, getInt(options, "audioGain", 1)));
            String audioGainTitle = getString(options, "audioGainTitle", "Volume");
            boolean showBrightnessControl = getBoolean(options, "showBrightnessControl", false);
            String brightnessTitle = getString(options, "brightnessTitle", "Brightness");

            FrameLayout root = new FrameLayout(activity);
            root.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            root.setClickable(true);
            root.setFocusable(false);
            root.setFocusableInTouchMode(false);
            root.setOnClickListener(v -> {
                resolveCurrentOption("cancel", -1, "");
                if (currentDialog != null && currentDialog.isShowing()) {
                    currentDialog.dismiss();
                }
            });

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(0, dp(activity, 6), 0, dp(activity, 6));
            content.setClickable(true);
            content.setFocusable(false);
            content.setFocusableInTouchMode(false);
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.argb(214, 28, 31, 36));
            background.setCornerRadius(dp(activity, 18));

            ScrollView scroll = new ScrollView(activity);
            scroll.setBackground(background);
            scroll.setClipToOutline(true);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.addView(content, new ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT
            ));

            FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                    targetWidth,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
            );
            contentParams.topMargin = dp(activity, 12);
            contentParams.bottomMargin = dp(activity, 12);
            root.addView(scroll, contentParams);

            final List<View> navViews = new ArrayList<>();
            final List<SliderHolder> navSliders = new ArrayList<>();
            final int[] focusedIndex = {0};

            if (showBrightnessControl) {
                SliderHolder brightness = createSliderRow(
                        activity,
                        brightnessTitle,
                        100,
                        getCurrentBrightnessPercent(activity),
                        5,
                        value -> value + "%",
                        value -> applyBrightness(activity, value)
                );
                addNavRow(activity, content, navViews, navSliders, focusedIndex, brightness.row, brightness);
            }
            if (showAudioGainControl) {
                SliderHolder gain = createSliderRow(
                        activity,
                        audioGainTitle,
                        10,
                        audioGain,
                        1,
                        value -> value + "x",
                        this::emitAudioGainChange
                );
                addNavRow(activity, content, navViews, navSliders, focusedIndex, gain.row, gain);
            }
            if (!navViews.isEmpty()) {
                content.addView(createDivider(activity));
            }

            boolean switchSectionEnded = false;
            for (int i = 0; i < labels.length; i++) {
                final int index = i;
                View view;
                if (isSwitch[i]) {
                    view = createSwitchRow(activity, labels[i], ids[i], checked[i]);
                } else {
                    if (i > 0 && isSwitch[i - 1] && !switchSectionEnded) {
                        content.addView(createDivider(activity));
                        switchSectionEnded = true;
                    }
                    TextView textView = new TextView(activity);
                    textView.setGravity(Gravity.CENTER);
                    textView.setText(labels[i]);
                    textView.setTextColor(danger[i] ? Color.rgb(255, 82, 82) : Color.WHITE);
                    textView.setTextSize(15);
                    textView.setSingleLine(false);
                    textView.setMinHeight(dp(activity, 42));
                    textView.setPadding(dp(activity, 24), dp(activity, 9), dp(activity, 24), dp(activity, 9));
                    textView.setOnClickListener(v -> {
                        resolveCurrentOption("select", index, ids[index]);
                        if (currentDialog != null && currentDialog.isShowing()) {
                            currentDialog.dismiss();
                        }
                    });
                    view = textView;
                }
                addNavRow(activity, content, navViews, navSliders, focusedIndex, view, null);
            }

            Dialog dialog = new Dialog(activity);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(root);

            currentDialog = dialog;
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setWindowAnimations(0);
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = WindowManager.LayoutParams.MATCH_PARENT;
                params.height = WindowManager.LayoutParams.MATCH_PARENT;
                params.dimAmount = 0.32f;
                params.gravity = Gravity.CENTER;
                window.setAttributes(params);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                    window.getAttributes().setBlurBehindRadius(dp(activity, 20));
                }
            }
            dialog.setOnCancelListener(d -> resolveCurrentOption("cancel", -1, ""));
            dialog.setOnDismissListener(d -> currentDialog = null);
            dialog.setOnShowListener(d -> {
                if (!navViews.isEmpty()) {
                    root.post(() -> {
                        focusedIndex[0] = 0;
                        navViews.get(0).requestFocus();
                    });
                }
            });
            dialog.setOnKeyListener((d, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN || navViews.isEmpty()) {
                    return false;
                }

                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusedIndex[0] = Math.min(focusedIndex[0] + 1, navViews.size() - 1);
                    navViews.get(focusedIndex[0]).requestFocus();
                    return true;
                }

                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    focusedIndex[0] = Math.max(focusedIndex[0] - 1, 0);
                    navViews.get(focusedIndex[0]).requestFocus();
                    return true;
                }

                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    SliderHolder slider = navSliders.get(focusedIndex[0]);
                    if (slider != null) {
                        int direction = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                        SeekBar bar = slider.bar;
                        bar.setProgress(Math.max(0, Math.min(bar.getMax(), bar.getProgress() + direction * slider.step)));
                    }
                    return true;
                }

                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                        || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                    if (navSliders.get(focusedIndex[0]) == null) {
                        navViews.get(focusedIndex[0]).performClick();
                    }
                    return true;
                }

                return false;
            });
            dialog.show();
        });
    }

    private int resolveInputType(int inputScope) {
        switch (inputScope) {
            case 1:
                return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;
            case 5:
                return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
            case 29:
                return InputType.TYPE_CLASS_NUMBER;
            case 31:
                return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
            case 32:
                return InputType.TYPE_CLASS_PHONE;
            default:
                return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        }
    }

    private void emitAudioGainChange(int gain) {
        WritableMap event = Arguments.createMap();
        event.putInt("value", Math.max(0, Math.min(10, gain)));
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("NativeInputDialogAudioGainChange", event);
    }

    @ReactMethod
    public void resetBrightness() {
        UiThreadUtil.runOnUiThread(() -> {
            Activity activity = getCurrentActivity();
            if (activity == null) {
                return;
            }
            setWindowBrightness(activity.getWindow(), WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE);
        });
    }

    private static class SliderHolder {
        final LinearLayout row;
        final SeekBar bar;
        final int step;

        SliderHolder(LinearLayout row, SeekBar bar, int step) {
            this.row = row;
            this.bar = bar;
            this.step = step;
        }
    }

    private SliderHolder createSliderRow(
            Context context,
            String title,
            int max,
            int initial,
            int step,
            IntFunction<String> formatter,
            IntConsumer onChange
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 24), dp(context, 4), dp(context, 16), dp(context, 4));
        row.setMinimumHeight(dp(context, 42));

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(15);
        row.addView(titleView, new LinearLayout.LayoutParams(
                dp(context, 64),
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        int value = Math.max(0, Math.min(max, initial));
        TextView valueView = new TextView(context);
        valueView.setText(formatter.apply(value));
        valueView.setTextColor(Color.WHITE);
        valueView.setTextSize(14);
        valueView.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

        SeekBar bar = new SeekBar(context);
        bar.setMax(max);
        bar.setProgress(value);
        bar.setFocusable(false);
        bar.setFocusableInTouchMode(false);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueView.setText(formatter.apply(progress));
                onChange.accept(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        row.addView(bar, new LinearLayout.LayoutParams(0, dp(context, 34), 1f));
        row.addView(valueView, new LinearLayout.LayoutParams(
                dp(context, 42),
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        return new SliderHolder(row, bar, step);
    }

    private void addNavRow(
            Context context,
            LinearLayout content,
            List<View> navViews,
            List<SliderHolder> navSliders,
            int[] focusedIndex,
            View view,
            SliderHolder slider
    ) {
        final int index = navViews.size();
        view.setFocusable(true);
        view.setFocusableInTouchMode(false);
        view.setClickable(true);
        view.setBackground(createOptionBackground(context));
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                focusedIndex[0] = index;
            }
        });
        content.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        navViews.add(view);
        navSliders.add(slider);
    }

    private int getCurrentBrightnessPercent(Activity activity) {
        float override = activity.getWindow().getAttributes().screenBrightness;
        if (override >= 0f) {
            return Math.round(override * 100f);
        }
        try {
            int system = Settings.System.getInt(
                    activity.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS
            );
            return Math.round(system * 100f / 255f);
        } catch (Settings.SettingNotFoundException e) {
            return 50;
        }
    }

    private void applyBrightness(Activity activity, int percent) {
        float value = Math.max(0.01f, Math.min(1f, percent / 100f));
        setWindowBrightness(activity.getWindow(), value);
        if (currentDialog != null) {
            setWindowBrightness(currentDialog.getWindow(), value);
        }
    }

    private void setWindowBrightness(Window window, float value) {
        if (window == null) {
            return;
        }
        WindowManager.LayoutParams params = window.getAttributes();
        params.screenBrightness = value;
        window.setAttributes(params);
    }

    private void emitSwitchChange(String id, boolean checked) {
        WritableMap event = Arguments.createMap();
        event.putString("id", id);
        event.putBoolean("checked", checked);
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("NativeInputDialogSwitchChange", event);
    }

    private View createSwitchRow(Context context, String label, String id, boolean initialChecked) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(context, 42));
        row.setPadding(dp(context, 24), dp(context, 4), dp(context, 16), dp(context, 4));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextColor(Color.WHITE);
        labelView.setTextSize(15);
        row.addView(labelView, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        Switch toggle = new Switch(context);
        toggle.setChecked(initialChecked);
        toggle.setFocusable(false);
        toggle.setFocusableInTouchMode(false);
        toggle.setClickable(false);
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        toggle.setThumbTintList(new ColorStateList(states, new int[]{
                Color.WHITE,
                Color.rgb(190, 190, 190)
        }));
        toggle.setTrackTintList(new ColorStateList(states, new int[]{
                Color.rgb(16, 124, 16),
                Color.argb(110, 255, 255, 255)
        }));
        row.addView(toggle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        row.setOnClickListener(v -> {
            boolean next = !toggle.isChecked();
            toggle.setChecked(next);
            emitSwitchChange(id, next);
        });
        return row;
    }

    private View createDivider(Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(Color.argb(40, 255, 255, 255));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(context, 1))
        );
        params.leftMargin = dp(context, 16);
        params.rightMargin = dp(context, 16);
        params.topMargin = dp(context, 4);
        params.bottomMargin = dp(context, 4);
        divider.setLayoutParams(params);
        return divider;
    }

    private StateListDrawable createOptionBackground(Context context) {
        StateListDrawable states = new StateListDrawable();

        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(Color.argb(72, 255, 255, 255));
        pressed.setCornerRadius(dp(context, 8));

        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.argb(46, 255, 255, 255));
        focused.setCornerRadius(dp(context, 8));

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.TRANSPARENT);
        normal.setCornerRadius(dp(context, 8));

        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        return states;
    }

    private void dismissCurrent(String action, String text) {
        resolveCurrent(action, text);
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        currentDialog = null;
        currentInput = null;
    }

    private void resolveCurrent(String action, String text) {
        if (currentPromise == null) {
            return;
        }
        Promise promise = currentPromise;
        currentPromise = null;
        WritableMap result = Arguments.createMap();
        result.putString("action", action);
        result.putString("text", text == null ? "" : text);
        promise.resolve(result);
    }

    private void resolveCurrentOption(String action, int index, String id) {
        if (currentPromise == null) {
            return;
        }
        Promise promise = currentPromise;
        currentPromise = null;
        WritableMap result = Arguments.createMap();
        result.putString("action", action);
        result.putInt("index", index);
        result.putString("id", id == null ? "" : id);
        promise.resolve(result);
    }

    private String getCurrentText() {
        if (currentInput == null) {
            return "";
        }
        return currentInput.getText().toString();
    }

    private String getString(ReadableMap map, String key, String fallback) {
        if (map != null && map.hasKey(key) && !map.isNull(key)) {
            return map.getString(key);
        }
        return fallback;
    }

    private int getInt(ReadableMap map, String key, int fallback) {
        if (map != null && map.hasKey(key) && !map.isNull(key)) {
            return map.getInt(key);
        }
        return fallback;
    }

    private boolean getBoolean(ReadableMap map, String key, boolean fallback) {
        if (map != null && map.hasKey(key) && !map.isNull(key)) {
            return map.getBoolean(key);
        }
        return fallback;
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
