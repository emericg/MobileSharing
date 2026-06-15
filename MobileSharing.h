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

/*
 * This project is based on ideas from:
 * - https://github.com/ekke/ekkesSHAREexample
 * - https://www.qt.io/blog/2017/12/01/sharing-files-android-ios-qt-app
 * - https://www.qt.io/blog/2018/01/16/sharing-files-android-ios-qt-app-part-2
 * - https://www.qt.io/blog/2018/02/06/sharing-files-android-ios-qt-app-part-3
 * - https://www.qt.io/blog/2018/11/06/sharing-files-android-ios-qt-app-part-4
 * also inspired by:
 * - http://blog.lasconic.com/share-on-ios-and-android-using-qml/
 * - https://github.com/lasconic/ShareUtils-QML
 * also inspired by:
 * - https://www.androidcode.ninja/android-share-intent-example/
 * - https://www.calligra.org/blogs/sharing-with-qt-on-android/
 * - https://stackoverflow.com/questions/7156932/open-file-in-another-app
 * - http://www.qtcentre.org/threads/58668-How-to-use-QAndroidJniObject-for-intent-setData
 * - https://stackoverflow.com/questions/5734678/custom-filtering-of-intent-chooser-based-on-installed-android-package-name
 */

#ifndef MOBILESHARING_H
#define MOBILESHARING_H
/* ************************************************************************** */

#include <QtQml/qqmlregistration.h>
#include <QObject>
#include <QString>
#include <QMimeDatabase>
#include <QUrl>
#include <QDebug>

/* ************************************************************************** */

/*!
 * \brief The PlatformShareUtils class
 */
class PlatformShareUtils : public QObject
{
    Q_OBJECT

signals:
    void shareEditDone(int requestCode);
    void shareFinished(int requestCode);
    void shareNoAppAvailable(int requestCode);
    void shareError(int requestCode, QString message);
    void fileReceived(QString filePath);

public:
    PlatformShareUtils(QObject *parent = nullptr) : QObject(parent) { };
    virtual ~PlatformShareUtils() = default;

    virtual void checkPendingIntents(const QString &workingDirPath) {
        qDebug() << "checkPendingIntents" << workingDirPath;
    }
    virtual bool checkMimeTypeView(const QString &mimeType) {
        qDebug() << "check view for" << mimeType;
        return true;
    }
    virtual bool checkMimeTypeEdit(const QString &mimeType) {
        qDebug() << "check edit for" << mimeType;
        return true;
    }

    virtual void sendText(const QString &text, const QString &subject, const QUrl &url) {
        qDebug() << text << subject << url.url();
    }
    virtual void sendFile(const QString &filePath, const QString &title, const QString &mimeType, const int &requestId, bool move) {
        qDebug() << filePath << " - " << title << "requestId: " << requestId << " - " << mimeType << " - " << move;
    }
    virtual void viewFile(const QString &filePath, const QString &title, const QString &mimeType, const int &requestId) {
        qDebug() << filePath << " - " << title << "requestId: " << requestId << " - " << mimeType;
    }
    virtual void editFile(const QString &filePath, const QString &title, const QString &mimeType, const int &requestId) {
        qDebug() << filePath << " - " << title << "requestId: " << requestId << " - " << mimeType;
    }

    const QMimeDatabase &getMimeDatabase() const {
        return m_mimeDatabase;
    }

private:
    QMimeDatabase m_mimeDatabase;
};

/* ************************************************************************** */

/*!
 * \brief The MobileSharing class
 */
class MobileSharing : public QObject
{
    Q_OBJECT
    QML_ELEMENT

    PlatformShareUtils *mPlatformShareUtils = nullptr;
    bool mPendingIntentsChecked = false;

    QString mWorkingDir;  //!< module-owned cache root (cache/MobileSharing), wiped at startup
    QString mIncomingDir; //!< subdir where received files are copied (cache/MobileSharing/incoming)
    QString mOutgoingDir; //!< subdir where sending files are copied (cache/MobileSharing/outgoing)

private slots:
    void onApplicationStateChanged(Qt::ApplicationState state);

signals:
    void shareEditDone(int requestCode);
    void shareFinished(int requestCode);
    void shareNoAppAvailable(int requestCode);
    void shareError(int requestCode, QString message);

    /*!
     * \brief fileReceived signal, emitted once per incoming file.
     * \param filePath: The path to the file received.
     *
     * The path always points to a real, readable file the app owns (a copy in the module's cache subdir).
     * The cache will be deleted next time the app starts, so copy/move it into your own storage if you want to keep it.
     */
    void fileReceived(const QString &filePath);

public slots:
    void onShareEditDone(int requestCode);
    void onShareFinished(int requestCode);
    void onShareNoAppAvailable(int requestCode);
    void onShareError(int requestCode, const QString &message);
    void onFileReceived(const QString &filePath);

public:
    explicit MobileSharing(QObject *parent = nullptr);

    Q_INVOKABLE bool checkMimeTypeView(const QString &mimeType);
    Q_INVOKABLE bool checkMimeTypeEdit(const QString &mimeType);
    const QMimeDatabase &getMimeDatabase() const;

    //! Explicitely reject an incoming file: delete the cached copy (only within our cache subdir).
    Q_INVOKABLE void discardFileReceived(const QString &filePath);
    Q_INVOKABLE void sendText(const QString &text, const QString &subject, const QUrl &url);

    Q_INVOKABLE void sendFile(const QString &filePath, const QString &title, const QString &mimeType, const int &requestId, bool move = false);
    Q_INVOKABLE void viewFile(const QString &filePath, const QString &title, const QString &mimeType, const int &requestId);
    Q_INVOKABLE void editFile(const QString &filePath, const QString &title, const QString &mimeType, const int &requestId);
};

/* ************************************************************************** */
#endif // MOBILESHARING_H
