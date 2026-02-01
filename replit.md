# Termux AI Development Environment

## Overview
A comprehensive AI-powered development environment built on top of Termux v0.118.1 for Android. Features swipe navigation between Terminal, AI Agent, and Browser panels, with integrated AI assistance using Anthropic/HuggingFace/Ollama APIs, RAG knowledge base, and MCP tool execution.

## Project Architecture

### Core Structure
```
app/src/main/java/com/termux/
├── app/
│   ├── MainActivity.java          # Main ViewPager2 navigation controller
│   ├── TermuxActivity.java        # Original terminal (now used as fallback)
│   ├── TermuxService.java         # Background terminal service
│   ├── ai/
│   │   ├── AIAgent.java           # AI API integration with RAG + MCP
│   │   ├── ChatMessage.java       # Chat message model
│   │   ├── rag/                   # RAG (Retrieval-Augmented Generation)
│   │   │   ├── Document.java          # Document model
│   │   │   ├── DocumentMetadata.java  # Document metadata
│   │   │   ├── DocumentChunker.java   # Text chunking utilities
│   │   │   ├── EmbeddingService.java  # Text embeddings (HuggingFace/Ollama/TF-IDF)
│   │   │   ├── VectorStore.java       # Vector storage and similarity search
│   │   │   └── RAGEngine.java         # Main RAG orchestrator
│   │   └── mcp/                   # MCP (Model Context Protocol) Tools
│   │       ├── MCPTool.java           # Tool interface
│   │       ├── MCPToolResult.java     # Tool execution result
│   │       ├── MCPToolRegistry.java   # Tool registration and discovery
│   │       ├── MCPOrchestrator.java   # Tool execution coordination
│   │       └── tools/
│   │           ├── TerminalTool.java      # Shell command execution
│   │           ├── FileOperationTool.java # File system operations
│   │           ├── WebSearchTool.java     # Tavily web search
│   │           ├── WebBrowserTool.java    # Web page fetching
│   │           └── PackageManagerTool.java # Termux pkg management
│   ├── browser/
│   │   └── BrowserTab.java        # Browser tab model
│   ├── ota/                       # OTA Updates
│   │   ├── UpdateInfo.java            # Update information model
│   │   └── OTAUpdateManager.java      # GitHub release update manager
│   ├── ui/
│   │   ├── fragments/
│   │   │   ├── HomeFragment.java      # AI chat interface (center)
│   │   │   ├── TerminalFragment.java  # Terminal wrapper (left)
│   │   │   └── BrowserFragment.java   # WebView browser (right)
│   │   ├── adapters/
│   │   │   ├── MainPagerAdapter.java  # ViewPager2 adapter
│   │   │   ├── ChatAdapter.java       # Chat messages RecyclerView
│   │   │   └── TabsAdapter.java       # Browser tabs adapter
│   │   └── modals/
│   │       ├── DevModalFragment.java       # Dev tools modal
│   │       ├── WorkspaceModalFragment.java # Session management
│   │       └── SettingsModalFragment.java  # API key configuration
```

### Navigation
- **Left Panel**: Terminal (original Termux functionality)
- **Center Panel**: AI Agent (chat with tool calling capabilities)
- **Right Panel**: Browser (multi-tab WebView with localhost access)

Swipe gestures enable smooth transitions between panels.

### Design System
Location: `app/src/main/res/values/`

**Colors (Watercolor Theme)**:
- Canvas Background: #F6F4F1
- Surface: #FFFFFF
- Primary Accent (Mauve): #7A6EAA
- Secondary Accents: Olive (#8A8C68), Terracotta (#C47A5A), Mint (#A7C4B8)

**Typography**:
- H1: 48sp, H2: 32sp, H3: 24sp, Body: 16sp, Caption: 12sp

**Spacing Tokens**:
- xs: 4dp, sm: 8dp, md: 16dp, lg: 24dp, xl: 40dp

## Key Features

### AI Agent
- Supports Anthropic Claude API (primary)
- Fallback to local Ollama
- **RAG Knowledge Base** for context-aware responses
- **MCP Tool Calling** for:
  - Terminal command execution
  - File operations (create, read, edit, delete, copy, move)
  - Web search via Tavily API
  - Web page content extraction
  - Package management (pkg install/uninstall)
- Conversation history with session management

### RAG (Retrieval-Augmented Generation)
- Document ingestion (files, code, conversations)
- Smart chunking based on document type
- Embedding generation (HuggingFace, Ollama, TF-IDF fallback)
- Vector similarity search
- Automatic context injection into AI prompts

### MCP Tools (Model Context Protocol)
Built-in tools that the AI can use:
- `execute_command` - Run terminal commands
- `file_operation` - Create, read, write, delete files
- `web_search` - Search the web via Tavily
- `browse_web` - Fetch and extract web page content
- `package_manager` - Install/uninstall Termux packages

### Browser Integration
- Multi-tab browsing
- Quick localhost access (3000, 8080)
- Progress indicator
- Full JavaScript support

### Dev Tools (Floating Nav)
- Package management (pkg install)
- Runtime selection (Node, Python, etc.)
- Git integration
- Domain setup

### OTA Updates
- Automatic update checking from GitHub releases
- Download progress tracking
- One-click installation
- Version comparison and skip functionality

## Recent Changes
- **2024**: Implemented ViewPager2 swipe navigation
- **2024**: Created watercolor design system
- **2024**: Built AI Agent with Anthropic API integration
- **2024**: Added floating bottom navigation with modals
- **2024**: Integrated WebView browser with tab support
- **2024**: Implemented RAG knowledge base system
- **2024**: Added MCP tools for terminal, file, and web operations
- **2024**: Created OTA update system from GitHub releases
- **2024**: Added GitHub Actions CI/CD for APK builds

## Build & Run
```bash
# Build debug APK
./gradlew assembleDebug

# Output location
app/build/outputs/apk/debug/
```

## Dependencies
- AndroidX ViewPager2
- AndroidX RecyclerView
- AndroidX ConstraintLayout
- AndroidX WebKit
- OkHttp (HTTP client)
- Gson (JSON parsing)
- Google Material Design

## User Preferences
- Native Android development (Java)
- Clean watercolor aesthetic with mauve (#7A6EAA) accent
- Terminal UI remains original - only logic modifications
- No emojis in UI - use Material icons
- Floating nav only visible on Home screen
