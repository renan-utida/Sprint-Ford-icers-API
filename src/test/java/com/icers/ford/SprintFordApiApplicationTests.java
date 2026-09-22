package com.icers.ford;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// dev-h2, nunca dev/prod — este teste sobe o contexto Spring inteiro
// (DataSource real incluído) e não pode tocar o Oracle da FIAP.
@SpringBootTest
@ActiveProfiles("dev-h2")
class SprintFordApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
