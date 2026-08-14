# 社群媒體系統

一個具備註冊、登入驗證、發文（新增／列出／編輯／刪除）與留言功能的社群媒體系統，採 Vue.js + Spring Boot + MySQL 三層式架構，資料庫存取一律透過 Stored Procedure。

---

## 技術棧

| 項目 | 本專案採用 | 位置 |
|---|---|---|
| 三層式架構 | Vite Dev Server／靜態檔 + Spring Boot 內嵌 Tomcat + MySQL | `Frontend/`、`Backend/`、`DB/` |
| 後端分層 | `presentation`／`business`／`data`／`common` 四個 package，各有 `package-info.java` 說明職責與依賴限制 | `Backend/src/main/java/com/esunbank/social/` |
| 前端 | Vue 3.5 + Vue Router 4 + Vite 8 | `Frontend/` |
| 後端 | Spring Boot 4.1.0 / Java 17 | `Backend/pom.xml` |
| API 風格 | 資源集合 `/api/posts`、`/api/posts/{id}/comments`，動作由 HTTP 方法表達，路徑不含動詞 | 見下方端點一覽 |
| 建置工具 | Maven（附 Maven Wrapper 3.9.16，不需另裝 Maven） | `Backend/mvnw` |
| 資料庫 | MySQL 8 | `DB/01_DDL.sql` |
| 資料存取 | 8 支 SP；資料層以 `CallableStatement` 呼叫，不寫任何 SQL 字串 | `DB/02_DDL_stored_procedures.sql`、`Backend/.../data/repository/` |
| 跨資料表交易 | `sp_post_delete` 於顯式交易內同時更新 `post` 與 `comment`，並以 `EXIT HANDLER` 回滾 | `DB/02_DDL_stored_procedures.sql` |
| 資料庫腳本 | `01_DDL.sql`、`02_DDL_stored_procedures.sql`、`03_DML.sql` | `DB/` |
| SQL Injection 防護 | 參數綁定（`CallableStatement`）+ SP 內全為靜態語句，不使用 `PREPARE`/`CONCAT` 組動態 SQL | 資料層 + SP |
| XSS 防護 | 前端一律用 `{{ }}` 插值輸出（自動跳脫），全案不使用 `v-html` | `Frontend/src/views/` |
| 密碼儲存 | Spring Security `BCryptPasswordEncoder`（自動產生 salt，輸出 60 字元存於 `VARCHAR(72)`）| `Backend/.../common/config/SecurityConfig.java` |
| 身分驗證 | Spring Security + JWT，`SecurityConfig` deny-by-default | `Backend/.../common/security/` |

---

## 快速開始

### 前置需求

| 項目 | 版本 | 備註 |
|---|---|---|
| JDK | 17 | Spring Boot 4.1 要求 17 以上 |
| MySQL | 8.0 以上 | 開發驗證於 8.4.4 |
| Node.js | 20.19+ 或 22.12+ | Vite 8 的要求 |

Maven 不需另外安裝，專案內附 Maven Wrapper（`./mvnw`）。

以下指令皆從**專案根目錄**執行。

### 1. 建立資料庫

依序執行三支腳本（順序不可對調，`03` 會呼叫 `02` 建立的 Stored Procedure）：

```bash
mysql -u root -p < DB/01_DDL.sql
mysql -u root -p < DB/02_DDL_stored_procedures.sql
mysql -u root -p < DB/03_DML.sql
```

- `01_DDL.sql`：建立 `esunbank_social` 資料庫與 `user`／`post`／`comment` 三張表。**開頭有 `DROP DATABASE IF EXISTS esunbank_social`**，重跑等於整個重建。
- `02_DDL_stored_procedures.sql`：建立 8 支 Stored Procedure。
- `03_DML.sql`：寫入種子資料，最後會印出三段驗證查詢的結果（發文列表、軟刪除連動狀態、密碼皆為 60 字元雜湊）。

