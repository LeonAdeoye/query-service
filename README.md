# Query Service

A Spring Boot Gradle Kotlin reactive query service that executes SQL queries on Oracle and MS SQL Server databases via HTTP REST API.

## Viewing the Implementation Plan

The implementation plan is located in [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md).

### Viewing Mermaid Diagrams

The plan contains Mermaid diagrams that visualize the architecture and data flow. Here are several ways to view them:

#### Option 1: GitHub/GitLab (Recommended)
If you push this repository to GitHub or GitLab, the Mermaid diagrams will render automatically in the markdown files.

#### Option 2: VS Code Extensions
Install one of these VS Code extensions:
- **Markdown Preview Mermaid Support** by Matt Bierner
- **Mermaid Preview** by vstirbu
- **Markdown Preview Enhanced** by Yiyi Wang (supports Mermaid and many other features)

After installation, open the markdown file and use the preview pane (Ctrl+Shift+V or Cmd+Shift+V).

#### Option 3: Online Mermaid Editors
1. **Mermaid Live Editor**: https://mermaid.live/
   - Copy the mermaid code block from the plan
   - Paste it into the editor
   - View the rendered diagram

2. **Mermaid.ink**: https://mermaid.ink/
   - Can generate static images from Mermaid code

#### Option 4: Desktop Applications
- **Obsidian** (https://obsidian.md/) - Note-taking app with built-in Mermaid support
- **Typora** (https://typora.io/) - Markdown editor with Mermaid rendering
- **Mark Text** (https://marktext.app/) - Open-source markdown editor

#### Option 5: Command Line Tools
- **Mermaid CLI** (`@mermaid-js/mermaid-cli`): 
  ```bash
  npm install -g @mermaid-js/mermaid-cli
  mmdc -i IMPLEMENTATION_PLAN.md -o diagrams/
  ```

#### Option 6: Browser Extensions
- **Markdown Viewer** extensions for Chrome/Firefox that support Mermaid

### Quick Start for Viewing Diagrams

**Easiest method**: 
1. Install the "Markdown Preview Mermaid Support" extension in VS Code
2. Open `IMPLEMENTATION_PLAN.md`
3. Press `Ctrl+Shift+V` (Windows/Linux) or `Cmd+Shift+V` (Mac) to open the preview
4. The diagrams will render automatically

**Online method**:
1. Go to https://mermaid.live/
2. Copy the mermaid code block (between the ```mermaid markers) from the plan
3. Paste into the editor to see the diagram

## Project Structure

```
query-service/
├── IMPLEMENTATION_PLAN.md    # Detailed implementation plan with diagrams
├── README.md                  # This file
└── [project files will be created during implementation]
```

## Features

- Multi-database support (Oracle & MS SQL Server)
- Async/reactive/non-blocking execution
- Query caching
- Query prioritization
- Streaming/pagination support
- Big data file export
- Comprehensive logging with error codes
- Connection pooling
- Configurable retries
- Source tracking

See [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) for complete details.

