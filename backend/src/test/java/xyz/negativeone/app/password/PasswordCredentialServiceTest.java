package xyz.negativeone.app.password;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.negativeone.app.persistence.User;

/** Ported from SPRE PasswordManagement.PasswordCredentialServiceTest. */
class PasswordCredentialServiceTest {

  private PasswordManager passwordManager;
  private PasswordCredentialService service;

  @BeforeEach
  void setUp() {
    passwordManager = new PasswordManager();
    service = new PasswordCredentialService(passwordManager);
  }

  @Test
  void prepareNewUserCredentialsSetsArgon2() {
    User user = new User();
    service.prepareNewUserCredentials(user, "plain");

    assertNotNull(user.getPassword());
    assertTrue(user.getPassword().startsWith("$argon2"));
    assertTrue(passwordManager.matches("plain", user.getPassword()));
  }

  @Test
  void verifyLoginSucceedsWhenArgon2Matches() {
    User user = new User();
    user.setPassword(passwordManager.encodePassword("secret"));

    assertEquals(
        PasswordCredentialService.LoginOutcome.SUCCESS, service.verifyLogin(user, "secret"));
  }

  @Test
  void verifyLoginFailsWhenPasswordWrong() {
    User user = new User();
    user.setPassword(passwordManager.encodePassword("b"));

    assertEquals(PasswordCredentialService.LoginOutcome.FAILED, service.verifyLogin(user, "wrong"));
  }

  @Test
  void verifyLoginFailsWhenArgon2Missing() {
    User user = new User();
    user.setPassword(null);

    assertEquals(PasswordCredentialService.LoginOutcome.FAILED, service.verifyLogin(user, "y"));
  }

  @Test
  void verifyLoginFailsWhenStoredPasswordNull() {
    User user = new User();
    user.setPassword(null);

    assertEquals(
        PasswordCredentialService.LoginOutcome.FAILED, service.verifyLogin(user, "anything"));
  }

  @Test
  void verifyLoginFailsWhenStoredPasswordEmpty() {
    User user = new User();
    user.setPassword("");

    assertEquals(PasswordCredentialService.LoginOutcome.FAILED, service.verifyLogin(user, "ok"));
  }
}
