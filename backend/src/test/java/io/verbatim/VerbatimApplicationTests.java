package io.verbatim;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
	"verbatim.storage.root=target/test-data",
	"verbatim.codex.enabled=false",
	"verbatim.workflow.poll-delay=60000"
})
class VerbatimApplicationTests {

	@Test
	void contextLoads() {
	}

}
