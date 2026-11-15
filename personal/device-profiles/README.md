# 裝置首頁設定

每台裝置使用一份 JSON，記錄機型與首頁版本，透過 Git 歷史管理主題、字體、App 排序與快捷鍵。電視提供受 WRITE_SECURE_SETTINGS 保護的設定匯出入口，已配對的 Mac 負責 Git 同步。設定頁可建立及還原本機備份，也可查看最近同步的提交。

先將 TV_HOST 與 TV_ADB_BINARY 設為目標電視的已配對連線，在 repository 根目錄執行。

```bash
tools/venv/bin/python -I personal/device-profiles/profiles.py export chromecast-4k
tools/venv/bin/python -I personal/device-profiles/profiles.py sync chromecast-4k
```

export 更新設定檔。sync 會提交該裝置的設定檔並推送目前分支到 origin，成功後將提交編號記回電視。

還原前，電視需具備設定檔列出的壁紙素材及相容的個人版首頁。

```bash
tools/venv/bin/python -I personal/device-profiles/profiles.py restore chromecast-4k
```

還原會檢查格式、機型、偏好型別及素材，使用原子檔案寫入與持久還原紀錄，讓中斷的操作可於下次開啟首頁時完成。

設定檔採首頁欄位白名單。Wi-Fi 與影音帳密、NAS 路徑、裝置序號及系統安全設定由各裝置本機保存。同步工具另會拒絕含網路位址、URL 或 email 的設定檔。

舊版首頁可用 --theme-only 擷取主題，JSON 會標記 scope 為 theme-only，並記錄擷取日期。完成該台個人版更新後，可改為完整首頁設定匯出。

同步工具的公開資料檢查可用既有 Python 環境驗證。

```bash
tools/venv/bin/python -m unittest discover -s personal/device-profiles -p 'test_*.py'
```