### 2. 設定環境變數

```bash
export DB_USERNAME=root
export DB_PASSWORD='你的 MySQL 密碼'        # root 沒有密碼時寫成 export DB_PASSWORD=
export APP_JWT_SECRET=$(openssl rand -base64 48)
```

- 資料庫連線預設為 `localhost:3306/esunbank_social`（`Backend/src/main/resources/application.properties`）。
- `APP_JWT_SECRET` 是 JWT 的簽章金鑰（HS256 需至少 32 位元組）。**刻意不給預設值**：對稱金鑰一旦寫進版控，任何拿到原始碼的人都能自行簽出合法憑證。
- **不設 `APP_JWT_SECRET` 也能正常啟動**——此時後端改用啟動時隨機產生的金鑰並記錄一行 WARN，功能完全不受影響；唯一代價是後端重新啟動後先前簽發的 token 失效，需重新登入一次。

### 3. 啟動後端

環境變數要與啟動指令在同一個終端機 session：

```bash
cd Backend
./mvnw spring-boot:run
```

啟動於 `http://localhost:8080`。確認 AP 與資料庫都通了——用種子帳號登入，
這一步會實際經過 Spring Boot → Stored Procedure → MySQL 全程：

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"phone":"0912345678","password":"Test1234"}'
```

回傳 `{"userId":1,"userName":"陳大文","token":"eyJ..."}` 即表示三層都正常。
若資料庫未建立或連線設定有誤，這裡會直接失敗。

### 4. 啟動前端

另開一個終端機：

```bash
cd Frontend
npm install
npm run dev
```

### 5. 開啟畫面

<http://localhost:5174>

**埠號是 5174，不是 Vite 預設的 5173**——開發機上另一個專案曾在 `localhost:5173` 註冊 Service Worker，會攔截請求並回舊的快取內容；Service Worker 的作用域綁定 origin（含埠號），換埠即可避開。設定與理由見 `Frontend/vite.config.js`（`strictPort: true`，埠被占用時會直接報錯而不會自動換號）。

前端 dev server 同時擔任 Web Server 角色，會把 `/api` 反向代理到 `localhost:8080`，所以只需要開 5174 這一個網址。

畫面路由：`/register` 註冊、`/login` 登入、`/posts` 發文與留言（列出／新增／編輯／刪除與留言都在這一頁）、`/health` 健康檢查。開啟根路徑會導向 `/register`。可用下方的種子帳號直接登入，或先在 `/register` 註冊一個新帳號。

---

## 種子帳號

`DB/03_DML.sql` 建立三個帳號，**明碼密碼皆為 `Test1234`**（資料庫內存的是 BCrypt 雜湊值，非明碼）：

| 手機號碼（帳號） | 密碼 | 使用者名稱 |
|---|---|---|
| `0912345678` | `Test1234` | 陳大文 |
| `0922333444` | `Test1234` | 林小明 |
| `0933555666` | `Test1234` | 王美麗 |

另有 4 篇可見發文、4 則留言，以及 1 篇「已被 `sp_post_delete` 軟刪除」的發文（含 2 則連帶標記的留言）——後者用於驗證交易連動與列表過濾，正常操作下不會出現在畫面上。

也可以用 curl 直接驗證後端（登入 → 發文 → 列出發文）：

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"phone":"0912345678","password":"Test1234"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -s -X POST http://localhost:8080/api/posts -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"content":"測試發文"}'
curl -s http://localhost:8080/api/posts -H "Authorization: Bearer $TOKEN"
```

---

## API 端點

| 方法 | 路徑 | 用途 | 需登入 | 成功狀態 |
|---|---|---|---|---|
| POST | `/api/auth/register` | 註冊 | — | 201 |
| POST | `/api/auth/login` | 登入取得 JWT | — | 200 |
| GET | `/api/posts` | 列出所有未刪除發文 | ✅ | 200 |
| POST | `/api/posts` | 新增發文 | ✅ | 201 |
| PUT | `/api/posts/{postId}` | 編輯發文 | ✅ | 200 |
| DELETE | `/api/posts/{postId}` | 刪除發文，軟刪除並連動留言 | ✅ | 204 |
| POST | `/api/posts/{postId}/comments` | 針對發文新增留言 | ✅ | 201 |

