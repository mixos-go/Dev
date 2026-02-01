package com.termux.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.ui.adapters.MainPagerAdapter;
import com.termux.app.ui.fragments.BrowserFragment;
import com.termux.app.ui.fragments.HomeFragment;
import com.termux.app.ui.fragments.TerminalFragment;
import com.termux.app.ui.modals.DevModalFragment;
import com.termux.app.ui.modals.SettingsModalFragment;
import com.termux.app.ui.modals.WorkspaceModalFragment;

public class MainActivity extends AppCompatActivity implements TerminalFragment.OnTerminalActionListener {

    private ViewPager2 mainViewPager;
    private MainPagerAdapter pagerAdapter;
    private LinearLayout floatingNavContainer;
    private FrameLayout settingsFab;
    private FrameLayout modalContainer;
    
    private FrameLayout navDev;
    private FrameLayout navHome;
    private FrameLayout navWorkspace;
    private ImageView navDevIcon;
    private ImageView navHomeIcon;
    private ImageView navWorkspaceIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupViewPager();
        setupNavigation();
        setupListeners();
    }

    private void initViews() {
        mainViewPager = findViewById(R.id.main_view_pager);
        floatingNavContainer = findViewById(R.id.floating_nav_container);
        settingsFab = findViewById(R.id.settings_fab);
        modalContainer = findViewById(R.id.modal_container);
        
        navDev = findViewById(R.id.nav_dev);
        navHome = findViewById(R.id.nav_home);
        navWorkspace = findViewById(R.id.nav_workspace);
        navDevIcon = findViewById(R.id.nav_dev_icon);
        navHomeIcon = findViewById(R.id.nav_home_icon);
        navWorkspaceIcon = findViewById(R.id.nav_workspace_icon);
    }

    private void setupViewPager() {
        pagerAdapter = new MainPagerAdapter(this);
        mainViewPager.setAdapter(pagerAdapter);
        
        // Start on Home (center) page
        mainViewPager.setCurrentItem(MainPagerAdapter.PAGE_HOME, false);
        
        // Page change listener
        mainViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateUIForPage(position);
            }
        });
    }

    private void setupNavigation() {
        updateNavSelection(MainPagerAdapter.PAGE_HOME);
    }

    private void setupListeners() {
        navDev.setOnClickListener(v -> showDevModal());
        navHome.setOnClickListener(v -> mainViewPager.setCurrentItem(MainPagerAdapter.PAGE_HOME, true));
        navWorkspace.setOnClickListener(v -> showWorkspaceModal());
        settingsFab.setOnClickListener(v -> showSettingsModal());
    }

    private void updateUIForPage(int position) {
        // Update navigation icons
        updateNavSelection(position);
        
        // Show/hide floating navigation and settings based on page
        boolean isHomePage = position == MainPagerAdapter.PAGE_HOME;
        floatingNavContainer.setVisibility(isHomePage ? View.VISIBLE : View.GONE);
        settingsFab.setVisibility(isHomePage ? View.VISIBLE : View.GONE);
        
        // Animate navigation visibility
        if (isHomePage) {
            floatingNavContainer.setAlpha(0f);
            floatingNavContainer.animate().alpha(1f).setDuration(200).start();
            settingsFab.setAlpha(0f);
            settingsFab.animate().alpha(1f).setDuration(200).start();
        }
    }

    private void updateNavSelection(int position) {
        int activeColor = getColor(R.color.nav_icon_active);
        int inactiveColor = getColor(R.color.nav_icon_inactive);
        
        navDevIcon.setColorFilter(inactiveColor);
        navHomeIcon.setColorFilter(position == MainPagerAdapter.PAGE_HOME ? activeColor : inactiveColor);
        navWorkspaceIcon.setColorFilter(inactiveColor);
    }

    private void showDevModal() {
        DevModalFragment modal = DevModalFragment.newInstance();
        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.modal_container, modal)
            .addToBackStack("dev_modal")
            .commit();
        modalContainer.setVisibility(View.VISIBLE);
    }

    private void showWorkspaceModal() {
        WorkspaceModalFragment modal = WorkspaceModalFragment.newInstance();
        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.modal_container, modal)
            .addToBackStack("workspace_modal")
            .commit();
        modalContainer.setVisibility(View.VISIBLE);
    }

    private void showSettingsModal() {
        SettingsModalFragment modal = SettingsModalFragment.newInstance();
        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.modal_container, modal)
            .addToBackStack("settings_modal")
            .commit();
        modalContainer.setVisibility(View.VISIBLE);
    }

    public void hideModal() {
        getSupportFragmentManager().popBackStack();
        modalContainer.setVisibility(View.GONE);
    }

    public void navigateToTerminal() {
        mainViewPager.setCurrentItem(MainPagerAdapter.PAGE_TERMINAL, true);
    }

    public void navigateToBrowser() {
        mainViewPager.setCurrentItem(MainPagerAdapter.PAGE_BROWSER, true);
    }

    public void navigateToBrowserWithUrl(String url) {
        mainViewPager.setCurrentItem(MainPagerAdapter.PAGE_BROWSER, true);
        BrowserFragment browserFragment = pagerAdapter.getBrowserFragment();
        if (browserFragment != null) {
            // Load URL in browser
        }
    }

    public void executeTerminalCommand(String command) {
        TerminalFragment terminalFragment = pagerAdapter.getTerminalFragment();
        if (terminalFragment != null) {
            terminalFragment.executeCommand(command);
        }
    }

    @Override
    public void onOpenSettings() {
        showSettingsModal();
    }

    @Override
    public void onBackPressed() {
        // Check if modal is showing
        if (modalContainer.getVisibility() == View.VISIBLE) {
            hideModal();
            return;
        }

        // Check browser back navigation
        int currentPage = mainViewPager.getCurrentItem();
        if (currentPage == MainPagerAdapter.PAGE_BROWSER) {
            BrowserFragment browserFragment = pagerAdapter.getBrowserFragment();
            if (browserFragment != null && browserFragment.handleBackPress()) {
                return;
            }
        }

        // If not on home, go to home
        if (currentPage != MainPagerAdapter.PAGE_HOME) {
            mainViewPager.setCurrentItem(MainPagerAdapter.PAGE_HOME, true);
            return;
        }

        super.onBackPressed();
    }
}
