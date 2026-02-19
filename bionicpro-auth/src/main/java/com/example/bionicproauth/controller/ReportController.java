package com.example.bionicproauth.controller;

import com.example.bionicproauth.service.ClickhouseService;
import com.example.bionicproauth.service.MinioService;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.StringWriter;
import java.sql.Array;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api")
public class ReportController {

    private final ClickhouseService clickhouseService;
    private final MinioService minioService;

    public ReportController(ClickhouseService clickhouseService, MinioService minioService) {
        this.clickhouseService = clickhouseService;
        this.minioService = minioService;
    }

    @GetMapping("/report")
    public Map<String, Object> getReport(HttpSession session) {
        String email = (String) session.getAttribute("email");
        if (email == null) {
            throw new RuntimeException("User email not found in session");
        }

// Получаем версию как Unix timestamp (секунды)
        String versionSql = "SELECT toUnixTimestamp(max_signal_time) as version FROM customer_telemetry_summary_view WHERE email = ?";
        List<Map<String, Object>> versionResult = clickhouseService.query(versionSql, email);
        if (versionResult.isEmpty() || versionResult.get(0).get("version") == null) {
            throw new RuntimeException("No data found for user");
        }
// Версия в секундах, умножаем на 1000 для миллисекунд
        long version = ((Number) versionResult.get(0).get("version")).longValue() * 1000;

        String objectName = String.format("reports/%s/report_%d.csv", email, version);

        if (!minioService.objectExists(objectName)) {
            // Получаем полные данные для отчёта
            String fullSql = "SELECT * FROM customer_telemetry_summary_view WHERE email = ?";
            List<Map<String, Object>> dataList = clickhouseService.query(fullSql, email);
            if (dataList.isEmpty()) {
                throw new RuntimeException("No data found for user");
            }
            Map<String, Object> data = dataList.get(0);

            byte[] csvData = generateCsv(data); // метод generateCsv нужно адаптировать под структуру данных из Clickhouse
            minioService.uploadFile(objectName, csvData, "text/csv");
            log.info("Generated new report for {}: {}", email, objectName);
        }

        String fileUrl = minioService.getPublicUrl(objectName);
        return Map.of("url", fileUrl);
    }

    private byte[] generateCsv(Map<String, Object> data) {
        try (StringWriter out = new StringWriter()) {
            CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(
                    "user_id", "name", "email", "age", "gender", "country",
                    "prosthesis_types", "total_signals", "avg_signal_frequency",
                    "avg_signal_duration", "avg_signal_amplitude", "min_signal_time",
                    "max_signal_time", "last_signal_time"
            ));

            String prosthesisTypesStr = "";
            Object prosthesisObj = data.get("prosthesis_types");
            if (prosthesisObj != null) {
                if (prosthesisObj instanceof java.sql.Array) {
                    String[] types = (String[]) ((java.sql.Array) prosthesisObj).getArray();
                    prosthesisTypesStr = String.join(";", types);
                } else {
                    prosthesisTypesStr = prosthesisObj.toString();
                }
            }

            printer.printRecord(
                    data.get("user_id"),
                    data.get("name"),
                    data.get("email"),
                    data.get("age"),
                    data.get("gender"),
                    data.get("country"),
                    prosthesisTypesStr,
                    data.get("total_signals"),
                    data.get("avg_signal_frequency"),
                    data.get("avg_signal_duration"),
                    data.get("avg_signal_amplitude"),
                    data.get("min_signal_time"),
                    data.get("max_signal_time"),
                    data.get("last_signal_time")
            );

            printer.flush();
            return out.toString().getBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate CSV", e);
        }
    }
}