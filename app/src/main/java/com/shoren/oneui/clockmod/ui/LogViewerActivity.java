package com.shoren.oneui.clockmod.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.shoren.oneui.clockmod.R;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 日志查看界面：实时读取系统 Logcat 中关于本模块的运行日志
 */
public class LogViewerActivity extends AppCompatActivity {

    private TextView tvLogContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("运行日志调试");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvLogContent = findViewById(R.id.tv_log_content);
        Button btnRefresh = findViewById(R.id.btn_refresh_log);

        btnRefresh.setOnClickListener(v -> loadLogs());

        loadLogs();
    }

    private void loadLogs() {
        new Thread(() -> {
            StringBuilder logBuilder = new StringBuilder();
            try {
                // 读取包含 OneUIClockMod_Debug 标签的日志
                Process process = Runtime.getRuntime().exec("logcat -d -s OneUIClockMod_Debug:V Xposed:D *:S");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    logBuilder.append(line).append("\n");
                }
                if (logBuilder.length() == 0) {
                    logBuilder.append("暂无相关日志，请确保模块已激活且 SystemUI 已触发加载。");
                }
            } catch (Exception e) {
                logBuilder.append("获取日志失败: ").append(e.getMessage());
            }

            runOnUiThread(() -> tvLogContent.setText(logBuilder.toString()));
        }).start();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}