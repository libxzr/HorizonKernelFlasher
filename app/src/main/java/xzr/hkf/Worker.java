package xzr.hkf;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import xzr.hkf.utils.AssetsUtil;

public class Worker extends MainActivity.fileWorker {
    Activity activity;
    String file_path;
    String binary_path;
    boolean is_error;

    public Worker(Activity activity) {
        this.activity = activity;
    }

    public void run() {
        MainActivity.cur_status = MainActivity.status.flashing;
        ((MainActivity) activity).update_title();
        is_error = false;
        file_path = activity.getFilesDir().getAbsolutePath() + "/" + DocumentFile.fromSingleUri(activity, uri).getName();
        binary_path = activity.getFilesDir().getAbsolutePath() + "/META-INF/com/google/android/update-binary";

        try {
            cleanup();
        } catch (IOException ioException) {
            is_error = true;
        }
        if (is_error) {
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_cleanup), activity);
            MainActivity.cur_status = MainActivity.status.error;
            ((MainActivity) activity).update_title();
            return;
        }

        if (!rootAvailable()) {
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_flash_root), activity);
            MainActivity.cur_status = MainActivity.status.error;
            ((MainActivity) activity).update_title();
            return;
        }

        try {
            copy();
        } catch (IOException ioException) {
            is_error = true;
        }
        if (!is_error)
            is_error = !new File(file_path).exists();
        if (is_error) {
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_copy), activity);
            MainActivity.cur_status = MainActivity.status.error;
            ((MainActivity) activity).update_title();
            return;
        }

        try {
            getBinary();
        } catch (IOException ioException) {
            is_error = true;
        }
        if (is_error) {
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_get_exe), activity);
            MainActivity.cur_status = MainActivity.status.error;
            ((MainActivity) activity).update_title();
            return;
        }

        try {
            patch();
        } catch (IOException ignored) {
        }

        try {
            flash(activity);
        } catch (IOException ioException) {
            is_error = true;
        }
        if (is_error) {
            MainActivity._appendLog(activity.getResources().getString(R.string.unable_flash_error), activity);
            MainActivity.cur_status = MainActivity.status.error;
            ((MainActivity) activity).update_title();
            return;
        }
        activity.runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.reboot_complete_title)
                    .setMessage(R.string.reboot_complete_msg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        try {
                            reboot();
                        } catch (IOException e) {
                            Toast.makeText(activity, R.string.failed_reboot, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(R.string.no, null)
                    .create().show();
        });
        MainActivity.cur_status = MainActivity.status.flashing_done;
        ((MainActivity) activity).update_title();
    }

    boolean rootAvailable() {
        try {
            String ret = runWithNewProcessReturn(true, "id");
            return ret != null && ret.contains("root");
        } catch (IOException e) {
            return false;
        }
    }

    void copy() throws IOException {
        InputStream inputStream = activity.getContentResolver().openInputStream(uri);
        FileOutputStream fileOutputStream = new FileOutputStream(new File(file_path));
        byte[] buffer = new byte[1024];
        int count = 0;
        while ((count = inputStream.read(buffer)) != -1) {
            fileOutputStream.write(buffer, 0, count);
        }
        fileOutputStream.flush();
        inputStream.close();
        fileOutputStream.close();
    }

    void getBinary() throws IOException {
        runWithNewProcessNoReturn(false, "unzip \"" + file_path + "\" \"*/update-binary\" -d " + activity.getFilesDir().getAbsolutePath());
        if (!new File(binary_path).exists())
            throw new IOException();
    }

    void patch() throws IOException {
        final String mkbootfs_path = activity.getFilesDir().getAbsolutePath() + "/mkbootfs";
        AssetsUtil.exportFiles(activity, "mkbootfs", mkbootfs_path);

        runWithNewProcessNoReturn(false, "sed -i '/$BB chmod -R 755 tools bin;/i cp -f " + mkbootfs_path + " $AKHOME/tools;' " + binary_path);
    }

    void flash(Activity activity) throws IOException {
        Process process = new ProcessBuilder("su").redirectErrorStream(true).start();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(process.getOutputStream());
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        outputStreamWriter.write("export POSTINSTALL=" + activity.getFilesDir().getAbsolutePath() + "\n");
        outputStreamWriter.write("sh " + (MainActivity.DEBUG ? "-x " : "") + binary_path + " 3 1 \"" + file_path + "\"&& touch " + activity.getFilesDir().getAbsolutePath() + "/done\nexit\n");
        outputStreamWriter.flush();
        String line;
        while ((line = bufferedReader.readLine()) != null)
            MainActivity.appendLog(line, activity);

        bufferedReader.close();
        outputStreamWriter.close();
        process.destroy();

        if (!new File(activity.getFilesDir().getAbsolutePath() + "/done").exists())
            throw new IOException();
    }

    void cleanup() throws IOException {
        runWithNewProcessNoReturn(false, "rm -rf " + activity.getFilesDir().getAbsolutePath() + "/*");
    }

    void reboot() throws IOException {
        runWithNewProcessNoReturn(true, "svc power reboot");
    }

    void runWithNewProcessNoReturn(boolean su, String cmd) throws IOException {
        runWithNewProcessReturn(su, cmd);
    }

    String runWithNewProcessReturn(boolean su, String cmd) throws IOException {
        Process process = null;
        if (su) {
            process = new ProcessBuilder("su").redirectErrorStream(true).start();
        } else {
            process = new ProcessBuilder("sh").redirectErrorStream(true).start();
        }
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter((process.getOutputStream()));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        outputStreamWriter.write(cmd + "\n");
        outputStreamWriter.write("exit\n");
        outputStreamWriter.flush();
        String tmp;
        StringBuilder ret = new StringBuilder();
        while ((tmp = bufferedReader.readLine()) != null) {
            ret.append(tmp);
            ret.append("\n");
        }
        outputStreamWriter.close();
        bufferedReader.close();
        process.destroy();
        return ret.toString();
    }

}
