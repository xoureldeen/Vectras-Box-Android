package com.xoureldeen.vectrasbox;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.xoureldeen.vectrasbox.data.MachineStore;
import com.xoureldeen.vectrasbox.data.MachineStore.Machine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class MachineEditorViewModel extends AndroidViewModel {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final MutableLiveData<SaveState> state = new MutableLiveData<>(new SaveState(false, 0, -1, null, null));

    public MachineEditorViewModel(@NonNull Application application) { super(application); }

    public LiveData<SaveState> state() { return state; }
    public boolean isSaving() { return state.getValue() != null && state.getValue().busy; }
    public void dismissError() { state.setValue(new SaveState(false, 0, -1, null, null)); }

    public void loadConfiguration(Uri uri) {
        if (isSaving()) return;
        state.setValue(new SaveState(true, 0, -1, null, null));
        worker.execute(() -> {
            try (InputStream input = getApplication().getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("Could not open the configuration file.");
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 2 * 1024 * 1024)
                        throw new IOException("Configuration file is too large.");
                    output.write(buffer, 0, count);
                }
                String config = output.toString(StandardCharsets.UTF_8.name());
                MachineStore.validateConfig(config);
                state.postValue(new SaveState(config));
            } catch (Exception error) {
                state.postValue(new SaveState(false, 0, -1, null, error.getMessage()));
            }
        });
    }

    public void save(String id, String name, String config, int diskGb, Uri disk,
                     Uri iso, Uri floppy, Uri icon) {
        if (isSaving()) return;
        state.setValue(new SaveState(true, 0, -1, null, null));
        worker.execute(() -> {
            try {
                MachineStore store = new MachineStore(getApplication());
                Machine saved;
                if (id == null) {
                    long[] lastProgress = {0};
                    saved = store.create(name, config, diskGb, disk, iso, floppy, icon, (copied, total) -> {
                        long now = android.os.SystemClock.elapsedRealtime();
                        if (now - lastProgress[0] >= 150 || copied == total) {
                            lastProgress[0] = now;
                            state.postValue(new SaveState(true, copied, total, null, null));
                        }
                    });
                } else {
                    store.saveConfig(id, config);
                    store.rename(id, name);
                    if (icon != null) store.setIcon(id, icon);
                    saved = store.get(id);
                }
                state.postValue(new SaveState(false, 0, -1, saved, null));
            } catch (Exception error) {
                state.postValue(new SaveState(false, 0, -1, null, error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage()));
            }
        });
    }

    @Override protected void onCleared() { worker.shutdown(); }

    public static final class SaveState {
        public final boolean busy;
        public final long copied, total;
        public final Machine machine;
        public final String error;
        public final String configuration;
        SaveState(boolean busy, long copied, long total, Machine machine, String error) {
            this.busy = busy;
            this.copied = copied;
            this.total = total;
            this.machine = machine;
            this.error = error;
            this.configuration = null;
        }
        SaveState(String configuration) {
            this.busy = false;
            this.copied = 0;
            this.total = -1;
            this.machine = null;
            this.error = null;
            this.configuration = configuration;
        }
    }
}
