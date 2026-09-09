package com.edu.questionbank.service;

import com.edu.questionbank.model.ImportRecord;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 檔案解析服務：支援 PPT/PPTX 文字擷取、Excel 表格精確解析、PDF 國家檢定與多重格式智慧匹配與文字提取
 */
@Service
public class FileParserService {

    /**
     * 擷取 PPT / PPTX 檔案內的所有投影片文字與備忘錄
     */
    public String extractTextFromPpt(InputStream inputStream, String filename) {
        StringBuilder sb = new StringBuilder();
        try (XMLSlideShow ppt = new XMLSlideShow(inputStream)) {
            int slideNum = 1;
            for (XSLFSlide slide : ppt.getSlides()) {
                sb.append("--- Slide ").append(slideNum++).append(" ---\n");
                for (XSLFTextShape shape : slide.getPlaceholders()) {
                    String text = shape.getText();
                    if (text != null && !text.isBlank()) {
                        sb.append(text.trim()).append("\n");
                    }
                }
                slide.getShapes().forEach(shape -> {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank() && !sb.substring(sb.lastIndexOf("--- Slide")).contains(text.trim())) {
                            sb.append(text.trim()).append("\n");
                        }
                    }
                });
                sb.append("\n");
            }
        } catch (Exception e) {
            sb.append("無法完整讀取 PPT 內容，退回檔名分析 (").append(e.getMessage()).append(")\n");
        }
        return sb.toString();
    }

    /**
     * 擷取 PDF 檔案的全文文字（用於提供給 Gemini AI 解析）
     */
    public String extractTextFromPdf(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                stripper.setSuppressDuplicateOverlappingText(true);
                return stripper.getText(document);
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 將 PDF 檔逐頁渲染為高解析度 PNG 圖片 Byte 陣列（供 Gemini Vision 視覺 OCR 分析）
     */
    public List<byte[]> renderPdfPagesToImages(InputStream inputStream) {
        List<byte[]> images = new ArrayList<>();
        try {
            byte[] pdfBytes = inputStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
                int totalPages = document.getNumberOfPages();
                for (int i = 0; i < totalPages; i++) {
                    java.awt.image.BufferedImage bim = renderer.renderImageWithDPI(i, 140);
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(bim, "PNG", baos);
                    images.add(baos.toByteArray());
                }
            }
        } catch (Exception e) {
            // fallback
        }
        return images;
    }

    /**
     * 依頁數逐頁擷取 PDF 檔案文字（用於支援 Gemini API 分頁批次解析，徹底突破 8192 Token 限制）
     */
    public List<String> extractTextPagesFromPdf(InputStream inputStream) {
        List<String> pages = new ArrayList<>();
        try {
            byte[] bytes = inputStream.readAllBytes();
            try (PDDocument document = Loader.loadPDF(bytes)) {
                int totalPages = document.getNumberOfPages();
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                stripper.setSuppressDuplicateOverlappingText(true);
                for (int p = 1; p <= totalPages; p++) {
                    stripper.setStartPage(p);
                    stripper.setEndPage(p);
                    String pageText = stripper.getText(document);
                    if (pageText != null && !pageText.isBlank()) {
                        pages.add(pageText);
                    }
                }
            }
        } catch (Exception e) {
            // fallback
        }
        return pages;
    }

    /**
     * 將 Excel 表格內容轉為純文字格式，提供給 Gemini AI 進行題目、選項與答案的智慧分析
     */
    public String extractTextFromExcel(InputStream inputStream) {
        StringBuilder sb = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            int rIndex = 1;
            for (Row row : sheet) {
                StringBuilder rowSb = new StringBuilder();
                for (Cell cell : row) {
                    String val = formatter.formatCellValue(cell).trim();
                    if (!val.isEmpty()) {
                        rowSb.append(val).append(" | ");
                    }
                }
                if (rowSb.length() > 0) {
                    sb.append("第 ").append(rIndex).append(" 列: ").append(rowSb.toString()).append("\n");
                    rIndex++;
                }
            }
        } catch (Exception e) {
            // fallback
        }
        return sb.toString();
    }

    /**
     * 解析固定格式 Excel 表格檔案 (.xlsx / .xls)
     */
    public List<ImportRecord> parseFixedExcel(InputStream inputStream, String sourceFileName) throws Exception {
        List<ImportRecord> records = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            int contentCol = -1, optACol = -1, optBCol = -1, optCCol = -1, optDCol = -1;
            int ansCol = -1, subjCol = -1, unitCol = -1;

            Row firstRowObj = sheet.getRow(0);
            if (firstRowObj != null) {
                for (Cell cell : firstRowObj) {
                    String h = formatter.formatCellValue(cell).trim().toLowerCase();
                    int idx = cell.getColumnIndex();
                    if (h.contains("題幹") || h.contains("題目") || h.contains("content") || h.contains("question") || h.contains("敘述")) {
                        if (contentCol == -1) contentCol = idx;
                    } else if (h.contains("選項1") || h.contains("選項a") || h.equalsIgnoreCase("a") || h.contains("optiona") || h.contains("option1") || h.contains("(1)")) {
                        optACol = idx;
                    } else if (h.contains("選項2") || h.contains("選項b") || h.equalsIgnoreCase("b") || h.contains("optionb") || h.contains("option2") || h.contains("(2)")) {
                        optBCol = idx;
                    } else if (h.contains("選項3") || h.contains("選項c") || h.equalsIgnoreCase("c") || h.contains("optionc") || h.contains("option3") || h.contains("(3)")) {
                        optCCol = idx;
                    } else if (h.contains("選項4") || h.contains("選項d") || h.equalsIgnoreCase("d") || h.contains("optiond") || h.contains("option4") || h.contains("(4)")) {
                        optDCol = idx;
                    } else if (h.contains("答案") || h.contains("解答") || h.contains("answer") || h.contains("ans")) {
                        ansCol = idx;
                    } else if (h.contains("科目") || h.contains("類別") || h.contains("subject")) {
                        subjCol = idx;
                    } else if (h.contains("單元") || h.contains("章節") || h.contains("unit")) {
                        unitCol = idx;
                    }
                }
            }

            // 若無法自動識別標頭，則預設標準位置 (0:題目, 1:選項A, 2:選項B, 3:選項C, 4:選項D, 5:答案)
            if (contentCol == -1) contentCol = 0;
            if (optACol == -1) optACol = 1;
            if (optBCol == -1) optBCol = 2;
            if (optCCol == -1) optCCol = 3;
            if (optDCol == -1) optDCol = 4;
            if (ansCol == -1) ansCol = 5;

            boolean isHeaderRow = true;
            for (Row row : sheet) {
                if (isHeaderRow) {
                    isHeaderRow = false;
                    // 若第 0 列包含 "題目" 或 "content" 等欄位名稱，則跳過標頭列
                    String firstCellText = formatter.formatCellValue(row.getCell(contentCol)).trim().toLowerCase();
                    if (firstCellText.contains("題目") || firstCellText.contains("content") || firstCellText.contains("題幹")) {
                        continue;
                    }
                }

                String content = formatter.formatCellValue(row.getCell(contentCol)).trim();
                if (content.isEmpty()) continue;

                String optA = formatter.formatCellValue(row.getCell(optACol)).trim();
                String optB = formatter.formatCellValue(row.getCell(optBCol)).trim();
                String optC = formatter.formatCellValue(row.getCell(optCCol)).trim();
                String optD = formatter.formatCellValue(row.getCell(optDCol)).trim();
                String answer = formatter.formatCellValue(row.getCell(ansCol)).trim().toUpperCase();
                
                String rawSubj = (subjCol >= 0 && row.getCell(subjCol) != null) ? formatter.formatCellValue(row.getCell(subjCol)).trim() : "";
                String rawUnit = (unitCol >= 0 && row.getCell(unitCol) != null) ? formatter.formatCellValue(row.getCell(unitCol)).trim() : "";

                String subject = (rawSubj.isEmpty() || rawSubj.length() > 30) ? "一般" : rawSubj;
                String unit = (rawUnit.isEmpty() || rawUnit.length() > 50) ? "Excel匯入" : rawUnit;

                if (answer.isEmpty() || !answer.matches("[A-D]")) {
                    if (answer.matches("[1-4]")) {
                        answer = String.valueOf((char)('A' + Integer.parseInt(answer) - 1));
                    } else {
                        answer = "A";
                    }
                }

                ImportRecord record = new ImportRecord();
                record.setContent(content);
                record.setOptionA(optA.isEmpty() ? "選項A" : optA);
                record.setOptionB(optB.isEmpty() ? "選項B" : optB);
                record.setOptionC(optC.isEmpty() ? "選項C" : optC);
                record.setOptionD(optD.isEmpty() ? "選項D" : optD);
                record.setAnswer(answer);
                record.setSubject(subject.isEmpty() ? "一般" : subject);
                record.setUnit(unit.isEmpty() ? "Excel匯入" : unit);
                record.setSourceFile(sourceFileName);
                record.setConfidence(98);
                record.setStatus("pending");

                records.add(record);
            }
        }
        return records;
    }

    /**
     * 全方位 PDF 解析器：支援國家技能檢定 (軟乙/軟丙)、單複選答案 (3) / (34) / (123)、圓圈數字選項 ①②③④ 與多工作項目自動銜接
     */
    public List<ImportRecord> parseFixedPdf(InputStream inputStream, String sourceFileName) throws Exception {
        List<ImportRecord> records = new ArrayList<>();
        byte[] bytes = inputStream.readAllBytes();

        String fullText;
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setSuppressDuplicateOverlappingText(true);
            fullText = stripper.getText(document);
        }

        if (fullText == null || fullText.isBlank()) return records;

        String[] lines = fullText.split("\\r?\\n");

        // 國家技能檢定標準題號格式 [題號] ([答案1-4或複選]) 或 ([答案1-4或複選]) [題號]
        Pattern qHeaderPattern = Pattern.compile("^\\s*(?:(\\d{1,4})[.、\\s]*[\\(（\\[【\\s]*([1-4A-D]{1,4})[\\)）\\]】\\s]*|[\\(（\\[【\\s]*([1-4A-D]{1,4})[\\)）\\]】\\s]*(\\d{1,4})[.、\\s]+)(.*)");

        // 一般題號格式 (如 1. 、 01. 、 第1題)
        Pattern genericQPattern = Pattern.compile("^\\s*(?:第\\s*(\\d{1,4})\\s*題|Q(\\d{1,4})|(\\d{1,4})[.、\\)\\-])[\\s]+(.*)");

        int currentQNum = 0;
        String currentRawNumStr = null;
        String currentAnsStr = null;
        StringBuilder currentBlock = new StringBuilder();

        String currentSubject = "電腦軟體應用 乙級";
        String currentUnit = "工作項目 01：電腦概論";

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // 遇工作項目或章節標題變更 (如: 11800 電腦軟體應用 乙級 工作項目 01：電腦概論)
            if (line.contains("工作項目") || line.matches(".*(?:第[一二三四五六七八九十0-9]+[章單元節]|章節|PART|SECTION).*")) {
                if (currentBlock.length() > 0) {
                    ImportRecord rec = buildImportRecordFromBlock(currentRawNumStr, currentAnsStr, currentBlock.toString(), sourceFileName, currentSubject, currentUnit);
                    if (rec != null) records.add(rec);
                }

                // 動態解析科目與單元名稱
                String[] parsedSubjUnit = parseSubjectAndUnit(line, currentSubject, currentUnit);
                currentSubject = parsedSubjUnit[0];
                currentUnit = parsedSubjUnit[1];

                currentQNum = 0;
                currentRawNumStr = null;
                currentAnsStr = null;
                currentBlock = new StringBuilder();
                continue;
            }

            // 過濾頁首頁尾與檔案資訊標題頁
            if (line.contains("技能檢定") || line.contains("本試題") || line.contains("頁次") || line.contains("專案代碼") || line.contains("版次編號") || line.contains("公告日期") || line.matches("(?i)^Page\\s+\\d+\\s+of\\s+\\d+.*")) {
                continue;
            }

            Matcher mHeader = qHeaderPattern.matcher(line);
            Matcher mGeneric = genericQPattern.matcher(line);

            boolean isNewQuestion = false;
            String matchedNumStr = null;
            String matchedAnsStr = null;
            String matchedRestText = null;

            if (mHeader.find()) {
                String candidateNumStr = mHeader.group(1) != null ? mHeader.group(1) : mHeader.group(4);
                String candidateAnsStr = mHeader.group(1) != null ? mHeader.group(2) : mHeader.group(3);
                String candidateRestText = mHeader.group(5);

                try {
                    int parsedNum = Integer.parseInt(candidateNumStr);
                    // 題號遞增嚴格驗證：防止 PDF 字型編碼錯位 (如 ①21 被誤判為 2. (1))，題號必須 >= currentQNum 且在連續範圍內 (+5 內)
                    if (currentQNum == 0 || (parsedNum >= currentQNum && parsedNum <= currentQNum + 5)) {
                        matchedNumStr = candidateNumStr;
                        matchedAnsStr = candidateAnsStr;
                        matchedRestText = candidateRestText;
                        isNewQuestion = true;
                    }
                } catch (Exception ignored) {}

            } else if (mGeneric.find()) {
                String numCandidate = mGeneric.group(1) != null ? mGeneric.group(1) : (mGeneric.group(2) != null ? mGeneric.group(2) : mGeneric.group(3));
                try {
                    int parsedNum = Integer.parseInt(numCandidate);
                    if (currentQNum == 0 || (parsedNum >= currentQNum && parsedNum <= currentQNum + 5)) {
                        matchedNumStr = numCandidate;
                        matchedAnsStr = null;
                        matchedRestText = mGeneric.group(4);
                        isNewQuestion = true;
                    }
                } catch (NumberFormatException ignored) {}
            }

            if (isNewQuestion) {
                // 結算前一題
                if (currentBlock.length() > 0) {
                    ImportRecord rec = buildImportRecordFromBlock(currentRawNumStr, currentAnsStr, currentBlock.toString(), sourceFileName, currentSubject, currentUnit);
                    if (rec != null) records.add(rec);
                }

                currentRawNumStr = matchedNumStr;
                currentAnsStr = matchedAnsStr;
                try {
                    if (matchedNumStr != null) currentQNum = Integer.parseInt(matchedNumStr);
                } catch (Exception ignored) {}

                currentBlock = new StringBuilder();
                if (matchedRestText != null && !matchedRestText.isBlank()) {
                    currentBlock.append(matchedRestText.trim());
                }
            } else {
                if (currentBlock.length() > 0) {
                    currentBlock.append(" ").append(line);
                }
            }
        }

        // 結算最後一題
        if (currentBlock.length() > 0) {
            ImportRecord rec = buildImportRecordFromBlock(currentRawNumStr, currentAnsStr, currentBlock.toString(), sourceFileName, currentSubject, currentUnit);
            if (rec != null) records.add(rec);
        }

        return records;
    }

    private String[] parseSubjectAndUnit(String line, String defaultSubject, String defaultUnit) {
        String subj = defaultSubject;
        String unit = defaultUnit;

        // 1. 精準提取 工作項目 01：電腦概論 標題（防止擷取到前後的題目內文）
        Pattern pWorkItem = Pattern.compile("工作項目\\s*\\d{1,2}(?:[:：][^\\r\\n\\?？\\.,，]{1,30})?");
        Matcher mWI = pWorkItem.matcher(line);
        if (mWI.find()) {
            unit = mWI.group().trim();
            int idx = mWI.start();
            if (idx > 0) {
                String rawSubj = line.substring(0, idx).replaceAll("^\\d+\\s*", "").trim();
                // 只有當 rawSubj 長度適中 (<=25) 且不包含問號或題目標點時，才採納為科目名稱
                if (!rawSubj.isBlank() && rawSubj.length() <= 25 && !rawSubj.contains("？") && !rawSubj.contains("?") && !rawSubj.contains("下列") && !rawSubj.contains("何者")) {
                    subj = rawSubj;
                }
            }
        } else {
            // 2. 匹配 第X章 / 第X單元 格式
            Pattern pChapter = Pattern.compile("(?:第[一二三四五六七八九十0-9]+[章單元節]|章節|PART|SECTION)[\\s:：]*[^\\r\\n\\?？\\.,，]{1,30}");
            Matcher mCh = pChapter.matcher(line);
            if (mCh.find()) {
                unit = mCh.group().trim();
            }
        }

        return new String[]{subj, unit};
    }

    private ImportRecord buildImportRecordFromBlock(String qNumStr, String rawAnsStr, String bodyText, String sourceFileName, String subject, String unit) {
        if (bodyText == null || bodyText.isBlank()) return null;

        ImportRecord record = new ImportRecord();
        record.setSourceFile(sourceFileName);
        record.setConfidence(98);
        record.setStatus("pending");
        record.setSubject(subject != null && !subject.isBlank() ? subject : "綜合科目");
        record.setUnit(unit != null && !unit.isBlank() ? unit : "一般單元");

        String finalAns = formatAnswer(rawAnsStr);
        record.setAnswer(finalAns);

        // 尋找選項 (1)(2)(3)(4) 或 ①②③④ 或 (A)(B)(C)(D) 位置
        int idxA = -1, idxB = -1, idxC = -1, idxD = -1;
        int lenA = 0, lenB = 0, lenC = 0, lenD = 0;

        // 1. 優先匹配 國家技能檢定標準圓圈數字 ① ② ③ ④ (Unicode \u2460 \u2461 \u2462 \u2463)
        Matcher mA1 = Pattern.compile("[①❶]").matcher(bodyText);
        Matcher mB1 = Pattern.compile("[②❷]").matcher(bodyText);
        Matcher mC1 = Pattern.compile("[③❸]").matcher(bodyText);
        Matcher mD1 = Pattern.compile("[④❹]").matcher(bodyText);

        if (mA1.find() && mB1.find() && mC1.find() && mD1.find()) {
            idxA = mA1.start(); lenA = mA1.end() - mA1.start();
            idxB = mB1.start(); lenB = mB1.end() - mB1.start();
            idxC = mC1.start(); lenC = mC1.end() - mC1.start();
            idxD = mD1.start(); lenD = mD1.end() - mD1.start();
        } else {
            // 2. 匹配 括號數字 (1) (2) (3) (4) 或 （1） （2） （3） （4）
            Matcher mA2 = Pattern.compile("[\\(（]1[\\)）]").matcher(bodyText);
            Matcher mB2 = Pattern.compile("[\\(（]2[\\)）]").matcher(bodyText);
            Matcher mC2 = Pattern.compile("[\\(（]3[\\)）]").matcher(bodyText);
            Matcher mD2 = Pattern.compile("[\\(（]4[\\)）]").matcher(bodyText);

            if (mA2.find() && mB2.find() && mC2.find() && mD2.find()) {
                idxA = mA2.start(); lenA = mA2.end() - mA2.start();
                idxB = mB2.start(); lenB = mB2.end() - mB2.start();
                idxC = mC2.start(); lenC = mC2.end() - mC2.start();
                idxD = mD2.start(); lenD = mD2.end() - mD2.start();
            } else {
                // 3. 匹配 括號英文 (A) (B) (C) (D) 或 （A） （B） （C） （D）
                Matcher mA3 = Pattern.compile("[\\(（]A[\\)）]").matcher(bodyText);
                Matcher mB3 = Pattern.compile("[\\(（]B[\\)）]").matcher(bodyText);
                Matcher mC3 = Pattern.compile("[\\(（]C[\\)）]").matcher(bodyText);
                Matcher mD3 = Pattern.compile("[\\(（]D[\\)）]").matcher(bodyText);

                if (mA3.find() && mB3.find() && mC3.find() && mD3.find()) {
                    idxA = mA3.start(); lenA = mA3.end() - mA3.start();
                    idxB = mB3.start(); lenB = mB3.end() - mB3.start();
                    idxC = mC3.start(); lenC = mC3.end() - mC3.start();
                    idxD = mD3.start(); lenD = mD3.end() - mD3.start();
                } else {
                    // 4. 匹配 A. B. C. D. 或 A) B) C) D)
                    Matcher mA4 = Pattern.compile("\\bA[\\.、\\)]").matcher(bodyText);
                    Matcher mB4 = Pattern.compile("\\bB[\\.、\\)]").matcher(bodyText);
                    Matcher mC4 = Pattern.compile("\\bC[\\.、\\)]").matcher(bodyText);
                    Matcher mD4 = Pattern.compile("\\bD[\\.、\\)]").matcher(bodyText);

                    if (mA4.find() && mB4.find() && mC4.find() && mD4.find()) {
                        idxA = mA4.start(); lenA = mA4.end() - mA4.start();
                        idxB = mB4.start(); lenB = mB4.end() - mB4.start();
                        idxC = mC4.start(); lenC = mC4.end() - mC4.start();
                        idxD = mD4.start(); lenD = mD4.end() - mD4.start();
                    }
                }
            }
        }

        String titleContent;
        String optA = "", optB = "", optC = "", optD = "";

        if (idxA != -1 && idxB != -1 && idxC != -1 && idxD != -1 && idxA < idxB && idxB < idxC && idxC < idxD) {
            titleContent = bodyText.substring(0, idxA).trim();
            optA = cleanOptionString(bodyText.substring(idxA + lenA, idxB));
            optB = cleanOptionString(bodyText.substring(idxB + lenB, idxC));
            optC = cleanOptionString(bodyText.substring(idxC + lenC, idxD));
            optD = cleanOptionString(bodyText.substring(idxD + lenD));
        } else {
            titleContent = bodyText.trim();
        }

        // 清理末端雜訊與標籤
        titleContent = titleContent.replaceAll("(?i)\\[?答案[：:\\s]*[1-4A-D]+\\]?", "").trim();
        optD = optD.replaceAll("(?i)\\[?答案[：:\\s]*[1-4A-D]+\\]?", "").trim();

        // 組合純淨題幹 (僅包含 "1. 題目文字"，不重複包含開頭答案括號 (3))
        String qHeader = (qNumStr != null && !qNumStr.isBlank()) ? qNumStr + ". " : "";
        record.setContent(qHeader + titleContent);

        record.setOptionA(optA.isBlank() ? "選項A" : optA);
        record.setOptionB(optB.isBlank() ? "選項B" : optB);
        record.setOptionC(optC.isBlank() ? "選項C" : optC);
        record.setOptionD(optD.isBlank() ? "選項D" : optD);

        return record;
    }

    private String formatAnswer(String rawAnsStr) {
        if (rawAnsStr == null || rawAnsStr.isBlank()) return "A";
        StringBuilder sb = new StringBuilder();
        for (char c : rawAnsStr.toCharArray()) {
            if (c >= '1' && c <= '4') {
                char ansChar = (char) ('A' + (c - '1'));
                if (sb.length() > 0) sb.append(", ");
                sb.append(ansChar);
            } else if ((c >= 'A' && c <= 'D') || (c >= 'a' && c <= 'd')) {
                char ansChar = Character.toUpperCase(c);
                if (sb.length() > 0) sb.append(", ");
                sb.append(ansChar);
            }
        }
        return sb.length() > 0 ? sb.toString() : "A";
    }

    private String cleanOptionString(String str) {
        if (str == null) return "";
        String s = str.trim();
        s = s.replaceAll("\\s*[。\\.]\\s*$", "").trim();
        return s;
    }

    private boolean isValidRecord(ImportRecord record) {
        return record.getContent() != null && !record.getContent().isBlank();
    }
}
