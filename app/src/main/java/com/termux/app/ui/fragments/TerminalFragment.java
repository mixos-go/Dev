package com.termux.app.ui.fragments;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.termux.R;
import com.termux.app.TermuxService;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.TermuxTerminalSessionClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.shared.terminal.io.extrakeys.ExtraKeysView;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

/**
 * Fragment that wraps the original Termux terminal functionality.
 * This fragment maintains the original Termux terminal behavior while
 * integrating it into the new swipe-based navigation structure.
 */
public class TerminalFragment extends Fragment implements ServiceConnection {

    private TermuxService mTermuxService;
    private TerminalView mTerminalView;
    private TermuxTerminalViewClient mTermuxTerminalViewClient;
    private TermuxTerminalSessionClient mTermuxTerminalSessionClient;
    private ExtraKeysView mExtraKeysView;
    private TermuxSessionsListViewController mTermuxSessionListViewController;
    
    private DrawerLayout mDrawerLayout;
    private LinearLayout mLeftDrawer;
    private ListView mTerminalSessionsList;
    private ImageButton mSettingsButton;
    private Button mToggleKeyboardButton;
    private Button mNewSessionButton;
    private ViewPager mTerminalToolbarViewPager;

    private boolean mIsServiceBound = false;

    public static TerminalFragment newInstance() {
        return new TerminalFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_termux, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
    }

    private void initViews(View view) {
        mTerminalView = view.findViewById(R.id.terminal_view);
        mDrawerLayout = view.findViewById(R.id.drawer_layout);
        mLeftDrawer = view.findViewById(R.id.left_drawer);
        mTerminalSessionsList = view.findViewById(R.id.terminal_sessions_list);
        mSettingsButton = view.findViewById(R.id.settings_button);
        mToggleKeyboardButton = view.findViewById(R.id.toggle_keyboard_button);
        mNewSessionButton = view.findViewById(R.id.new_session_button);
        mTerminalToolbarViewPager = view.findViewById(R.id.terminal_toolbar_view_pager);

        // Setup click listeners
        if (mSettingsButton != null) {
            mSettingsButton.setOnClickListener(v -> openSettings());
        }

        if (mToggleKeyboardButton != null) {
            mToggleKeyboardButton.setOnClickListener(v -> toggleSoftKeyboard());
        }

        if (mNewSessionButton != null) {
            mNewSessionButton.setOnClickListener(v -> createNewSession());
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        bindTermuxService();
    }

    @Override
    public void onStop() {
        super.onStop();
        unbindTermuxService();
    }

    private void bindTermuxService() {
        if (getActivity() == null) return;
        
        Intent serviceIntent = new Intent(getActivity(), TermuxService.class);
        getActivity().bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);
    }

    private void unbindTermuxService() {
        if (getActivity() == null || !mIsServiceBound) return;
        
        getActivity().unbindService(this);
        mIsServiceBound = false;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mIsServiceBound = true;
        mTermuxService = ((TermuxService.LocalBinder) service).service;
        
        // Initialize terminal view client
        if (mTermuxTerminalViewClient == null && getActivity() != null) {
            // Create session if none exists
            if (mTermuxService.getTermuxSessionsSize() == 0) {
                mTermuxService.createTermuxSession(null, null, null, 
                    null, null, false, null);
            }

            // Set current session
            TerminalSession session = mTermuxService.getTermuxSession(0);
            if (session != null && mTerminalView != null) {
                mTerminalView.attachSession(session);
            }
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mIsServiceBound = false;
        mTermuxService = null;
    }

    private void openSettings() {
        // Will be handled by main activity
        if (getActivity() instanceof OnTerminalActionListener) {
            ((OnTerminalActionListener) getActivity()).onOpenSettings();
        }
    }

    private void toggleSoftKeyboard() {
        if (mTerminalView != null) {
            mTerminalView.requestFocus();
            // Toggle soft keyboard
            android.view.inputmethod.InputMethodManager imm = 
                (android.view.inputmethod.InputMethodManager) 
                getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.toggleSoftInput(0, 0);
            }
        }
    }

    private void createNewSession() {
        if (mTermuxService == null) return;

        TerminalSession session = mTermuxService.createTermuxSession(
            null, null, null, null, null, false, null);
        
        if (session != null && mTerminalView != null) {
            mTerminalView.attachSession(session);
        }
    }

    public void executeCommand(String command) {
        if (mTermuxService == null || mTerminalView == null) return;

        TerminalSession currentSession = mTerminalView.getCurrentSession();
        if (currentSession != null) {
            currentSession.write(command + "\n");
        }
    }

    public String getTerminalOutput() {
        if (mTerminalView == null) return "";
        
        TerminalSession session = mTerminalView.getCurrentSession();
        if (session == null) return "";
        
        // Get transcript from terminal
        return session.getEmulator().getScreen().getTranscriptText();
    }

    public interface OnTerminalActionListener {
        void onOpenSettings();
    }
}
