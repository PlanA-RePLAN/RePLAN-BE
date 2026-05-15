package plana.replan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReplanApplication {

  public static void main(String[] args) {
    SpringApplication.run(ReplanApplication.class, args);
  }
}