需登入的端點以 `Authorization: Bearer <token>` 帶入登入取得的憑證。發文者／留言者一律由後端從憑證取得，不接受由請求指定。

---

## 測試

```bash
cd Backend
./mvnw test
```

實際結果：

```
Tests run: 112, Failures: 0, Errors: 0, Skipped: 17
BUILD SUCCESS
```

跳過的 17 個是**需要真實資料庫**的整合測試（驗證 Stored Procedure 內部的軟刪除過濾、跨表交易連動、注入字串經參數綁定後原樣存為文字）。預設跳過是刻意的：沒有資料庫的環境跑 `./mvnw test` 不應該失敗。其餘 95 個測試不需要資料庫。

### 執行需要資料庫的整合測試（可選）

這 17 個測試的連線字串固定寫在測試類別內：F004 用 `127.0.0.1:3309`、F005 用 `127.0.0.1:3310`，帳號 `root`、無密碼。若這兩個埠上有已載入 `DB/` 三支腳本的 MySQL，直接執行：

```bash
cd Backend
F004_IT_DB=true ./mvnw test -Dtest=PostRepositoryIntegrationTest                              # 9 個
./mvnw test -Df005.integration=true -Dtest='CommentRepositoryIntegrationTest,CommentEndToEndTest'  # 8 個
```

若手上只有 3306 的 MySQL，可另起一個用完即丟的實例（不影響原本的資料）：

```bash
mysqld --initialize-insecure --datadir=/tmp/esun-it
mysqld --datadir=/tmp/esun-it --port=3309 --socket=/tmp/esun-it.sock &
mysql -h 127.0.0.1 -P 3309 -u root < DB/01_DDL.sql
mysql -h 127.0.0.1 -P 3309 -u root < DB/02_DDL_stored_procedures.sql
mysql -h 127.0.0.1 -P 3309 -u root < DB/03_DML.sql
```

（跑 F005 的兩個類別時把 `3309` 換成 `3310`。若 `mysqld` 不在標準安裝路徑，兩行 `mysqld` 需加上 `--basedir=<MySQL 安裝目錄>`。）

---

## 專案結構

```
.
├── DB/                                    DDL / DML 腳本
│   ├── 01_DDL.sql                         資料庫、三張表、索引與約束
│   ├── 02_DDL_stored_procedures.sql       8 支 Stored Procedure（含 sp_post_delete 的交易）
│   └── 03_DML.sql                         種子資料 + 驗證查詢
│
├── Backend/                               Application Server（Spring Boot 4.1 / Java 17）
│   └── src/
│       ├── main/java/com/esunbank/social/
│       │   ├── presentation/              展示層：controller、dto、請求驗證
│       │   ├── business/                  業務層：service、業務規則
│       │   ├── data/                      資料層：repository，只呼叫 Stored Procedure
│       │   ├── common/                    共用層：config、security（JWT）、exception
│       │   └── SocialApplication.java
│       ├── main/resources/                application.properties（連線與埠號設定）
│       └── test/java/                     112 個測試，目錄結構與 main 對應
│
├── Frontend/                              Web Server（Vue 3.5 + Vite 8）
│   └── src/
│       ├── api/client.js                  後端呼叫集中於此，自動附帶 JWT
│       ├── views/                         註冊／登入／發文留言／健康檢查四個畫面
│       └── router/index.js
│
└── Document/                              需求文件存放處（內容不納入版控）
```

四個 package 的職責與依賴限制寫在各自的 `package-info.java`：依賴方向為 `presentation → business → data`，共用層可被三者依賴、但不反向依賴任何一層。

