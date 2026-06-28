package plana.replan.global.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "apple")
public class AppleProperties {
  private List<String> clientIds = List.of();
  private String teamId;
  private String keyId;
  private String privateKey;
}
