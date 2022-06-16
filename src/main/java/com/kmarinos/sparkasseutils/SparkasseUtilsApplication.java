package com.kmarinos.sparkasseutils;

import com.kmarinos.sparkasseutils.clients.Sparkasse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SparkasseUtilsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SparkasseUtilsApplication.class, args);
	}
	@Autowired
	Sparkasse sparkasse;

//	@Bean
	public CommandLineRunner run(){
		return args -> {
			sparkasse.printLatestBookings();
		};
	}

}
