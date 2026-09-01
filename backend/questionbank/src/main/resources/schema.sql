-- ============================================================
-- 智慧題庫管理與學習分析系統 — SQL Server Schema
-- 資料庫名稱：QuestionBankDB
-- ============================================================

-- 若資料庫不存在，請先執行：
-- CREATE DATABASE QuestionBankDB;
-- USE QuestionBankDB;

-- ── 題目主表 ─────────────────────────────────────────────────
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='questions' AND xtype='U')
CREATE TABLE questions (
    id           INT IDENTITY(1,1) PRIMARY KEY,
    content      NVARCHAR(1000)  NOT NULL,
    option_a     NVARCHAR(500)   NOT NULL,
    option_b     NVARCHAR(500)   NOT NULL,
    option_c     NVARCHAR(500)   NOT NULL,
    option_d     NVARCHAR(500)   NOT NULL,
    answer       CHAR(1)         NOT NULL CHECK (answer IN ('A','B','C','D')),
    subject      NVARCHAR(50)    DEFAULT N'未分類',
    unit         NVARCHAR(100)   DEFAULT N'未分類',
    department   NVARCHAR(100)   DEFAULT N'自然科學科',
    difficulty   NVARCHAR(10)    DEFAULT N'中' CHECK (difficulty IN (N'易',N'中',N'難')),
    source_type  NVARCHAR(30)    DEFAULT N'教師手動',
    created_at   DATETIME2       DEFAULT GETDATE(),
    updated_at   DATETIME2       DEFAULT GETDATE()
);
GO

-- ── 匯入紀錄（待確認佇列） ───────────────────────────────────
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='import_records' AND xtype='U')
CREATE TABLE import_records (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    content     NVARCHAR(1000),
    option_a    NVARCHAR(500),
    option_b    NVARCHAR(500),
    option_c    NVARCHAR(500),
    option_d    NVARCHAR(500),
    answer      CHAR(1),
    subject     NVARCHAR(50),
    unit        NVARCHAR(100),
    source_file NVARCHAR(255),
    confidence  INT             DEFAULT 0 CHECK (confidence BETWEEN 0 AND 100),
    status      NVARCHAR(20)    DEFAULT N'pending'
                CHECK (status IN (N'pending',N'confirmed',N'rejected')),
    created_at  DATETIME2       DEFAULT GETDATE(),
    updated_at  DATETIME2       DEFAULT GETDATE()
);
GO

-- ── 學習資料夾 ───────────────────────────────────────────────
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='learning_folders' AND xtype='U')
CREATE TABLE learning_folders (
    id         INT IDENTITY(1,1) PRIMARY KEY,
    name       NVARCHAR(200)   NOT NULL,
    created_at DATETIME2       DEFAULT GETDATE()
);
GO

-- ── 資料夾內教材 ─────────────────────────────────────────────
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='folder_materials' AND xtype='U')
CREATE TABLE folder_materials (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    folder_id   INT             NOT NULL REFERENCES learning_folders(id) ON DELETE CASCADE,
    file_name   NVARCHAR(255)   NOT NULL,
    file_type   NVARCHAR(20)    DEFAULT 'PDF',
    file_path   NVARCHAR(500),
    uploaded_at DATETIME2       DEFAULT GETDATE()
);
GO

-- ── 課程主表 ─────────────────────────────────────────────────
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='courses' AND xtype='U')
CREATE TABLE courses (
    id            INT IDENTITY(1,1) PRIMARY KEY,
    name          NVARCHAR(200)   NOT NULL,
    code          NVARCHAR(100)   NOT NULL,
    type          NVARCHAR(50)    DEFAULT N'必修',
    academic_year NVARCHAR(20)    DEFAULT N'113',
    semester      NVARCHAR(20)    DEFAULT N'1',
    credits       NVARCHAR(20)    DEFAULT N'3',
    grade         NVARCHAR(50)    DEFAULT N'高二',
    class_group   NVARCHAR(50)    DEFAULT N'甲班',
    teacher       NVARCHAR(100)   DEFAULT N'王老師',
    created_at    DATETIME2       DEFAULT GETDATE(),
    updated_at    DATETIME2       DEFAULT GETDATE()
);
GO

