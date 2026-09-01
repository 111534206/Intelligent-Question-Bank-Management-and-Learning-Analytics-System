# 智慧題庫管理與學習分析系統
Intelligent Question Bank Management and Learning Analytics System

---

## 📖 專案簡介 (Overview)
本系統是一套結合 **AI 智能題目解析**、**教材管理**、**班級學生管理** 與 **學習分析** 的全端教育輔助平台。
提供教師上傳與管理各類型教材（Excel、PPT、PDF）、自動化匯入題庫、指派練習作業，並能結合 Google Gemini AI 模型進行智慧輔助解析與學習診斷。

---

## 🛠️ 技術架構 (Tech Stack)

### 前端 (Frontend)
- **HTML5 / CSS3 / Vanilla JavaScript (ES6+)**
- 響應式介面設計、模組化狀態管理

### 後端 (Backend)
- **Java 17**
- **Spring Boot 3.2.5**
  - Spring Web (RESTful API)
  - Spring Data JPA (Hibernate)
  - Bean Validation
- **Microsoft SQL Server** (關聯式資料庫)
- **Apache POI 5.2.5** (Excel `.xlsx` / PPT `.pptx` 解析)
- **Apache PDFBox 3.0.1** (PDF 文件解析)
- **Google Gemini API** (AI 智慧分析與出題輔助)

---

## 📁 專案目錄結構 (Project Structure)

```plaintext
.
├── backend/
│   └── questionbank/
│       ├── pom.xml                               # Maven 依賴與建置設定
│       └── src/
│           ├── main/
│           │   ├── java/com/edu/questionbank/    # 後端原始碼 (Controller, Service, Model, DTO...)
│           │   └── resources/
│           │       ├── application.properties    # 系統設定檔
│           │       └── schema.sql                # 資料庫建表腳本
│           └── test/                             # 單元測試
├── css/
│   └── style.css                                 # 前端樣式表
├── js/
│   └── app.js                                    # 前端核心邏輯
├── index.html                                    # 前端入口主頁面
├── .gitignore                                    # Git 排除清單
└── README.md                                     # 專案說明文件
```

---

## 🚀 快速開始 (Getting Started)

### 1. 前置需求 (Prerequisites)
- **JDK 17+**
- **Maven 3.8+**
- **Microsoft SQL Server** (預設連線連接埠 1433)
- (選填) Google Gemini API Key（若無金鑰將自動使用 Mock 測試模式）

### 2. 資料庫設定
1. 在 SQL Server 中建立資料庫 `SelfLearningDB`。
2. 執行 [backend/questionbank/src/main/resources/schema.sql](backend/questionbank/src/main/resources/schema.sql) 建立資料表與初始資料。
3. 檢查並修改 [backend/questionbank/src/main/resources/application.properties](backend/questionbank/src/main/resources/application.properties) 中的資料庫連線帳號與密碼：
   ```properties
   spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=SelfLearningDB;encrypt=false;trustServerCertificate=true
   spring.datasource.username=sa
   spring.datasource.password=YOUR_PASSWORD
   ```

### 3. 啟動後端伺服器 (Backend)
在 `backend/questionbank` 目錄下執行：

```bash
cd backend/questionbank
mvn spring-boot:run
```
> 後端服務預設於 `http://localhost:8080` 啟動。

### 4. 啟動前端介面 (Frontend)
直接以瀏覽器開啟根目錄下的 `index.html`，或使用 VS Code Live Server 擴充套件啟動。

---

## 🌟 主要功能模組 (Features)

1. **使用者與班級管理 (User & Course Management)**
   - 教師與學生帳號體系、加入課程、班級學生名單管理。
2. **教材資料夾與檔案管理 (Folder & Material Management)**
   - 樹狀資料夾組織、支援多格式教材檔案上傳（PDF、PPT、Excel）。
3. **題庫自動化解析與匯入 (Smart Question Import)**
   - 支援 Excel 題庫快速批次匯入、格式檢核與錯誤提示。
4. **AI 智慧輔助 (Gemini AI Integration)**
   - 結合 Google Gemini 進行題目分析與智慧輔助。
5. **學習歷程與分析 (Learning Analytics)**
   - 學生答題紀錄追蹤、學習成效統計與診斷。
