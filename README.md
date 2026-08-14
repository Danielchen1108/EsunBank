# 社群媒體系統

一個簡易的社群媒體平台。使用者以手機號碼註冊登入後，可以發文與留言。

採 Vue.js + Spring Boot + MySQL 三層式架構，資料庫存取一律透過 Stored Procedure。

---

## 功能

- **註冊** — 以手機號碼建立帳號，密碼加鹽雜湊後儲存
- **登入驗證** — 只有登入的使用者可以發文或留言
- **發文** — 新增、列出所有發文（一併帶出留言）、編輯、刪除
- **留言** — 針對發文新增留言，留言顯示在所屬發文下方（預設最新 3 則，可展開全部）

---

## 技術棧

| 層 | 技術 |
|---|---|
| 前端 | Vue 3.5 · Vue Router 4 · Vite 8 |
| 後端 | Spring Boot 4.1 · Java 17 · Spring Security + JWT · Maven |
| 資料庫 | MySQL 8 · 9 支 Stored Procedure |

後端分為展示層、業務層、資料層、共用層四個 package，各自的職責與依賴限制寫在 `package-info.java`。

---

## 執行

需要 JDK 17、MySQL 8、Node 20.19+。Maven 用專案內附的 wrapper，不必另外安裝。

**1. 建立資料庫**

```bash
mysql -u root -p < DB/01_DDL.sql
mysql -u root -p < DB/02_DDL_stored_procedures.sql
mysql -u root -p < DB/03_DML.sql
```

**2. 設定環境變數（多數情況可略過）**

三個變數都有可用的預設值，`root` 帳號無密碼的環境**可以直接跳到第 3 步**。

| 變數 | 未設定時 |
|---|---|
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | 空字串 |
| `APP_JWT_SECRET` | 啟動時隨機產生，並在日誌提醒 |

需要調整時，複製 `.env.example` 填值後載入（Spring Boot 不會自動讀 `.env`，
必須先載入環境變數）：

```bash
cp .env.example .env
set -a && source .env && set +a
```

或直接指定：

```bash
export DB_PASSWORD='你的 MySQL 密碼'
```

**關於 `APP_JWT_SECRET`：不必自行產生金鑰也能完整操作系統。**
未設定時每次啟動換一把隨機金鑰，唯一影響是重啟後需重新登入一次。

範例檔中此欄**刻意留空、不附預設金鑰**：HS256 簽發與驗證使用同一把對稱金鑰，
金鑰若隨原始碼散布，任何取得程式碼的人都能簽出合法憑證、冒用任意身分。
要讓憑證跨重啟有效，請自行產生：`openssl rand -base64 48`。

**3. 啟動後端**

```bash
cd Backend && ./mvnw spring-boot:run
```

**4. 啟動前端**（另開一個終端機）

```bash
cd Frontend && npm install && npm run dev
```

開啟 **http://localhost:5173** 即可操作。

> 若該埠已被佔用，Vite 會自動改用下一個可用埠，以終端機印出的網址為準。

---

## 測試帳號

種子資料建立了三個帳號，密碼皆為 `Test1234`：

`0912345678` · `0922333444` · `0933555666`

---

## 測試

```bash
cd Backend && ./mvnw test
```

需要連線資料庫的整合測試預設跳過，啟用方式見各測試類別的說明。

---

## 專案結構

```
Backend/     Spring Boot 後端（四層分層）
Frontend/    Vue 前端
DB/          DDL / Stored Procedure / 種子資料
Document/    需求文件（不納入版控）
```
