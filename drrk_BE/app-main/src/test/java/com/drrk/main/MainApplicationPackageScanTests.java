package com.drrk.main;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class MainApplicationPackageScanTests {

	@Test
	void scansAllDrrkPackages() {
		SpringBootApplication annotation = MainApplication.class.getAnnotation(SpringBootApplication.class);

		assertThat(annotation.scanBasePackages()).containsExactly("com.drrk");
	}

}
