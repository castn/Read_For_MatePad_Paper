package com.jack.bookshelf.view.dialog;

import static com.jack.bookshelf.utils.StringUtils.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.jack.bookshelf.R;

/**
 * Alert Dialog
 * Adapt to Huawei MatePad Paper
 * Edited by Jack251970
 */

public class AlertDialog extends PopupWindow{
    private final Context context;
    private TextView tvTitle;
    private TextView tvMessage;
    private TextView tvAppendMessage;
    private TextView tvNegativeButton;
    private TextView tvPositiveButton;
    private OnItemClickListener itemClick;

    public static final int NO_APPEND_MESSAGE = 0, ONLY_CENTER_TITLE = 1, ALL = 2;

    public static AlertDialog builder(Context context) { return new AlertDialog(context); }

    public AlertDialog setType(int type) {
        switch (type) {
            case ONLY_CENTER_TITLE:
                tvTitle.setGravity(Gravity.CENTER);
                tvMessage.setVisibility(View.GONE);
                break;
            case ALL:
                tvAppendMessage.setVisibility(View.VISIBLE);
                break;
            case NO_APPEND_MESSAGE:
                return this;
        }
        return this;
    }

    public AlertDialog setTitle(int strId) {
        return setTitle(getString(strId));
    }

    public AlertDialog setTitle(String title) {
        tvTitle.setText(title);
        return this;
    }

    public AlertDialog setMessage(int strId) {
        return setMessage(getString(strId));
    }

    public AlertDialog setMessage(String message) {
        tvMessage.setText(message);
        return this;
    }

    public AlertDialog setAppendMessage(int strId) {
        return setAppendMessage(getString(strId));
    }

    public AlertDialog setAppendMessage(String appendMessage) {
        tvAppendMessage.setText(appendMessage);
        return this;
    }

    public AlertDialog setNegativeButton (int strId) {
        return setNegativeButton(getString(strId));
    }

    public AlertDialog setNegativeButton (String text) {
        tvNegativeButton.setText(text);
        tvNegativeButton.setOnClickListener(v -> {
            dismiss();
            itemClick.forNegativeButton();
        });
        return this;
    }

    public AlertDialog setPositiveButton (int strId) {
        return setPositiveButton(getString(strId));
    }

    public AlertDialog setPositiveButton (String text) {
        tvPositiveButton.setText(text);
        tvPositiveButton.setOnClickListener(v -> {
            dismiss();
            itemClick.forPositiveButton();
        });
        return this;
    }

    public AlertDialog setOnclick(@NonNull OnItemClickListener itemClick) {
        this.itemClick = itemClick;
        return this;
    }

    @SuppressLint({"InflateParams"})
    public AlertDialog(Context context) {
        super(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        this.context = context;
        View view = LayoutInflater.from(context).inflate(R.layout.alert_dialog_paper, null);
        this.setContentView(view);
        bindView(view);
        setFocusable(true);
        setTouchable(true);
    }

    private void bindView(View view) {
        tvTitle = view.findViewById(R.id.tv_alert_dialog_title);
        tvMessage = view.findViewById(R.id.tv_alert_dialog_message);
        tvAppendMessage = view.findViewById(R.id.tv_alert_dialog_append_message);
        tvNegativeButton = view.findViewById(R.id.tv_alert_dialog_negative_button);
        tvPositiveButton = view.findViewById(R.id.tv_alert_dialog_positive_button);
    }

    public void show(View mainView) {
        showAtLocation(mainView, Gravity.CENTER, 0, 0);
    }

    public interface OnItemClickListener {
        void forNegativeButton();

        void forPositiveButton();
    }
}