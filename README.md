# Mission GTA Mobile

Mission GTA Mobile is an experimental Android game-runtime/project shell designed to load and execute game logic natively.

### Architecture

- **Offline-First:** No proprietary GTA V game files, `.rpf` files, executables, or copyrighted assets are included in this repository.
- **BYOD (Bring Your Own Data):** Actual game resources are NOT stored in GitHub. Users must legally obtain their game resources and store them locally on their device (e.g., `Android/0/Games/gta5/`).
- **Storage Access Framework:** The application uses Android's native Storage Access Framework (`OpenDocumentTree`) to allow the user to select the game-resource directory securely without requiring broad storage permissions.
- **Resource Validation:** Once selected, the app scans the directory for required assets (like `common.rpf`, `x64` directories, etc.) and validates their presence. Diagnostic logs (like `*.log`, `*.bin`) are ignored.
- **Native Runtime Setup:** The architecture is designed to supply these resources to a future native C++/NDK runtime. The `GameResourceManager` resolves paths cleanly and protects against traversal attacks.

### Notice

This is an experimental shell project and contains NO proprietary or copyrighted Rockstar Games files. Do not commit or upload `.rpf`, `.bin`, `.exe`, or other proprietary assets to this repository.
