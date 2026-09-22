package com.icers.ford;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

// Roda todos os testes dentro dos pacotes indicados
@Suite
@SelectPackages({
        "com.icers.ford.service",
        "com.icers.ford.security",
        "com.icers.ford.exception",
        "com.icers.ford.crypto",
        "com.icers.ford.model.converter"
})
public class SuiteDeTestesGeral {
    // Nenhum código necessário aqui
}
