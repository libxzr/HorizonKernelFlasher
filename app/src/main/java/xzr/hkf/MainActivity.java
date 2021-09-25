package xzr.hkf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    static final boolean DEBUG = false;

    TextView logView;
    ScrollView scrollView;

    enum status {
        flashing,
        flashing_done,
        error,
        normal
    }

    static status cur_status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scrollView = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextIsSelectable(true);
        scrollView.addView(logView);
        setContentView(scrollView);

        flash_new();
    }

    void update_title() {
        runOnUiThread(() -> {
            switch (cur_status) {
                case error:
                    setTitle(R.string.failed);
                    break;
                case flashing:
                    setTitle(R.string.flashing);
                    break;
                case flashing_done:
                    setTitle(R.string.flashing_done);
                    break;
                default:
                    setTitle(R.string.app_name);
            }
        });
    }

    void flash_new() {
        if (cur_status == MainActivity.status.flashing) {
            Toast.makeText(this, R.string.task_running, Toast.LENGTH_SHORT).show();
            return;
        }

        logView.setText("");
        cur_status = status.normal;
        update_title();
        Toast.makeText(this, R.string.please_select_kzip, Toast.LENGTH_LONG).show();
        runWithFilePath(this, new Worker(this));
    }

    @Override
    public void onBackPressed() {
        if (cur_status != status.flashing)
            super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.about) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.about)
                    .setMessage(R.string.about_msg)
                    .setPositiveButton(R.string.ok, null)
                    .setNegativeButton("Github", (dialog1, which1) -> MainActivity.this.startActivity(new Intent() {{
                        setAction(Intent.ACTION_VIEW);
                        setData(Uri.parse("https://github.com/libxzr/HorizonKernelFlasher"));
                    }})).create().show();
        } else if (item.getItemId() == R.id.flash_new) {
            flash_new();
        }
        return true;
    }

    public static void _appendLog(String log, Activity activity) {
        activity.runOnUiThread(() -> {
            ((MainActivity) activity).logView.append(log + "\n");
            ((MainActivity) activity).scrollView.fullScroll(ScrollView.FOCUS_DOWN);
        });
    }

    public static void appendLog(String log, Activity activity) {
        if (DEBUG) {
            _appendLog(log, activity);
            return;
        }
        if (!log.startsWith("ui_print"))
            return;
        log = log.replace("ui_print", "");
        _appendLog(log, activity);
    }

    static class fileWorker extends Thread {
        public Uri uri;
    }

    private static fileWorker file_worker;

    public static void runWithFilePath(Activity activity, fileWorker what) {
        MainActivity.file_worker = what;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        activity.startActivityForResult(intent, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            file_worker.uri = data.getData();
            if (file_worker != null) {
                file_worker.start();
                file_worker = null;
            }
        }
    }
}