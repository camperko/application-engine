package com.fmworkflow;

import com.fmworkflow.mail.IMailService;
import com.fmworkflow.mail.MailService;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@SpringBootApplication
@ComponentScan(basePackages = {"com.fmworkflow", "it.ozimov.springboot"})

public class WorkflowManagementSystemApplication {

	@Bean
	public JavaMailSenderImpl mailSender() {
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setPort(587);
		sender.setHost("smtp.mailgun.org");
		sender.setUsername("workflow@sandbox77b26b1bdb5a40439459609a0292b926.mailgun.org");
		sender.setPassword("workflow");
		Properties mailProperties = new Properties();
		mailProperties.put("mail.smtp.auth", true);
		mailProperties.put("mail.smtp.starttls.enable", true);
		mailProperties.put("mail.smtp.starttls.required", true);
		sender.setJavaMailProperties(mailProperties);
		return sender;
	}

	@Bean
	public VelocityEngine velocityEngine() {
		VelocityEngine engine = new VelocityEngine();
		engine.setProperty("resource.loader", "class");
		engine.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
		return engine;
	}

	@Bean
	public IMailService mailService(VelocityEngine velocityEngine, JavaMailSender mailSender) {
		MailService mailService = new MailService();
		mailService.setVelocityEngine(velocityEngine);
		mailService.setMailSender(mailSender);
		return mailService;
	}

	public static void main(String[] args) {
		SpringApplication.run(WorkflowManagementSystemApplication.class, args);
	}
}