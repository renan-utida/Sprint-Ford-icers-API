package com.icers.ford;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Nunca prod — este teste sobe o contexto Spring inteiro (DataSource real
// incluído), e só dev garante H2 em memória, sem tocar o Oracle da FIAP.
@SpringBootTest
@ActiveProfiles("dev")
class SprintFordApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
