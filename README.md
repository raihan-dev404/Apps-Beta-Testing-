# Files

An adaptive Android file manager starter app for phones, Android TV, and Google TV. The interface uses Material 3, a warm folder-inspired identity, and focusable controls suitable for a TV remote.

## Open and build

1. Install Android Studio (current stable) with Android SDK Platform 37 and the bundled Gradle JDK (JDK 17 or newer).
2. In Android Studio, choose **File > Open** and select this `Files` folder. Allow Gradle to sync and download dependencies.
3. Use **Build > Build App Bundle(s) / APK(s) > Build APK(s)** for a test APK.
4. For a release, choose **Build > Generate Signed Bundle / APK**, select **Android App Bundle** (or APK), create or select your signing key, and follow the wizard. Keep the keystore and passwords private and backed up.
5. Install the APK on your phone or Google TV. You can also use Android Studio's device chooser to install directly to a connected device; an emulator is not required.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The application ID is `com.example.files`; choose a unique ID before publishing.

## Storage access and platform limits

Files uses Android's Storage Access Framework. Choose **Choose storage folder** to grant access to a folder, SD card, or USB drive. Android controls which locations a provider exposes; some system roots, app-private folders, and removable-drive operations may be unavailable. The app does not request broad all-files access or media permissions.

File actions are limited by the selected storage provider. Copy/move works on files into a folder you choose; opening and sharing are delegated to Android apps and the system Sharesheet. APK files are handed to Android's package installer and are never installed silently. Clean only surfaces large files; deleting requires confirmation and saves a recoverable copy in the app's private Trash when the provider permits it. Trash restore copies items to the currently selected storage root. Private Trash data is not included in Android backup and is removed when the app is uninstalled.

## Current scope

This first version provides folder browsing, search, recent/category filters, saved folders, file open/share/rename/copy/move/trash, a large-file review surface, and settings/help notes. Recent and category results are discovered within the selected folder tree (up to four levels deep and 300 files per scan); they are not a device-wide index.
