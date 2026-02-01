package com.termux.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.browser.BrowserTab;

import java.util.List;

public class TabsAdapter extends RecyclerView.Adapter<TabsAdapter.TabViewHolder> {

    private final List<BrowserTab> tabs;
    private final OnTabClickListener listener;

    public interface OnTabClickListener {
        void onTabClick(int position);
        void onTabClose(int position);
    }

    public TabsAdapter(List<BrowserTab> tabs, OnTabClickListener listener) {
        this.tabs = tabs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TabViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_browser_tab, parent, false);
        return new TabViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TabViewHolder holder, int position) {
        BrowserTab tab = tabs.get(position);
        holder.bind(tab, position);
    }

    @Override
    public int getItemCount() {
        return tabs.size();
    }

    class TabViewHolder extends RecyclerView.ViewHolder {
        private final TextView tabTitle;
        private final TextView tabUrl;
        private final ImageButton closeButton;
        private final View container;

        TabViewHolder(@NonNull View itemView) {
            super(itemView);
            tabTitle = itemView.findViewById(R.id.tab_title);
            tabUrl = itemView.findViewById(R.id.tab_url);
            closeButton = itemView.findViewById(R.id.btn_close_tab);
            container = itemView.findViewById(R.id.tab_container);
        }

        void bind(BrowserTab tab, int position) {
            tabTitle.setText(tab.getDisplayTitle());
            tabUrl.setText(tab.getUrl());

            container.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTabClick(position);
                }
            });

            closeButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTabClose(position);
                }
            });

            // Show/hide close button based on tab count
            closeButton.setVisibility(tabs.size() > 1 ? View.VISIBLE : View.GONE);
        }
    }
}
