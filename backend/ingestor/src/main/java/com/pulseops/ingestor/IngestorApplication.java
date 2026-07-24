package com.pulseops.ingestor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.TimeZone;

@SpringBootApplication
public class IngestorApplication {

	public static void main(String[] args) {
		// Force the JVM to use the modern timezone name so Postgres accepts the connection
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));

		SpringApplication.run(IngestorApplication.class, args);
	}
}