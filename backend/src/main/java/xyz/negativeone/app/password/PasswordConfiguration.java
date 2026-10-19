package xyz.negativeone.app.password;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Ported from SPRE PasswordManagement.PasswordConfiguration. */
@Configuration
public class PasswordConfiguration {

  @Bean
  public PasswordManager passwordManager() {
    return new PasswordManager();
  }
}
