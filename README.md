# StreamAndroidScreen

Androidスマホの画面をローカルネットワーク内でHTTP、MJPEGでリアルタイム配信するものです。スマホ画面をシェアするアプリはzoomをはじめとしてたくさんあるのですが、専用アプリを使うため拡張性が低かったり、遅延があったりしたため、自分で作りました。

## How to Use
1. [Android Studio](https://developer.android.com/studio)で開いて、Androidスマホにビルドします。
2. アプリを開くと、白い画面が出ます。その時点から配信が始まっています。アプリを閉じたり、他アプリを開いたりしてもそのまま画面が配信され続けます。
3. ブラウザで、http://<AndroidスマホのIPアドレス>:8888/video_feed にアクセスすると、映像を確認できます。遅延は↓このくらいです。<img src="./img/VID_20240908_143652.gif" width="80%">
5. 配信を終了するときは、「アプリ情報→強制終了」としてください。(要改良)

## How to connect Unity
* 以下のサイトのアセットを使うとUnity上で映像を再生できます。
* ただし、現状はandroidとwindowsへのビルドしか対応していないようです。([libjpeg-turbo](https://libjpeg-turbo.virtualgl.org/)まわりの設定を変えればiOSでも使えるかもしれません。)
* Questで使えるかは未確認です。Androidでも[XREAL](https://www.xreal.com/)用アプリとしてビルドすると再生できなかったです。
