package com.xoureldeen.vectrasbox;

import android.app.Activity;
import android.text.util.Linkify;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LegalTextDialogs {
    private static final ExecutorService READER = Executors.newSingleThreadExecutor();

    private LegalTextDialogs() { }

    static void showAsset(Activity activity, String title, String path, String header) {
        READER.execute(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    activity.getAssets().open(path), StandardCharsets.UTF_8))) {
                StringBuilder text = new StringBuilder(header);
                String line;
                while ((line = reader.readLine()) != null) text.append(line).append('\n');
                List<String> pages = splitPages(text.toString());
                activity.runOnUiThread(() -> showPage(activity, title, pages, 0));
            } catch (IOException error) {
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        Toast.makeText(activity, R.string.unable_to_load_legal_text, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    static List<String> splitPages(String text) {
        List<String> pages = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + 32000, text.length());
            if (end < text.length()) {
                int newline = text.lastIndexOf('\n', end - 1);
                if (newline > start) end = newline + 1;
                else if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
            }
            pages.add(text.substring(start, end));
            start = end;
        }
        if (pages.isEmpty()) pages.add("");
        return pages;
    }

    private static void showPage(Activity activity, String title, List<String> pages, int index) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        String pageTitle = pages.size() == 1 ? title
                : activity.getString(R.string.license_page_title, title, index + 1, pages.size());
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(pageTitle)
                .setMessage(pages.get(index));
        if (pages.size() == 1) builder.setPositiveButton(android.R.string.ok, null);
        else {
            builder.setNeutralButton(android.R.string.ok, null);
            if (index > 0) builder.setNegativeButton(R.string.previous_license_page,
                    (dialog, which) -> showPage(activity, title, pages, index - 1));
            if (index + 1 < pages.size()) builder.setPositiveButton(R.string.next_license_page,
                    (dialog, which) -> showPage(activity, title, pages, index + 1));
        }
        AlertDialog dialog = builder.show();
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextIsSelectable(true);
            Linkify.addLinks(message, Linkify.WEB_URLS);
        }
    }
}
