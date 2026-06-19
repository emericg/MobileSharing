/*
 * Copyright (c) 2017 Ekkehard Gentz (ekke)
 * Copyright (c) 2026 Emeric Grange
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.emeric.mobilesharing;

import java.lang.String;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;

import android.util.Log;
import android.net.Uri;
import android.os.Parcelable;
import android.os.Build;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.ContentResolver;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.webkit.MimeTypeMap;
import androidx.core.content.FileProvider;
import androidx.core.app.ShareCompat;

public class QShareUtils
{
    // store the app main activity
    private static Activity m_activity = null;

    // reference android:authorities as defined in AndroidManifest.xml FileProvider section
    // resolved dynamically from the running app by the setActivity() call below,
    // so it always matches "${applicationId}.fileprovider" with no manual editing
    private static String AUTHORITY = "";

    protected QShareUtils() {
       //Log.d("QShareUtils", "QShareUtils()");
    }

    public void setActivity(Activity activity) {
        m_activity = activity;
        if (m_activity == null) {
            Log.d("QShareUtils", "Activity is null");
        } else {
            AUTHORITY = m_activity.getPackageName() + ".fileprovider";
            Log.d("QShareUtils", AUTHORITY);
        }
    }

    public static boolean checkMimeTypeView(String mimeType) {
        if (m_activity == null) return false;

        Intent myIntent = new Intent();
        myIntent.setAction(Intent.ACTION_VIEW);
        // without an URI resolve always fails
        // an empty URI allows to resolve the Activity
        File fileToShare = new File("");
        Uri uri = Uri.fromFile(fileToShare);
        myIntent.setDataAndType(uri, mimeType);

        // Verify that the intent will resolve to an activity
        if (myIntent.resolveActivity(m_activity.getPackageManager()) != null) {
            Log.d("QShareUtils", " checkMime() yes - we can go on and View");
            return true;
        } else {
            Log.d("QShareUtils", " checkMime() sorry - no App available to View");
        }
        return false;
    }

    public static boolean checkMimeTypeEdit(String mimeType) {
        if (m_activity == null) return false;

        Intent myIntent = new Intent();
        myIntent.setAction(Intent.ACTION_EDIT);
        // without an URI resolve always fails
        // an empty URI allows to resolve the Activity
        File fileToShare = new File("");
        Uri uri = Uri.fromFile(fileToShare);
        myIntent.setDataAndType(uri, mimeType);

        // Verify that the intent will resolve to an activity
        if (myIntent.resolveActivity(m_activity.getPackageManager()) != null) {
            Log.d("QShareUtils", " checkMime() yes - we can go on and Edit");
            return true;
        } else {
            Log.d("QShareUtils", " checkMime() sorry - no App available to Edit");
        }
        return false;
    }

    public static boolean sendText(String text, String subject, String url) {
        if (m_activity == null) return false;

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text + " " + url);
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        sendIntent.setType("text/plain");

        Intent chooserIntent = Intent.createChooser(sendIntent, "Share to messenger");

        // Launch the chooser directly. Don't gate on resolveActivity(): on Android 11+ it is
        // filtered by package visibility and can falsely return null, while the system chooser
        // is exempt from that filtering anyway. Only a thrown ActivityNotFoundException (no app
        // at all) is a real miss.
        try {
            m_activity.startActivity(chooserIntent);
            return true;
        } catch (android.content.ActivityNotFoundException e) {
            Log.d("QShareUtils", " sendText() no app to handle ACTION_SEND - " + e);
            return false;
        }
    }

    // thx @oxied and @pooks for the idea: https://stackoverflow.com/a/18835895/135559
    // theIntent is already configured with all needed properties and flags
    // so we only have to add the packageName of targeted app
    public static boolean createCustomChooserAndStartActivity(Intent theIntent, String title, int requestId, Uri uri) {
        if (m_activity == null) return false;
        final Context context = m_activity;

        final PackageManager packageManager = context.getPackageManager();

        // MATCH_DEFAULT_ONLY: Resolution and querying flag. if set, only filters that support the CATEGORY_DEFAULT will be considered for matching.
        // Check if there is a default app for this type of content.
        ResolveInfo defaultAppInfo = packageManager.resolveActivity(theIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (defaultAppInfo == null) {
            Log.d("QShareUtils", title + " PackageManager cannot resolve Activity");
            return false;
        }

        // had to remove this check - there can be more Activity names, per ex
        // com.google.android.apps.docs.editors.kix.quickword.QuickWordDocumentOpenerActivityAlias
        // if (!defaultAppInfo.activityInfo.name.endsWith("ResolverActivity") && !defaultAppInfo.activityInfo.name.endsWith("EditActivity")) {
            // Log.d("QShareUtils", title + " defaultAppInfo not Resolver or EditActivity: " + defaultAppInfo.activityInfo.name);
            // return false;
        //}

        // Retrieve all apps for our intent. Check if there are any apps returned
        List<ResolveInfo> appInfoList = packageManager.queryIntentActivities(theIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (appInfoList.isEmpty()) {
            Log.d("QShareUtils", title + " appInfoList.isEmpty");
            return false;
        }
        Log.d("QShareUtils", title + " appInfoList: " + appInfoList.size());

        // Sort in alphabetical order
        Collections.sort(appInfoList, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo first, ResolveInfo second) {
                String firstName = first.loadLabel(packageManager).toString();
                String secondName = second.loadLabel(packageManager).toString();
                return firstName.compareToIgnoreCase(secondName);
            }
        });

        List<Intent> targetedIntents = new ArrayList<Intent>();
        // Filter itself and create intent with the rest of the apps.
        for (ResolveInfo appInfo : appInfoList) {
            // get the target PackageName
            String targetPackageName = appInfo.activityInfo.packageName;
            // we don't want to share with our own app
            // in fact sharing with own app with resultCode will crash because doesn't work well with launch mode 'singleInstance'
            if (targetPackageName.equals(context.getPackageName())) {
                continue;
            }
            // if you have a blacklist of apps please exclude them here

            // we create the targeted Intent based on our already configured Intent
            Intent targetedIntent = new Intent(theIntent);
            // now add the target packageName so this Intent will only find the one specific App
            targetedIntent.setPackage(targetPackageName);
            // collect all these targetedIntents
            targetedIntents.add(targetedIntent);

            // did some changes to make it run with API 30+ and Android 13 devices.
            // removed KitKat check and added queries to AndroidManifest
            // thx: https://forum.qt.io/topic/127170/android-11-qdir-mkdir-does-not-always-work/11
            context.grantUriPermission(targetPackageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }

        // check if there are apps found for our Intent to avoid that there was only our own removed app before
        if (targetedIntents.isEmpty()) {
            Log.d("QShareUtils", title + " targetedIntents.isEmpty");
            return false;
        }

        // now we can create our Intent with custom Chooser
        // we need all collected targetedIntents as EXTRA_INITIAL_INTENTS
        // we're using the last targetedIntent as initializing Intent, because
        // chooser adds its initializing intent to the end of EXTRA_INITIAL_INTENTS :)
        Intent chooserIntent = Intent.createChooser(targetedIntents.remove(targetedIntents.size() - 1), title);
        if (targetedIntents.isEmpty()) {
            Log.d("QShareUtils", title + " only one Intent left for Chooser");
        } else {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetedIntents.toArray(new Parcelable[] {}));
        }
        //chooserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // ?

        // Verify that the intent will resolve to an activity
        if (chooserIntent.resolveActivity(m_activity.getPackageManager()) != null) {
            if (requestId > 0) {
                // Mark this code as ours so QShareActivity.onActivityResult() consumes its result (and ignores results from Qt's own dialogs)
                if (m_activity instanceof QShareActivity) {
                    QShareActivity.ownRequestCodes.add(requestId);
                }
                m_activity.startActivityForResult(chooserIntent, requestId);
            } else {
                m_activity.startActivity(chooserIntent);
            }
            return true;
        }
        Log.d("QShareUtils", title + " Chooser Intent not resolved. Should never happen");
        return false;
    }

    public static boolean sendFile(String filePath, String title, String mimeType, int requestId) {
        if (m_activity == null) return false;
        final Context context = m_activity;
        if (context == null) return false;

        // (v2)
        File file = new File(filePath);
        Uri fileUri;
        try {
            fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        } catch (IllegalArgumentException e) {
            // path not under a filepaths.xml root (the C++ layer normally prevents this)
            Log.e("QShareUtils", "sendFile: cannot be shared: " + filePath + " - " + e);
            return false;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setDataAndType(fileUri, mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(Intent.createChooser(shareIntent, "Share file using"));

        return true;
/*
        // using v4 support library create the Intent from ShareCompat
        // Intent sendIntent = new Intent();
        Intent sendIntent = ShareCompat.IntentBuilder.from(m_activity).getIntent();
        sendIntent.setAction(Intent.ACTION_SEND);

        File fileToShare = new File(filePath);

        // Using FileProvider you must get the URI from FileProvider using your AUTHORITY
        // Uri uri = Uri.fromFile(fileToShare);
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(m_activity, AUTHORITY, fileToShare);
        } catch (IllegalArgumentException e) {
            Log.d("QShareUtils", " cannot be shared: " + filePath + " " + e);
            return false;
        }

        Log.d("QShareUtils", " sendFile " + uri.toString());
        sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
        //sendIntent.setData(Uri.parse("mailto:")); // ?
        //sendIntent.putExtra(Intent.EXTRA_SUBJECT,title); // ?

        if (mimeType == null || mimeType.isEmpty()) {
            // fallback if mimeType not set
            mimeType = m_activity.getContentResolver().getType(uri);
            Log.d("QShareUtils", " sendFile guessed mimeType: " + mimeType);
        } else {
            Log.d("QShareUtils", " sendFile w mimeType: " + mimeType);
        }

        //sendIntent.setType(mimeType); // replaced
        sendIntent.setTypeAndNormalize(mimeType); // ?
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        sendIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION); // ?

        return createCustomChooserAndStartActivity(sendIntent, title, requestId, uri);
*/
    }

    public static boolean viewFile(String filePath, String title, String mimeType, int requestId) {
        if (m_activity == null) return false;
        final Context context = m_activity;
        if (context == null) return false;

        File file = new File(filePath);
        Uri fileUri;
        try {
            fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        } catch (IllegalArgumentException e) {
            Log.e("QShareUtils", "viewFile: cannot be shared: " + filePath + " - " + e);
            return false;
        }

        Intent shareIntent = new Intent(Intent.ACTION_VIEW);
        shareIntent.setDataAndType(fileUri, mimeType);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(Intent.createChooser(shareIntent, "View file using"));

        return true;
/*
        // using v4 support library create the Intent from ShareCompat
        // Intent viewIntent = new Intent();
        Intent viewIntent = ShareCompat.IntentBuilder.from(m_activity).getIntent();
        viewIntent.setAction(Intent.ACTION_VIEW);

        File fileToShare = new File(filePath);

        // Using FileProvider you must get the URI from FileProvider using your AUTHORITY
        // Uri uri = Uri.fromFile(fileToShare);
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(m_activity, AUTHORITY, fileToShare);
        } catch (IllegalArgumentException e) {
            Log.d("QShareUtils", " viewFile - cannot be shared: " + filePath);
            return false;
        }
        // now we got a content URI per ex
        // content://org.ekkescorner.examples.sharex.fileprovider/my_shared_files/qt-logo.png
        // from a fileUrl:
        // /data/user/0/org.ekkescorner.examples.sharex/files/share_example_x_files/qt-logo.png
        Log.d("QShareUtils", " viewFile from file path: " + filePath);
        Log.d("QShareUtils", " viewFile to content URI: " + uri.toString());

        if (mimeType == null || mimeType.isEmpty()) {
            // fallback if mimeType not set
            mimeType = m_activity.getContentResolver().getType(uri);
            Log.d("QShareUtils", " viewFile guessed mimeType: " + mimeType);
        } else {
            Log.d("QShareUtils", " viewFile w mimeType: " + mimeType);
        }

        viewIntent.setDataAndType(uri, mimeType);
        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        viewIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        return createCustomChooserAndStartActivity(viewIntent, title, requestId, uri);
*/
    }

    public static boolean editFile(String filePath, String title, String mimeType, int requestId) {
        if (m_activity == null) return false;
        final Context context = m_activity;
        if (context == null) return false;

        Intent editIntent = new Intent();
        editIntent.setAction(Intent.ACTION_EDIT);

        File fileToShare = new File(filePath);

        // Using FileProvider you must get the URI from FileProvider using your AUTHORITY
        // Uri uri = Uri.fromFile(fileToShare);
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(m_activity, AUTHORITY, fileToShare);
        } catch (IllegalArgumentException e) {
            Log.d("QShareUtils", " editFile - cannot be shared: " + filePath);
            return false;
        }
        Log.d("QShareUtils", " editFile " + uri.toString());

        if (mimeType == null || mimeType.isEmpty()) {
            // fallback if mimeType not set
            mimeType = m_activity.getContentResolver().getType(uri);
            Log.d("QShareUtils", " editFile guessed mimeType: " + mimeType);
        } else {
            Log.d("QShareUtils", " editFile w mimeType: " + mimeType);
        }

        editIntent.setDataAndType(uri, mimeType);
        editIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        editIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        return createCustomChooserAndStartActivity(editIntent, title, requestId, uri);
    }

    // ------------------------------------------------------------------------
    // Incoming content helpers (used by QShareActivity.processIntent())
    // ------------------------------------------------------------------------

    // Resolve the human-readable display name of a content:// Uri, or null.
    public static String getContentName(ContentResolver cR, Uri uri) {
        Cursor cursor = cR.query(uri, null, null, null, null);
        if (cursor == null) {
            return null;
        }
        try {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    // Copy the content behind a file:// or content:// Uri into workingDirPath via an
    // InputStream, so the app always gets a real, readable file it owns.
    // Returns the absolute path of the written file, or null on failure.
    public static String createFile(ContentResolver cR, Uri uri, String workingDirPath) {
        if (workingDirPath == null || workingDirPath.isEmpty()) {
            Log.e("QShareUtils", "createFile: workingDirPath is empty");
            return null;
        }

        // Best display name: ContentResolver (content://), else the Uri's last
        // segment (file://), else a timestamped name keeping the mime extension.
        String name = getContentName(cR, uri);
        if (name == null || name.isEmpty()) {
            name = uri.getLastPathSegment();
        }
        if (name == null || name.isEmpty() || name.contains("/")) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(cR.getType(uri));
            name = "shared_" + System.currentTimeMillis() + (ext != null ? "." + ext : "");
        }

        File dir = new File(workingDirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e("QShareUtils", "createFile: cannot create working dir " + workingDirPath);
            return null;
        }

        File file = new File(dir, name);
        try (InputStream is = cR.openInputStream(uri);
             FileOutputStream os = new FileOutputStream(file)) {
            if (is == null) {
                Log.e("QShareUtils", "createFile: cannot open InputStream for " + uri);
                return null;
            }
            byte[] buffer = new byte[4096];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            Log.d("QShareUtils", "createFile: wrote " + file.getAbsolutePath());
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e("QShareUtils", "createFile: failed - " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // Outgoing "save file to..." helpers (SAF ACTION_CREATE_DOCUMENT)
    // ------------------------------------------------------------------------

    // Source paths for in-flight saveFile() requests, keyed by request code. The
    // destination is only known later (in QShareActivity.onActivityResult), so we
    // stash the source here between launching the picker and writing the bytes.
    private static final Map<Integer, String> pendingSaves =
            Collections.synchronizedMap(new HashMap<Integer, String>());

    public static boolean isPendingSave(int requestCode) {
        return pendingSaves.containsKey(requestCode);
    }

    // Launch the system "create document" picker. The actual write happens in
    // completeSave() once the user picked a destination. Returns false (so the C++
    // layer can report an error) if there is no QShareActivity to receive the result.
    public static boolean saveFile(String sourcePath, String suggestedName, String mimeType, int requestId) {
        if (m_activity == null) return false;
        if (!(m_activity instanceof QShareActivity)) {
            // Without QShareActivity, onActivityResult() won't reach us and the result is lost.
            Log.e("QShareUtils", "saveFile: requires QShareActivity to receive the result");
            return false;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType((mimeType == null || mimeType.isEmpty()) ? "*/*" : mimeType);
        if (suggestedName != null && !suggestedName.isEmpty()) {
            intent.putExtra(Intent.EXTRA_TITLE, suggestedName);
        }

        // Do NOT gate this on resolveActivity(): ACTION_CREATE_DOCUMENT is always handled by
        // the system DocumentsUI, but resolveActivity()/queryIntentActivities() are filtered by
        // Android 11+ package visibility and return null here even though startActivityForResult()
        // succeeds. So launch directly and only treat a thrown ActivityNotFoundException as failure.
        pendingSaves.put(requestId, sourcePath);
        QShareActivity.ownRequestCodes.add(requestId);
        try {
            m_activity.startActivityForResult(intent, requestId);
            return true;
        } catch (android.content.ActivityNotFoundException e) {
            Log.e("QShareUtils", "saveFile: no activity to handle ACTION_CREATE_DOCUMENT - " + e);
            pendingSaves.remove(requestId);
            QShareActivity.ownRequestCodes.remove(requestId);
            return false;
        }
    }

    // Called from QShareActivity.onActivityResult(): write the stashed source file into the
    // user-chosen destination Uri (null means the user cancelled), then notify C++ natively.
    public static void completeSave(ContentResolver cR, int requestCode, Uri destUri) {
        String sourcePath = pendingSaves.remove(requestCode);

        if (destUri == null) {
            QShareActivity.fireSaveResult(requestCode, false, true); // cancelled
            return;
        }
        if (sourcePath == null) {
            Log.e("QShareUtils", "completeSave: no pending source for request " + requestCode);
            QShareActivity.fireSaveResult(requestCode, false, false);
            return;
        }

        boolean ok = writeFileToUri(cR, sourcePath, destUri);
        QShareActivity.fireSaveResult(requestCode, ok, false);
    }

    // Stream a local file into a content:// Uri via the ContentResolver's OutputStream.
    private static boolean writeFileToUri(ContentResolver cR, String sourcePath, Uri destUri) {
        try (InputStream is = new FileInputStream(sourcePath);
             OutputStream os = cR.openOutputStream(destUri)) {
            if (os == null) {
                Log.e("QShareUtils", "writeFileToUri: cannot open OutputStream for " + destUri);
                return false;
            }
            byte[] buffer = new byte[4096];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            Log.d("QShareUtils", "writeFileToUri: wrote " + sourcePath + " -> " + destUri);
            return true;
        } catch (Exception e) {
            Log.e("QShareUtils", "writeFileToUri: failed - " + e.getMessage());
            return false;
        }
    }
}
