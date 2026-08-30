package com.shoren.oneui.clockmod.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.shoren.oneui.clockmod.R;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

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
        findViewById(R.id.btn_refresh_log).setOnClickListener(v -> loadLogs());
        loadLogs();
    }

    private void loadLogs() {
        tvLogContent.setText("正在请求 Root 权限并读取系统级日志...\n(如果卡在此处，请去面具/KSU允许授权)");
        new Thread(() -> {
            StringBuilder logBuilder = new StringBuilder();
            try {
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                os.writeBytes("logcat -d -s OneUIClockMod_Debug:V OneUIClockMod_LayoutHook:V Xposed:D *:S\n");
                os.writeBytes("exit\n");
                os.flush();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    logBuilder.append(line).append("\n");
                }
                if (logBuilder.length() == 0) {
                    logBuilder.append("没有抓取到任何模块运行日志。\n\n请检查:\n1. 模块是否在 LSPosed 中激活？\n2. 是否勾选了 SystemUI？\n3. 是否已重启手机？");
                }
            } catch (Exception e) {
                logBuilder.append("日志抓取失败，缺少 Root 权限: ").append(e.getMessage());
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
