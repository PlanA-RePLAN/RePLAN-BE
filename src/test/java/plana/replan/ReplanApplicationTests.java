package plana.replan;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import plana.replan.global.config.TestFirebaseConfig;

@SpringBootTest
@Import(TestFirebaseConfig.class)
class ReplanApplicationTests {

  @Test
  void contextLoads() {}
}