---

## 設計決策摘要

以下是需求未明確規範、由本專案自行決定的部分，列出決定與依據。

**用 Spring JDBC 而非 JPA。** 需求指定「透過 Stored Procedure 存取資料庫」，JPA 的核心價值（實體映射、JPQL、自動產生 SQL）在這個前提下用不上，反而多一層抽象遮蔽實際執行的呼叫。資料層以 Spring JDBC 的 `JdbcTemplate` + `CallableStatement` 呼叫 SP，`Backend/src/main/` 底下沒有任何一行 SQL 字串。

**用 Stored Procedure 不等於免疫 SQL Injection。** SP 內若以 `PREPARE` + `CONCAT` 組動態 SQL 一樣會被注入。本專案的防護是兩端合起來的：應用層參數綁定 + SP 內全為靜態語句。整合測試中有一則以注入字串新增留言、驗證其原樣存為文字的案例。

**刪除發文採軟刪除，並在同一交易內連動標記留言。** 盤點全案六個寫入操作後，只有「刪除發文」會動到多張表。若改用 `ON DELETE CASCADE`，連動刪除由資料庫引擎隱式完成，應用端不存在可展示的交易，需求「需同時異動多個資料表時請實作 Transaction」就無處落地；若軟刪除只更新 `post` 單表，同樣落空。因此 `sp_post_delete` 在顯式交易內同時更新兩張表，並以 `DECLARE EXIT HANDLER FOR SQLEXCEPTION` 回滾。代價是所有讀取都必須帶 `is_deleted = FALSE` 過濾，這點在每支查詢 SP 內都有標註。

**編輯／刪除發文不檢查操作者是否為發文者。** 需求對身分驗證的規範範圍是「發文或留言」，字面上不含編輯與刪除；發文功能本身也只寫「編輯或刪除發文」而未限定對象。依「需求未要求的不實作」原則，此處只要求登入、不比對發文者。**這是刻意決策而非遺漏**：`PostController.update()` 與 `delete()` 的方法簽章裡沒有目前使用者參數（只有 `create()` 有，因為要記錄發文者），並有測試斷言「他人的發文可被編輯與刪除」，把決策固化成規格。若要改為僅限本人，加一行比對即可。

**JWT 不設有效期，也沒有登出功能。** 需求未提及有效期、更新機制與登出，依同一原則不實作。選 JWT 而非 HttpSession 的理由是需求同時指定 RESTful 風格與 Vue 前後端分離：REST 的無狀態特性與 JWT 相符，Session 為有狀態且跨域需額外處理 CSRF。

**發文列表沒有排序與分頁。** 需求未定義排序規則，`sp_post_list` 刻意不寫 `ORDER BY`，前端也不自行排序——避免實作出需求未定義的行為。分頁、搜尋同理。

**`image` 欄位保留在資料庫，但不出現在 API 契約。** 資料表定義列有 `Post.Image`／`User.Cover Image`（皆標為非必要欄位），但功能清單沒有上傳功能。因此 schema 與 SP 參數保留該欄位（依資料表定義），API 的請求與回應則不含它——沒有上傳端點卻讓前端送一個沒有來源的路徑字串是空轉。

**手機號碼只驗長度 10 碼。** 需求要求以手機號碼註冊登入，但第 2 頁的 User 表沒有這個欄位，故依表格註明的「請包含，但不限制僅能有以下」新增 `phone`。驗證做兩層：應用層給明確錯誤訊息，資料庫層加 `CHECK (CHAR_LENGTH(phone) = 10)`——`CHAR(10)` 只擋超長不擋過短，`'0912'` 會被原樣寫入而不報錯。開頭數字、國別碼等需求未定義的規則不實作。

**XSS 的防線在輸出端。** 後端原樣儲存與回傳使用者輸入（不在寫入時竄改資料），跳脫由前端負責：所有回顯一律用 `{{ }}` 插值，全案不使用 `v-html`。
