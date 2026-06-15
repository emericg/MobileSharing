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
#include <QFile>
#include <QFileInfo>
#include <QDateTime>

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

    connectResult = connect(mPlatformShareUtils, &PlatformShareUtils::fileReceived, this, &MobileSharing::onFileReceived);
    Q_ASSERT(connectResult);

    Q_UNUSED(connectResult)

    // Module-owned temporary cache directory (cache/MobileSharing) with incoming/ and outgoing/ subdirs.
    mWorkingDir = QStandardPaths::writableLocation(QStandardPaths::CacheLocation) + "/MobileSharing";
    mIncomingDir = mWorkingDir + "/incoming";
    mOutgoingDir = mWorkingDir + "/outgoing";

    // Session-scoped: wipe the whole directory, at the earliest point before any file can be received or sent
    QDir(mWorkingDir).removeRecursively();

    QDir().mkpath(mIncomingDir);
    //Dir().mkpath(mOutgoingDir); // created on demand by the platform layer when a file is shared with move/copy

    // Self-drive incoming-intent processing so the host do not require a custom QGuiApplication subclass:
    // react to app-state changes, and also handle the case where the app is already active
    // by the time this object is created (deferred so QML signal handlers are wired up first).
    connect(qApp, &QGuiApplication::applicationStateChanged, this, &MobileSharing::onApplicationStateChanged);

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
        mPlatformShareUtils->checkPendingIntents(mIncomingDir);
    }
}

/* ************************************************************************** */

void MobileSharing::discardFileReceived(const QString &filePath)
{
    QString canonicalDir = QDir(mWorkingDir).canonicalPath();
    QString canonicalFile = QFileInfo(filePath).canonicalFilePath();

    // Only allow deleting files inside our own cache dir
    if (!canonicalDir.isEmpty() && canonicalFile.startsWith(canonicalDir + '/'))
    {
        if (!QFile::remove(canonicalFile))
        {
            qWarning() << "discardFileReceived() failed to remove" << canonicalFile;
        }
    }
    else
    {
        qWarning() << "discardFileReceived() refusing to delete outside cache dir:" << filePath;
    }
}

/* ************************************************************************** */

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

void MobileSharing::sendFile(const QString &filePath, const QString &title, const QString &mimeType, const int &requestId, bool move)
{
    mPlatformShareUtils->sendFile(filePath, title, mimeType, requestId, move);
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

/* ************************************************************************** */

void MobileSharing::onFileReceived(const QString &filePath)
{
    // Every received path is a file the application owns inside our cache dir.
    // - Android's platform layer already copied it there, so this is a no-op.
    // - iOS delivers it into Documents/Inbox, so move it into our cache dir.

    QString path = filePath;
    QDir incomingDir(mIncomingDir);
    const QString canonicalIncoming = incomingDir.canonicalPath();

    if (canonicalIncoming.isEmpty() || !QFileInfo(path).canonicalFilePath().startsWith(canonicalIncoming + '/'))
    {
        QString dest = incomingDir.filePath(QFileInfo(path).fileName());
        if (QFileInfo::exists(dest))
        {
            // Avoid clobbering a previous file of the same name
            dest = incomingDir.filePath(QString::number(QDateTime::currentMSecsSinceEpoch()) + '_' + QFileInfo(path).fileName());
        }

        // Move it in our incoming folder ('deleting' the OS-provided original, e.g. the iOS Inbox copy).
        if (QFile::rename(path, dest))
        {
            path = dest;
        }
        else
        {
            qWarning() << "onFileReceived() failed to move into shared cache dir, emitting original:" << path;
        }
    }

    Q_EMIT fileReceived(path);
}

/* ************************************************************************** */
