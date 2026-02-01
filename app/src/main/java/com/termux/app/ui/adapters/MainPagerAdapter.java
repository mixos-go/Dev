package com.termux.app.ui.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.termux.app.ui.fragments.BrowserFragment;
import com.termux.app.ui.fragments.HomeFragment;
import com.termux.app.ui.fragments.TerminalFragment;

public class MainPagerAdapter extends FragmentStateAdapter {

    public static final int PAGE_TERMINAL = 0;
    public static final int PAGE_HOME = 1;
    public static final int PAGE_BROWSER = 2;
    public static final int PAGE_COUNT = 3;

    private TerminalFragment terminalFragment;
    private HomeFragment homeFragment;
    private BrowserFragment browserFragment;

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case PAGE_TERMINAL:
                terminalFragment = TerminalFragment.newInstance();
                return terminalFragment;
            case PAGE_HOME:
                homeFragment = HomeFragment.newInstance();
                return homeFragment;
            case PAGE_BROWSER:
                browserFragment = BrowserFragment.newInstance();
                return browserFragment;
            default:
                return HomeFragment.newInstance();
        }
    }

    @Override
    public int getItemCount() {
        return PAGE_COUNT;
    }

    public TerminalFragment getTerminalFragment() {
        return terminalFragment;
    }

    public HomeFragment getHomeFragment() {
        return homeFragment;
    }

    public BrowserFragment getBrowserFragment() {
        return browserFragment;
    }
}
