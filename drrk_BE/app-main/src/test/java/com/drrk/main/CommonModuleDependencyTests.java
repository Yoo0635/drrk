package com.drrk.main;

import static org.assertj.core.api.Assertions.assertThat;

import com.drrk.domain.CommonDomainPackage;
import org.junit.jupiter.api.Test;

class CommonModuleDependencyTests {

	@Test
	void dependsOnCommonDomainPackage() {
		assertThat(CommonDomainPackage.class.getPackageName()).isEqualTo("com.drrk.domain");
	}

}
