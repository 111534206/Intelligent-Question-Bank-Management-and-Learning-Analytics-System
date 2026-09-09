package com.edu.questionbank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 智慧題庫管理與學習分析系統 — Spring Boot Entry Point
 *
 * 啟動方式：
 *   mvn spring-boot:run
 * 或打包後：
 *   java -jar target/questionbank-1.0.0.jar
 *
 * API 基礎路徑：http://localhost:8080/api
 */
@SpringBootApplication
public class QuestionBankApplication {

    private static final Logger log = LoggerFactory.getLogger(QuestionBankApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(QuestionBankApplication.class, args);
    }

    @Bean
    public CommandLineRunner initDatabaseSchema(JdbcTemplate jdbcTemplate) {
        return args -> {
            // 1. 自動解除舊有特有欄位 (如 QuestionText, OptionsJSON, CorrectAnswer 等) 的 NOT NULL 限制，防止新題目寫入時崩潰
            try {
                String relaxSql = "DECLARE @sql NVARCHAR(MAX) = ''; " +
                    "SELECT @sql += 'ALTER TABLE questions ALTER COLUMN [' + COLUMN_NAME + '] ' + DATA_TYPE + " +
                    "(CASE WHEN CHARACTER_MAXIMUM_LENGTH = -1 THEN '(MAX)' WHEN CHARACTER_MAXIMUM_LENGTH IS NOT NULL THEN '(' + CAST(CHARACTER_MAXIMUM_LENGTH AS VARCHAR) + ')' ELSE '' END) + ' NULL; ' " +
                    "FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_NAME = 'questions' AND IS_NULLABLE = 'NO' AND COLUMN_NAME NOT IN ('id', 'QuestionID'); " +
                    "EXEC sp_executesql @sql;";
                jdbcTemplate.execute(relaxSql);
                log.info("已成功自動將 questions 表中舊有的 NOT NULL 欄位轉為 NULLABLE");
            } catch (Exception e) {
                log.warn("嘗試調整 questions 表舊欄位可空屬性時提示: {}", e.getMessage());
            }

            // 2. 自動補充必要欄位
            String[] addCols = {
                "ALTER TABLE questions ADD content NVARCHAR(1000)",
                "ALTER TABLE questions ADD option_a NVARCHAR(500)",
                "ALTER TABLE questions ADD option_b NVARCHAR(500)",
                "ALTER TABLE questions ADD option_c NVARCHAR(500)",
                "ALTER TABLE questions ADD option_d NVARCHAR(500)",
                "ALTER TABLE questions ADD answer NVARCHAR(50)",
                "ALTER TABLE questions ADD subject NVARCHAR(50)",
                "ALTER TABLE questions ADD unit NVARCHAR(100)",
                "ALTER TABLE questions ADD department NVARCHAR(100)",
                "ALTER TABLE questions ADD difficulty NVARCHAR(10)",
                "ALTER TABLE questions ADD source_type NVARCHAR(30)",
                "ALTER TABLE questions ADD created_at DATETIME2",
                "ALTER TABLE questions ADD updated_at DATETIME2"
            };

            for (String sql : addCols) {
                try {
                    jdbcTemplate.execute(sql);
                    log.info("自動校正 questions 表欄位: {}", sql);
                } catch (Exception ignored) {}
            }

            // 3. 自動擴充欄位長度
            String[] expandCols = {
                "ALTER TABLE import_records ALTER COLUMN answer NVARCHAR(50)",
                "ALTER TABLE questions ALTER COLUMN content NVARCHAR(1000)",
                "ALTER TABLE questions ALTER COLUMN option_a NVARCHAR(500)",
                "ALTER TABLE questions ALTER COLUMN option_b NVARCHAR(500)",
                "ALTER TABLE questions ALTER COLUMN option_c NVARCHAR(500)",
                "ALTER TABLE questions ALTER COLUMN option_d NVARCHAR(500)",
                "ALTER TABLE questions ALTER COLUMN answer NVARCHAR(50)",
                "ALTER TABLE questions ALTER COLUMN subject NVARCHAR(50)",
                "ALTER TABLE questions ALTER COLUMN unit NVARCHAR(100)"
            };

            for (String sql : expandCols) {
                try {
                    jdbcTemplate.execute(sql);
                } catch (Exception ignored) {}
            }

            log.info("資料庫 questions 與 import_records 資料表結構校正升級完成！");
        };
    }
}
