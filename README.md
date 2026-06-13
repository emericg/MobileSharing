# MobileSharing

MobileSharing allows QML applications to interact with mobile specific sharing features.

You can see it in action in the [MobileSharing demo](https://github.com/emericg/MobileSharing_demo).

> Supports Qt 6.8+ with CMake.

> Supports iOS 16+. Tested up to iOS 17.7 devices.

> Supports Android 9+ (API 28). Tested up to Android 16 (API 36) devices.

> [!WARNING]
> Still a work in progress at the moment...


## Features

- Handle the Android/iOS specific features about sending / recieving files, links, text between apps
- Minimal disruption on the host project using it! Build it, link it, use it.
- Send files, texts, links...
- Receive files, texts, links...


## Quick start

### Build

To get started, simply checkout the MobileSharing repository as a submodule, or copy the
MobileSharing directory into your project, then include the `CMakeLists.txt` CMake project file:

```cmake
add_subdirectory(MobileSharing/)
target_link_libraries(${PROJECT_NAME} PRIVATE MobileSharing MobileSharing_plugin)
```

You might need some hacks so the QML Language Server recognize the MobileSharing module:

```cmake
set(QML_IMPORT_PATH "${CMAKE_BINARY_DIR}/MobileSharing/" CACHE STRING "QML Modules import paths" FORCE)
set(QT_QML_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR})
```

### Setup

### Setup on iOS (sending files)

Not much!

### Setup on iOS (receiving files)

You'll need to add the file formats that your app can accept in the `Info.plist`:

```xml
<key>CFBundleDocumentTypes</key>
<array>
  <dict>
    <key>CFBundleTypeName</key>
    <string>Multimedia</string>
    <key>CFBundleTypeRole</key>
    <string>Viewer</string>
    <key>LSHandlerRank</key>
    <string>Alternate</string>
    <key>LSItemContentTypes</key>
    <array>
      <string>public.image</string>
      <string>public.audio</string>
      <string>public.movie</string>
    </array>
  </dict>
</array>
```

> plist are using tabs, not spaces, so be careful not to copy/past this snippet as is.

### Setup on Android

The module has its own Android Java sources (`io.emeric.utils`) and FileProvider `res/xml/filepaths.xml` files.  
These resources are **copied into your own application android source dir** automatically at configure time.  

You can add these copied files to your `.gitignore`:

```
# MobileSharing module: Android resources are auto-copied from thirdparty/MobileSharing/android/
assets/android/src/io/emeric/utils/QShareActivity.java
assets/android/src/io/emeric/utils/QShareUtils.java
assets/android/res/xml/filepaths.xml
```

Like in many Qt / Android app, you only need to:

Enable AndroidX in your `gradle.properties` file:
```
android.useAndroidX=true
```

Add these to the dependencies {} section of your `build.gradle` file:
```
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'androidx.core:core:1.6.1'
```


### Setup on Android (receiving files)

To **receive** content, set your launcher activity to the module's `QShareActivity` and add the incoming intent-filters.

`QShareActivity` is what makes reception work (it handles `onNewIntent`/`onActivityResult` and the JNI callbacks) so receiving will **not** work with the stock `QtActivity`.  
If your app already needs a custom activity, have it **extend `io.emeric.utils.QShareActivity`** instead of `QtActivity`.  

`singleTask` (or `singleInstance`) is **required** so a share reuses the running instance via `onNewIntent` rather than spawning a second one.

Edit your manifest's activity section:

```xml
<activity android:name="io.emeric.utils.QShareActivity"
          android:launchMode="singleTask" android:exported="true"
          ... >

    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>

    <!-- Handle incoming content shared into this app, adjust mimeType to your own needs -->
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="*/*" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="audio/*" />
        <data android:mimeType="video/*" />
        <data android:mimeType="image/*" />
        <data android:scheme="file" />
        <data android:scheme="content" />
    </intent-filter>

</activity>
```

### Setup on Android (sending files)

To **send** content, add the FileProvider in your manifest application section:

> the `${applicationId}` placeholder resolves the authority at runtime, so there is no needs to change it

```xml
<manifest ...>
  <application ...>

    <!-- Handle outgoing content -->
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.fileprovider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/filepaths" />
    </provider>

  </application>
</manifest>
```

The module should have copied a `/res/xml/filepaths.xml` file in you Android directory:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-path
        name="external"
        path="." />
    <external-files-path
        name="external_files"
        path="." />
    <cache-path
        name="cache"
        path="." />
    <external-cache-path
        name="external_cache"
        path="." />
    <files-path
        name="files"
        path="." />
    <files-path
        name="export"
        path="export/" />
</paths>
```

### Use

MobileSharing is a proper CMake QML module, so it is registered automatically by the QML engine.

`MobileSharing` is the single entry point in your application, for both sending and receiving content.

Place **exactly one** instance:

```qml
import MobileSharing

Window {
    
    MobileSharing {
        id: mobileSharing
    
        // Outgoing functions
        //mobileSharing.sendText("Hello", "Subject", "https://github.com/emericg/MobileSharing")
        //mobileSharing.sendFile("/path/to/file.pdf", "My file", "application/pdf", 42)
    
        // Outgoing status
        onShareFinished: (requestCode) => {
            console.log("MobileSharing::onShareFinished() done:", requestCode)
        }
        onShareNoAppAvailable: (requestCode) =>{
            console.log("MobileSharing::onShareNoAppAvailable() error:", requestCode)
        }
        onShareError: (requestCode, message) => {
            console.log("MobileSharing::onShareNoAppAvailable() error:", requestCode, message)
        }
        
        // Incoming files: one signal, the path is always a real file you own
        onFileReceived: (path) => {
            console.log("MobileSharing::onFileReceived()", path)
        }
        }
    }
```

### Sending files

> TODO

### Receiving files

> TODO


## Caveats

> TODO


## License

This project is licensed under the MIT license, see LICENSE file for details.

This project is based on [ekkesSHAREexample](https://github.com/ekke/ekkesSHAREexample) by ekke.

> Copyright (c) 2017 Ekkehard Gentz (ekke)  

> Copyright (c) 2026 Emeric Grange (emeric.grange@gmail.com)  
