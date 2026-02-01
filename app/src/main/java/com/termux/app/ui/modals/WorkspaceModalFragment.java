package com.termux.app.ui.modals;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.MainActivity;

public class WorkspaceModalFragment extends Fragment {

    private RecyclerView aiSessionsList;
    private RecyclerView terminalSessionsList;
    private Button btnNewSession;
    private Button btnClearContext;

    public static WorkspaceModalFragment newInstance() {
        return new WorkspaceModalFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_modal_workspace, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupListeners(view);
        loadSessions();
    }

    private void initViews(View view) {
        aiSessionsList = view.findViewById(R.id.ai_sessions_list);
        terminalSessionsList = view.findViewById(R.id.terminal_sessions_list);
        btnNewSession = view.findViewById(R.id.btn_new_session);
        btnClearContext = view.findViewById(R.id.btn_clear_context);

        aiSessionsList.setLayoutManager(new LinearLayoutManager(getContext()));
        terminalSessionsList.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void setupListeners(View view) {
        view.findViewById(R.id.modal_overlay).setOnClickListener(v -> closeModal());
        view.findViewById(R.id.btn_close).setOnClickListener(v -> closeModal());
        
        btnNewSession.setOnClickListener(v -> createNewSession());
        btnClearContext.setOnClickListener(v -> clearContext());
    }

    private void loadSessions() {
        // Load AI and terminal sessions
    }

    private void createNewSession() {
        // Create new AI session
    }

    private void clearContext() {
        // Clear AI context
    }

    private void closeModal() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).hideModal();
        }
    }
}
