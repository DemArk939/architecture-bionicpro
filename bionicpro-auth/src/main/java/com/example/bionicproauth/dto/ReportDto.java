package com.example.bionicproauth.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class ReportDto {
    private Integer userId;
    private String name;
    private String email;
    private BigDecimal age;
    private String gender;
    private String country;
    private List<String> prosthesisTypes;
    private Integer totalSignals;
    private BigDecimal avgSignalFrequency;
    private BigDecimal avgSignalDuration;
    private BigDecimal avgSignalAmplitude;
    private Instant minSignalTime;
    private Instant maxSignalTime;
    private Instant lastSignalTime;
}