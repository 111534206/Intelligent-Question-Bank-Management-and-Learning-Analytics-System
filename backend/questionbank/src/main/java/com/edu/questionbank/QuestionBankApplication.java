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
            log.info("系統初始化：保留現有資料庫結構，不主動修改或重新命名使用者原有資料表。");
        };
    }
}
