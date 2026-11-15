# 兩台首頁的自動驗證

在 repository 根目錄執行。使用既有 tools/venv、JDK 17 或 21、Android SDK 34，以及已配對的 Google ADB 連線。

```bash
bash personal/verification/verify.sh
```

預設只處理 Chromecast。依序執行 repository Python 測試、設定檔隱私測試、驗證腳本自測，然後執行 Chromecast JVM 測試、lint、release 建置與實機導覽。Sony 支援保留，但私人設定中的 paused 為 true 時，在發送任何 Sony 連線或裝置指令前即停止。已安裝 APK 必須與剛建置的 APK 完全一致，避免把舊版本畫面誤當新程式通過。只要有一項失敗就回傳非零狀態；實機失敗時仍會處理其他已選取且未暫停的裝置。

若要更新 Chromecast 後驗證，加上安裝旗標。

```bash
bash personal/verification/verify.sh --install
```

安裝前核對 Chromecast 型號或 Sony 型號加 KM-55X85L SKU，備份目前 APK、套件／設定與首頁專用外部檔案，再以 pm install -r 覆蓋對應個人版 APK。相同的 APK 會略過重裝。使用既有 theme_restart.py 保留與確認原有 Home 按鍵輔助服務，不停用套件、不清資料，不觸碰 VLC／NAS 設定。備份在 backup/verification-*/，私有 shared preferences 仍由 Android 覆蓋更新保留；Chromecast 另匯出完整首頁設定檔。

## 私人連線設定

複製 [config.example.json](config.example.json) 到 work/tv-verification.json，填入兩台目前的連線位址、adb、JAVA_HOME 與 ANDROID_HOME。這個檔案、報告及截圖都在 Git 忽略目錄。可用 --config 指定其他私人檔案。

恢復 Sony 操作後，將私人設定的 devices.sony.paused 改成 false，再明確指定 --device sony 或 --device all。

Chromecast 的 devices.chromecast 可額外填 mdns_name，值為 adb mdns services 列出的已配對服務實例名稱。腳本只更新該名稱對應的 TLS 連線位址，不選擇其他電視或配對服務。這能處理無線偵錯連接埠改變；配對及授權仍須先完成。

## 實機檢查

腳本也會確認 Chromecast 字體／每週壁紙／備份選單、系統資訊刷新及 20 張素材清單，或 Sony 六鍵／本機影片設定頁。這些選單的檢查不更改原設定。

腳本使用 D-pad 導覽設定入口與「景深壁紙」選單，驗證首頁隱藏後停止動畫、往下縮小拉遠、往上回復、圖片邊緣完整覆蓋、停留時沒有景深動畫更新、關閉功能後保持原圖，以及測試後原有設定、排序、主題、停用清單與輔助服務保存。它會暫時切換景深設定，最後還原原選項。若中途斷線，work/tv-verification-pending 保留還原紀錄，下次連上先恢復；報告會標示待還原。

會將電視喚醒並切到首頁，結束時停在首頁。測試不按電源、音量或影音 App 快捷鍵，也不啟動影音播放。實體按鍵、語音收音、實際聲音、CEC 及廠商 HDMI 行為仍需人工驗證，不能用注入按鍵宣稱通過。

結果在 work/verification/verification-*/result.json，包含兩台個別結果、縮放數值、檢查項目、完整建置／測試日誌及前後畫面；work/verification-latest.txt 指向最近一次。失敗不會標為通過。截圖可能包含 Wi-Fi 名稱與位置，請只留本機。

## 常用選項

```bash
# 明確選取 Sony；私人設定 paused 必須為 false
bash personal/verification/verify.sh --device sony --install

# 兩個版本只做建置與本機測試，不接觸電視
bash personal/verification/verify.sh --device all --offline

# 沿用已建置且已安裝的 APK，只跑 Chromecast 實測
bash personal/verification/verify.sh --skip-build --skip-host-tests
```

兩台各有獨立程序與目標參數。型號不符、未授權、連線中斷、舊 APK、沒有壁紙或無法進入多列 App 網格都會報錯；關閉系統動畫／減少動態效果時，景深測試也會明確報錯，不會更改該設定。手動停用的景深選項可以暫時測試後還原。

目前 Chromecast 個人版 versionCode 18、Sony 影片版 versionCode 19 均已完成景深實機自動驗證。兩台的設定與 App 排序保存檢查均通過，Sony 預設仍沿用自然照片及其六鍵／影片設定。
