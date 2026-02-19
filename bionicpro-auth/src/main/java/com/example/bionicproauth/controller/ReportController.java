package com.example.bionicproauth.controller;

import com.example.bionicproauth.dto.ReportDto;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

@RestController
@RequestMapping("/api")
@Slf4j
public class ReportController {

    private final JdbcTemplate jdbcTemplate;

    public ReportController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/report")
    public ResponseEntity<?> getReport(HttpSession session) {
        String email = (String) session.getAttribute("email");
        if (email == null) {
            return ResponseEntity.status(401).body("User email not found in session");
        }

        String sql = """
                SELECT user_id, name, email, age, gender, country,
                       prosthesis_types, total_signals,
                       avg_signal_frequency, avg_signal_duration, avg_signal_amplitude,
                       min_signal_time, max_signal_time, last_signal_time
                FROM customer_telemetry_summary
                WHERE email = ?
                """;

        try {
            ReportDto report = jdbcTemplate.queryForObject(sql, new ReportRowMapper(), email);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("error", e);
            return ResponseEntity.status(404).body("Report not found for user");
        }
    }

    private static class ReportRowMapper implements RowMapper<ReportDto> {
        @Override
        public ReportDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            ReportDto dto = new ReportDto();
            dto.setUserId(rs.getInt("user_id"));
            dto.setName(rs.getString("name"));
            dto.setEmail(rs.getString("email"));
            dto.setAge(rs.getBigDecimal("age"));
            dto.setGender(rs.getString("gender"));
            dto.setCountry(rs.getString("country"));
            // prosthesis_types — массив text[]
            String[] typesArray = (String[]) rs.getArray("prosthesis_types").getArray();
            dto.setProsthesisTypes(Arrays.asList(typesArray));
            dto.setTotalSignals(rs.getInt("total_signals"));
            dto.setAvgSignalFrequency(rs.getBigDecimal("avg_signal_frequency"));
            dto.setAvgSignalDuration(rs.getBigDecimal("avg_signal_duration"));
            dto.setAvgSignalAmplitude(rs.getBigDecimal("avg_signal_amplitude"));
            dto.setMinSignalTime(rs.getTimestamp("min_signal_time").toInstant());
            dto.setMaxSignalTime(rs.getTimestamp("max_signal_time").toInstant());
            dto.setLastSignalTime(rs.getTimestamp("last_signal_time").toInstant());
            return dto;
        }
    }
}