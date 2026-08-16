package com.drrk.main;

import com.drrk.domain.user.User;
import com.drrk.main.consumer.inference.InferenceMessageReceipt;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.drrk")
@EntityScan(basePackageClasses = {User.class, InferenceMessageReceipt.class})
public class MainApplication {

	public static void main(String[] args) {
		SpringApplication.run(MainApplication.class, args);
	}

}
