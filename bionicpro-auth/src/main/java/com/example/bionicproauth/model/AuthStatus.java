package com.example.bionicproauth.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthStatus {
    private boolean authenticated;
}