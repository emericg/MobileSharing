/*!
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

#import "MobileSharing_ios.h"
#import "docviewcontroller_ios.h"

#import <UIKit/UIKit.h>
#import <UIKit/UIDocumentInteractionController.h>
#import <UniformTypeIdentifiers/UniformTypeIdentifiers.h>

#import <QGuiApplication>
#import <QQuickWindow>
#import <QDesktopServices>
#import <QUrl>
#import <QFileInfo>

/* ************************************************************************** */

// Resolve the view controller to present from.
// Prefers the foreground-active window scene's key window, and falls back to
// any window scene if none is active yet. Returns nil if there is none.
static UIViewController *topViewController()
{
    UIWindow *fallbackWindow = nil;

    for (UIScene *scene in [UIApplication sharedApplication].connectedScenes) {
        if (![scene isKindOfClass:[UIWindowScene class]]) continue;
        UIWindowScene *windowScene = (UIWindowScene *)scene;

        UIWindow *keyWindow = nil;
        for (UIWindow *w in windowScene.windows) {
            if (w.isKeyWindow) { keyWindow = w; break; }
        }
        if (keyWindow == nil) keyWindow = windowScene.windows.firstObject;
        if (keyWindow == nil) continue;

        if (scene.activationState == UISceneActivationStateForegroundActive) {
            return keyWindow.rootViewController; // best match
        }
        if (fallbackWindow == nil) fallbackWindow = keyWindow;
}

    return fallbackWindow.rootViewController;
}

/* ************************************************************************** */

IosShareUtils::IosShareUtils(QObject *parent) : PlatformShareUtils(parent)
{
    // the iOS "receive a file" entry point // analogous to the Android QShareActivity.processIntent() > setFileReceived() path
    // important note: hijack QDesktopServices::openUrl("file://") though you can't really do that on iOS anyway...
    QDesktopServices::setUrlHandler("file", this, "handleFileUrlReceived");
}

/* ************************************************************************** */

bool IosShareUtils::checkMimeTypeView(const QString &mimeType) {
#pragma unused (mimeType)
    // MimeType not used yet
    return true;
}

bool IosShareUtils::checkMimeTypeEdit(const QString &mimeType) {
#pragma unused (mimeType)
    // MimeType not used yet
    return true;
}

/* ************************************************************************** */

void IosShareUtils::sendText(const QString &text, const QString &subject, const QUrl &url) {

    NSMutableArray *sharingItems = [NSMutableArray new];

    if (!text.isEmpty()) {
        [sharingItems addObject:text.toNSString()];
    }
    if (url.isValid()) {
        [sharingItems addObject:url.toNSURL()];
    }

    UIViewController *qtUIViewController = topViewController();

    UIActivityViewController *activityController = [[UIActivityViewController alloc] initWithActivityItems:sharingItems applicationActivities:nil];
    if (qtUIViewController == nil) {
        emit shareError(0, "Cannot share: no root view controller");
        [activityController release];
        return;
    }

    // Report the outcome once the sheet closes
    activityController.completionWithItemsHandler = ^(UIActivityType activityType, BOOL completed,
                                                      NSArray *returnedItems, NSError *activityError) {
#pragma unused (activityType, returnedItems)
        if (activityError) {
            emit shareError(0, QString::fromNSString(activityError.localizedDescription));
        } else if (completed) {
            emit shareFinished(0);
        } else {
            // dismissed without sharing -> stay silent
        }
    };

    // iPad: anchor the popover (harmless on iPhone, where it presents as a sheet).
    activityController.popoverPresentationController.sourceView = qtUIViewController.view;
    activityController.popoverPresentationController.sourceRect = CGRectMake(qtUIViewController.view.bounds.size.width / 2.0,
                                                                            qtUIViewController.view.bounds.size.height / 2.0, 0, 0);

    [qtUIViewController presentViewController:activityController animated:YES completion:nil];
    [activityController release];
}

void IosShareUtils::sendFile(const QString &filePath, const QString &title, const QString &mimeType, const int &requestId, bool move) {
#pragma unused (title, mimeType, move)
    // 'move' is a no-op on iOS: any file in the app sandbox is shareable as-is via
    // UIDocumentInteractionController (no FileProvider equivalent). Throwaway-file
    // cleanup on iOS is left to the app (e.g. delete after onShareFinished).

    NSString *nsFilePath = filePath.toNSString();
    NSURL *nsFileUrl = [NSURL fileURLWithPath:nsFilePath];

    static DocViewController *docViewController = nil;
    if (docViewController != nil) {
        [docViewController removeFromParentViewController];
        [docViewController release];
    }

    UIDocumentInteractionController *documentInteractionController = nil;
    documentInteractionController = [UIDocumentInteractionController interactionControllerWithURL:nsFileUrl];

    UIViewController *qtUIViewController = topViewController();
    if (qtUIViewController!=nil) {
        docViewController = [[DocViewController alloc] init];

        docViewController.requestId = requestId;
        // we need this to be able to execute handleDocumentPreviewDone() method,
        // when preview was finished
        docViewController.mIosShareUtils = this;

        [qtUIViewController addChildViewController:docViewController];
        documentInteractionController.delegate = docViewController;
        // [documentInteractionController presentPreviewAnimated:YES];
        if (![documentInteractionController presentPreviewAnimated:YES]) {
            emit shareError(0, QString("No App found to open: %1").arg(filePath));
        }
    }
}

void IosShareUtils::viewFile(const QString &filePath, const QString &title, const QString &mimeType, const int &requestId) {
#pragma unused (title, mimeType)

    sendFile(filePath, title, mimeType, requestId, false);
}

void IosShareUtils::editFile(const QString &filePath, const QString &title, const QString &mimeType, const int &requestId) {
#pragma unused (title, mimeType)

    sendFile(filePath, title, mimeType, requestId, false);
}

/* ************************************************************************** */

void IosShareUtils::handleDocumentPreviewDone(const int &requestId)
{
    // documentInteractionControllerDidEndPreview
    qDebug() << "handleShareDone: " << requestId;
    emit shareFinished(requestId);
}

void IosShareUtils::handleFileUrlReceived(const QUrl &url)
{
    if (url.isEmpty()) {
        qWarning() << "handleFileUrlReceived: we got an empty URL";
        emit shareError(0, "Empty URL received");
        return;
    }

    // Resolve to a real local path with toLocalFile() to handles the file:// scheme and percent-decoding
    const QString localPath = url.isLocalFile() ? url.toLocalFile() : url.toString();
    qDebug() << "IosShareUtils handleFileUrlReceived:" << url.toString() << "->" << localPath;

    if (QFileInfo::exists(localPath)) {
        // iOS delivers the files directly into the app's Inbox
        emit fileReceived(localPath);
    } else {
        qWarning() << "handleFileUrlReceived: file does not exist:" << localPath;
        emit shareError(0, QString("File does not exist: %1").arg(localPath));
    }
}

/* ************************************************************************** */
