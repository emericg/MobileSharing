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

#include "MobileSharing.h"

#if defined(Q_OS_IOS)
#include "MobileSharing_ios.h"
#elif defined(Q_OS_ANDROID)
#include "MobileSharing_android.h"
#endif

#include <QGuiApplication>
#include <QStandardPaths>
#include <QDir>

/* ************************************************************************** */

MobileSharing::MobileSharing(QObject *parent) : QObject(parent)
{
#if defined(Q_OS_IOS)
    mPlatformShareUtils = new IosShareUtils(this);
#elif defined(Q_OS_ANDROID)
    mPlatformShareUtils = new AndroidShareUtils(this);
#else
    mPlatformShareUtils = new PlatformShareUtils(this);
#endif

    bool connectResult = connect(mPlatformShareUtils, &PlatformShareUtils::shareEditDone, this, &MobileSharing::onShareEditDone);
    Q_ASSERT(connectResult);

    connectResult = connect(mPlatformShareUtils, &PlatformShareUtils::shareFinished, this, &MobileSharing::onShareFinished);
    Q_ASSERT(connectResult);

    connectResult = connect(mPlatformShareUtils, &PlatformShareUtils::shareNoAppAvailable, this, &MobileSharing::onShareNoAppAvailable);
    Q_ASSERT(connectResult);

    connectResult = connect(mPlatformShareUtils, &PlatformShareUtils::shareError, this, &MobileSharing::onShareError);
    Q_ASSERT(connectResult);

    connectResult = connect(mPlatformShareUtils, &PlatformShareUtils::fileUrlReceived, this, &MobileSharing::onFileUrlReceived);
    Q_ASSERT(connectResult);

    connectResult = connect(mPlatformShareUtils, &PlatformShareUtils::fileReceivedAndSaved, this, &MobileSharing::onFileReceivedAndSaved);
    Q_ASSERT(connectResult);

    Q_UNUSED(connectResult)

    // Self-drive incoming-intent processing so the host needs no custom QGuiApplication
    // subclass: react to app-state changes, and also handle the case where the app is
    // already active by the time this object is created (deferred so QML signal handlers
    // are wired up first).
    connect(qApp, &QGuiApplication::applicationStateChanged,
            this, &MobileSharing::onApplicationStateChanged);

    if (qApp->applicationState() == Qt::ApplicationActive)
    {
        QMetaObject::invokeMethod(this, "onApplicationStateChanged", Qt::QueuedConnection,
                                  Q_ARG(Qt::ApplicationState, Qt::ApplicationActive));
    }
}

/* ************************************************************************** */

void MobileSharing::onApplicationStateChanged(Qt::ApplicationState state)
{
    if (state == Qt::ApplicationActive && !mPendingIntentsChecked)
    {
        mPendingIntentsChecked = true;

        // Working dir for received files copied via InputStream (content:// fallback)
        QString workingDir = QStandardPaths::writableLocation(QStandardPaths::TempLocation);
        if (!workingDir.isEmpty()) QDir().mkpath(workingDir);

        mPlatformShareUtils->checkPendingIntents(workingDir);
    }
}

/* ************************************************************************** */

void MobileSharing::checkPendingIntents(const QString &workingDirPath)
{
    mPlatformShareUtils->checkPendingIntents(workingDirPath);
}

bool MobileSharing::checkMimeTypeView(const QString &mimeType)
{
    return mPlatformShareUtils->checkMimeTypeView(mimeType);
}

bool MobileSharing::checkMimeTypeEdit(const QString &mimeType)
{
    return mPlatformShareUtils->checkMimeTypeEdit(mimeType);
}

void MobileSharing::sendText(const QString &text, const QString &subject, const QUrl &url)
{
    mPlatformShareUtils->sendText(text, subject, url);
}

void MobileSharing::sendFile(const QString &filePath, const QString &title, const QString &mimeType, const int &requestId)
{
    mPlatformShareUtils->sendFile(filePath, title, mimeType, requestId);
}

void MobileSharing::viewFile(const QString &filePath, const QString &title, const QString &mimeType, const int &requestId)
{
    mPlatformShareUtils->viewFile(filePath, title, mimeType, requestId);
}

void MobileSharing::editFile(const QString &filePath, const QString &title, const QString &mimeType, const int &requestId)
{
    mPlatformShareUtils->editFile(filePath, title, mimeType, requestId);
}

const QMimeDatabase &MobileSharing::getMimeDatabase() const
{
    return mPlatformShareUtils->getMimeDatabase();
}

/* ************************************************************************** */

void MobileSharing::onShareEditDone(int requestCode)
{
    Q_EMIT shareEditDone(requestCode);
}

void MobileSharing::onShareFinished(int requestCode)
{
    Q_EMIT shareFinished(requestCode);
}

void MobileSharing::onShareNoAppAvailable(int requestCode)
{
    Q_EMIT shareNoAppAvailable(requestCode);
}

void MobileSharing::onShareError(int requestCode, const QString &message)
{
    Q_EMIT shareError(requestCode, message);
}

void MobileSharing::onFileUrlReceived(const QString &url)
{
    Q_EMIT fileUrlReceived(url);
}

void MobileSharing::onFileReceivedAndSaved(const QString &url)
{
    Q_EMIT fileReceivedAndSaved(url);
}

/* ************************************************************************** */
