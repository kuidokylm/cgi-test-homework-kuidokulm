package com.example.homework;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class HomeworkApplicationTest {

  @Test
  void mainRunsSpringApplicationWithApplicationClassAndArguments() {
    String[] args = {"--spring.profiles.active=test"};

    try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
      HomeworkApplication.main(args);

      springApplication.verify(() -> SpringApplication.run(HomeworkApplication.class, args));
    }
  }
}
