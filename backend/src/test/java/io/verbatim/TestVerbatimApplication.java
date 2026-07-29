package io.verbatim;

import org.springframework.boot.SpringApplication;

public class TestVerbatimApplication {

	public static void main(String[] args) {
		SpringApplication.from(VerbatimApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
