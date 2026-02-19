package com.example.bionicproauth.controller;

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
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api")
public class ReportController {

    private final JdbcTemplate jdbcTemplate;
    private final MinioService minioService;

    public ReportController(JdbcTemplate jdbcTemplate, MinioService minioService) {
        this.jdbcTemplate = jdbcTemplate;
        this.minioService = minioService;
    }

    @GetMapping("/report")
    public Map<String, Object> getReport(HttpSession session) {
        String email = (String) session.getAttribute("email");
        if (email == null) {
            throw new RuntimeException("User email not found in session");
        }

        // 1. Получаем только дату последнего сигнала для версионирования
        String versionSql = "SELECT max_signal_time FROM customer_telemetry_summary WHERE email = ?";
        Timestamp maxSignalTime;
        try {
            maxSignalTime = jdbcTemplate.queryForObject(versionSql, Timestamp.class, email);
        } catch (Exception e) {
            throw new RuntimeException("No data found for user", e);
        }

        long version = maxSignalTime != null ? maxSignalTime.getTime() : System.currentTimeMillis();
        String objectName = String.format("reports/%s/report_%d.csv", email, version);

        // 2. Проверяем наличие в Minio
        if (!minioService.objectExists(objectName)) {
            // Файла нет – генерируем, запросив полные данные
            Map<String, Object> fullData = fetchFullData(email);
            byte[] csvData = generateCsv(fullData);
            minioService.uploadFile(objectName, csvData, "text/csv");
            log.info("Generated new report for {}: {}", email, objectName);
        }

        String fileUrl = minioService.getPublicUrl(objectName);
        return Map.of("url", fileUrl);
    }

    private Map<String, Object> fetchFullData(String email) {
        String sql = "SELECT * FROM customer_telemetry_summary WHERE email = ?";
        return jdbcTemplate.queryForMap(sql, email);
    }

    private byte[] generateCsv(Map<String, Object> data) {
        try (StringWriter out = new StringWriter()) {
            CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(
                    "user_id", "name", "email", "age", "gender", "country",
                    "prosthesis_types", "total_signals", "avg_signal_frequency",
                    "avg_signal_duration", "avg_signal_amplitude", "min_signal_time",
                    "max_signal_time", "last_signal_time"
            ));

            String prosthesisTypes = "";
            Array typesArray = (Array) data.get("prosthesis_types");
            if (typesArray != null) {
                String[] types = (String[]) typesArray.getArray();
                prosthesisTypes = Arrays.stream(types).collect(Collectors.joining(";"));
            }

            printer.printRecord(
                    data.get("user_id"),
                    data.get("name"),
                    data.get("email"),
                    data.get("age"),
                    data.get("gender"),
                    data.get("country"),
                    prosthesisTypes,
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