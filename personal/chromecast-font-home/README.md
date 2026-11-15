# Chromecast 字體個人版

![字體選單與即時預覽](preview.jpg)

首頁的桌面設定新增「字體」，可選原本 Outfit、系統報紙襯線與 UnifrakturCook 古英文報頭風格。在選單選取字體後可立即預覽，按確定並返回首頁後套用到首頁文字、App 名稱與待機時鐘。選擇保存在 App 私有偏好，更新 APK 與更換主題後沿用。設定頁使用易讀的系統字體。

UnifrakturCook 提供類似傳統報紙報頭的 blackletter 造型。中文字由 Android 備援字型顯示。原字型檔來自 [Google Fonts](https://github.com/google/fonts/tree/main/ofl/unifrakturcook)，採 SIL Open Font License 1.1，完整授權隨 APK 保存在 [assets](app/src/main/assets/OFL-UnifrakturCook.txt)。字型檔未修改。

此建置重用 launcher 的 Java、資源與測試。公開元件 MainActivity、LauncherSettingsActivity、ClockActivity 透過別名沿用，ClockDream 保留原服務名稱，因此相同簽章的覆蓋更新可保留原有設定。首頁在建立時套用字型，RecyclerView 的新項目在附加時套用。

## 建置

在完整 repository 中，以 Android SDK 34 與 JDK 17 或 21 執行。

```bash
cd personal/chromecast-font-home
./gradlew --no-daemon --no-watch-fs --max-workers=2 testReleaseUnitTest lintRelease assembleRelease
```

輸出為 app/build/outputs/apk/release/app-release.apk，套件 com.gathin.tvhome，versionName 3.5-fonts，versionCode 15。採用與參考版相同的本機 debug 簽章。此個人版會將字型、授權與選單一同打包。

選回「原本字體」並返回首頁即可恢復原先外觀。跨版本覆蓋更新需使用相同簽章，且 versionCode 至少為目前安裝值。
