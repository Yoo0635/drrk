package com.drrk.collector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class CollectorApplicationPackageScanTests {

	@Test
	void scansAllDrrkPackages() {
		SpringBootApplication annotation = CollectorApplication.class.getAnnotation(SpringBootApplication.class);

		assertThat(annotation.scanBasePackages()).containsExactly("com.drrk");
	}

}