-- ── 課程修課學生名單 ─────────────────────────────────────────
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='course_students' AND xtype='U')
CREATE TABLE course_students (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    course_id           BIGINT          NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    student_no          NVARCHAR(50)    NOT NULL,
    name                NVARCHAR(100)   NOT NULL,
    seat_no             NVARCHAR(20),
    email               NVARCHAR(150),
    note                NVARCHAR(255),
    is_assistant        BIT             DEFAULT 0,
    assistant_permissions NVARCHAR(500),
    created_at          DATETIME2       DEFAULT GETDATE()
);
GO

-- ── 系統使用者/學生表 (users) ───────────────────────────────
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='users' AND xtype='U')
CREATE TABLE users (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    username    NVARCHAR(100)   NOT NULL UNIQUE, -- 學號或帳號
    name        NVARCHAR(100)   NOT NULL,        -- 姓名
    email       NVARCHAR(150),
    role        NVARCHAR(50)    DEFAULT N'學生',
    seat_no     NVARCHAR(20),
    grade       NVARCHAR(50)    DEFAULT N'高二',
    class_group NVARCHAR(50)    DEFAULT N'甲班',
    status      NVARCHAR(50)    DEFAULT N'啟用中',
    created_at  DATETIME2       DEFAULT GETDATE(),
    updated_at  DATETIME2       DEFAULT GETDATE()
);
GO

-- ── 種子資料（範例） ─────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM questions)
BEGIN
    INSERT INTO questions (content, option_a, option_b, option_c, option_d, answer, subject, unit, department, difficulty, source_type)
    VALUES
    (N'下列何者為牛頓第二運動定律的公式？', N'F=ma', N'F=mv', N'F=m/a', N'F=a/m', 'A', N'物理', N'力學', N'自然科學科', N'易', N'PDF匯入'),
    (N'細胞膜的主要成分是什麼？', N'磷脂雙層', N'蛋白質', N'纖維素', N'澱粉', 'A', N'生物', N'細胞學', N'自然科學科', N'易', N'AI產生'),
    (N'sin(30°) 的值為？', N'1/2', N'√3/2', N'√2/2', N'1', 'A', N'數學', N'三角函數', N'自然科學科', N'易', N'教師手動');
END
GO

IF NOT EXISTS (SELECT 1 FROM users WHERE role IN (N'學生', 'student'))
BEGIN
    INSERT INTO users (username, name, email, role, seat_no, grade, class_group, status)
    VALUES
    (N'S1130101', N'張廷瑋', N's1130101@school.edu.tw', N'學生', N'01', N'高二', N'甲班', N'啟用中'),
    (N'S1130102', N'林語晨', N's1130102@school.edu.tw', N'學生', N'02', N'高二', N'甲班', N'啟用中'),
    (N'S1130103', N'陳冠宇', N's1130103@school.edu.tw', N'學生', N'03', N'高二', N'甲班', N'啟用中'),
    (N'S1130104', N'黃品瑄', N's1130104@school.edu.tw', N'學生', N'04', N'高二', N'甲班', N'啟用中'),
    (N'S1130105', N'趙韋翔', N's1130105@school.edu.tw', N'學生', N'05', N'高二', N'甲班', N'啟用中'),
    (N'S1130106', N'林小美', N's1130106@school.edu.tw', N'學生', N'06', N'高二', N'甲班', N'啟用中'),
    (N'S1130107', N'陳同學', N's1130107@school.edu.tw', N'學生', N'07', N'高二', N'甲班', N'啟用中'),
    (N'S1130108', N'王小明', N's1130108@school.edu.tw', N'學生', N'08', N'高二', N'甲班', N'啟用中');
END
GO

PRINT N'Schema 建立完成！';


