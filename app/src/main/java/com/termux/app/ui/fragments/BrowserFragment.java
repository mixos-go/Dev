package com.termux.app.ui.fragments;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.browser.BrowserTab;
import com.termux.app.ui.adapters.TabsAdapter;

import java.util.ArrayList;
import java.util.List;

public class BrowserFragment extends Fragment implements TabsAdapter.OnTabClickListener {

    private WebView webView;
    private EditText urlInput;
    private ImageButton btnBack;
    private ImageButton btnForward;
    private ImageButton btnRefresh;
    private FrameLayout btnTabs;
    private TextView tabCount;
    private ProgressBar loadingProgress;
    private LinearLayout emptyState;
    private FrameLayout tabsModal;
    private RecyclerView tabsList;
    private Button btnNewTab;

    private List<BrowserTab> tabs = new ArrayList<>();
    private int currentTabIndex = 0;
    private TabsAdapter tabsAdapter;

    private static final String DEFAULT_URL = "https://www.google.com";
    private static final String LOCALHOST_PREFIX = "http://localhost:";

    public static BrowserFragment newInstance() {
        return new BrowserFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_browser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupWebView();
        setupListeners();
        setupTabs();

        // Create initial tab
        createNewTab(DEFAULT_URL);
    }

    private void initViews(View view) {
        webView = view.findViewById(R.id.webview);
        urlInput = view.findViewById(R.id.url_input);
        btnBack = view.findViewById(R.id.btn_back);
        btnForward = view.findViewById(R.id.btn_forward);
        btnRefresh = view.findViewById(R.id.btn_refresh);
        btnTabs = view.findViewById(R.id.btn_tabs);
        tabCount = view.findViewById(R.id.tab_count);
        loadingProgress = view.findViewById(R.id.loading_progress);
        emptyState = view.findViewById(R.id.empty_state);
        tabsModal = view.findViewById(R.id.tabs_modal);
        tabsList = view.findViewById(R.id.tabs_list);
        btnNewTab = view.findViewById(R.id.btn_new_tab);

        // Quick localhost buttons
        TextView quick3000 = view.findViewById(R.id.quick_localhost_3000);
        TextView quick8080 = view.findViewById(R.id.quick_localhost_8080);

        if (quick3000 != null) {
            quick3000.setOnClickListener(v -> loadUrl(LOCALHOST_PREFIX + "3000"));
        }
        if (quick8080 != null) {
            quick8080.setOnClickListener(v -> loadUrl(LOCALHOST_PREFIX + "8080"));
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                urlInput.setText(url);
                loadingProgress.setVisibility(View.VISIBLE);
                emptyState.setVisibility(View.GONE);
                updateNavigationButtons();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loadingProgress.setVisibility(View.GONE);
                updateCurrentTabInfo(url, view.getTitle());
                updateNavigationButtons();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                loadingProgress.setProgress(newProgress);
                if (newProgress == 100) {
                    loadingProgress.setVisibility(View.GONE);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                updateCurrentTabInfo(view.getUrl(), title);
            }
        });
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            }
        });

        btnForward.setOnClickListener(v -> {
            if (webView.canGoForward()) {
                webView.goForward();
            }
        });

        btnRefresh.setOnClickListener(v -> webView.reload());

        btnTabs.setOnClickListener(v -> toggleTabsModal());

        btnNewTab.setOnClickListener(v -> {
            createNewTab(DEFAULT_URL);
            hideTabsModal();
        });

        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || 
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String url = urlInput.getText().toString().trim();
                loadUrl(url);
                return true;
            }
            return false;
        });

        tabsModal.setOnClickListener(v -> hideTabsModal());
    }

    private void setupTabs() {
        tabsAdapter = new TabsAdapter(tabs, this);
        tabsList.setLayoutManager(new LinearLayoutManager(getContext()));
        tabsList.setAdapter(tabsAdapter);
    }

    private void createNewTab(String url) {
        BrowserTab tab = new BrowserTab(url, "New Tab");
        tabs.add(tab);
        currentTabIndex = tabs.size() - 1;
        updateTabCount();
        tabsAdapter.notifyDataSetChanged();
        loadUrl(url);
    }

    private void loadUrl(String url) {
        if (url == null || url.isEmpty()) return;

        // Add protocol if missing
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            // Check if it's localhost
            if (url.startsWith("localhost")) {
                url = "http://" + url;
            } else if (url.contains(".") || url.startsWith("192.168") || url.startsWith("10.")) {
                url = "https://" + url;
            } else {
                // Treat as search query
                url = "https://www.google.com/search?q=" + url;
            }
        }

        emptyState.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(url);
    }

    private void updateNavigationButtons() {
        btnBack.setAlpha(webView.canGoBack() ? 1.0f : 0.3f);
        btnForward.setAlpha(webView.canGoForward() ? 1.0f : 0.3f);
    }

    private void updateTabCount() {
        tabCount.setText(String.valueOf(tabs.size()));
    }

    private void updateCurrentTabInfo(String url, String title) {
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
            BrowserTab tab = tabs.get(currentTabIndex);
            tab.setUrl(url);
            tab.setTitle(title != null ? title : url);
            tabsAdapter.notifyItemChanged(currentTabIndex);
        }
    }

    private void toggleTabsModal() {
        if (tabsModal.getVisibility() == View.VISIBLE) {
            hideTabsModal();
        } else {
            showTabsModal();
        }
    }

    private void showTabsModal() {
        tabsModal.setVisibility(View.VISIBLE);
        tabsModal.setAlpha(0f);
        tabsModal.animate().alpha(1f).setDuration(200).start();
    }

    private void hideTabsModal() {
        tabsModal.animate().alpha(0f).setDuration(200).withEndAction(() ->
            tabsModal.setVisibility(View.GONE)
        ).start();
    }

    @Override
    public void onTabClick(int position) {
        currentTabIndex = position;
        BrowserTab tab = tabs.get(position);
        loadUrl(tab.getUrl());
        hideTabsModal();
    }

    @Override
    public void onTabClose(int position) {
        if (tabs.size() <= 1) return;

        tabs.remove(position);
        tabsAdapter.notifyItemRemoved(position);
        updateTabCount();

        if (currentTabIndex >= tabs.size()) {
            currentTabIndex = tabs.size() - 1;
        }
        if (currentTabIndex >= 0 && currentTabIndex < tabs.size()) {
            BrowserTab tab = tabs.get(currentTabIndex);
            loadUrl(tab.getUrl());
        }
    }

    public boolean handleBackPress() {
        if (tabsModal.getVisibility() == View.VISIBLE) {
            hideTabsModal();
            return true;
        }
        if (webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
