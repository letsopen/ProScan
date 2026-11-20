package com.example.proscan;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proscan.adapter.HistoryAdapter;
import com.example.proscan.db.HistoryDbHelper;
import com.example.proscan.db.HistoryItem;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private TextView emptyView;
    private List<HistoryItem> historyList;
    private HistoryDbHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_history);
        dbHelper = HistoryDbHelper.getInstance(this);
        historyList = new ArrayList<>();

        recyclerView = findViewById(R.id.recyclerView);
        emptyView = findViewById(R.id.emptyView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new HistoryAdapter(historyList, new HistoryAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(HistoryItem item) {
                // 返回主界面并传递内容
                Intent resultIntent = new Intent();
                resultIntent.putExtra("content", item.getContent());
                setResult(RESULT_OK, resultIntent);
                finish();
            }

            @Override
            public void onItemLongClick(HistoryItem item) {
                // 长按复制内容
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("扫描内容", item.getContent());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(HistoryActivity.this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeleteClick(HistoryItem item) {
                // 点击删除按钮
                new AlertDialog.Builder(HistoryActivity.this)
                    .setTitle("删除记录")
                    .setMessage("确定要删除这条记录吗？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        dbHelper.deleteHistoryItem(item.getId());
                        historyList.remove(item);
                        adapter.notifyDataSetChanged();
                        updateEmptyState();
                        Toast.makeText(HistoryActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            }
        });
        recyclerView.setAdapter(adapter);
        applyHistoryInsets();

        // 设置删除全部按钮点击事件
        findViewById(R.id.buttonClearAll).setOnClickListener(v -> {
            if (historyList.isEmpty()) {
                Toast.makeText(this, "没有历史记录", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(this)
                .setTitle("删除全部")
                .setMessage("确定要删除所有历史记录吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    dbHelper.clearAllHistory();
                    historyList.clear();
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                    Toast.makeText(HistoryActivity.this, "已清空历史记录", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
        });

        loadHistory();
    }

    private void applyHistoryInsets() {
        final int headerTop = findViewById(R.id.history_header).getPaddingTop();
        final int listBottom = recyclerView.getPaddingBottom();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.history_root), (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            android.view.View header = findViewById(R.id.history_header);
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top, header.getPaddingRight(), header.getPaddingBottom());
            recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                    recyclerView.getPaddingRight(), listBottom + bars.bottom);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });
    }

    private void loadHistory() {
        // 数据库读取已移至后台线程，回调后再刷新列表
        dbHelper.getAllHistoryItems(items -> {
            historyList.clear();
            historyList.addAll(items);
            if (adapter != null) {
                adapter.notifyDataSetChanged();
                updateEmptyState();
            }
        });
    }

    private void updateEmptyState() {
        boolean empty = historyList.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
