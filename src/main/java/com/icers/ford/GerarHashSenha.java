package com.icers.ford;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GerarHashSenha {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        System.out.println("Admin:    " + encoder.encode("Admin@2026"));
        System.out.println("Analyst:  " + encoder.encode("Analyst@2026"));
    }
}