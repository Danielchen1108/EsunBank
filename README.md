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

**2. 設定環境變數**

```bash
export DB_USERNAME=root
export DB_PASSWORD='你的 MySQL 密碼'      # 沒有密碼就寫 export DB_PASSWORD=
export APP_JWT_SECRET=$(openssl rand -base64 48)
```

`APP_JWT_SECRET` 不設也能啟動，但每次重啟會換一把隨機金鑰，先前登入的憑證會失效。

**3. 啟動後端**

```bash
cd Backend && ./mvnw spring-boot:run
```

**4. 啟動前端**（另開一個終端機）

```bash
cd Frontend && npm install && npm run dev
```

開啟 **http://localhost:5174** 即可操作。

> 前端刻意不用 Vite 預設的 5173 埠，原因寫在 `Frontend/vite.config.js`。

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
