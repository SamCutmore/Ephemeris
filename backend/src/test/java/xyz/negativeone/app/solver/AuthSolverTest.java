package xyz.negativeone.app.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.auth.SessionService;

@SpringBootTest
@ActiveProfiles("dev")
class AuthSolverTest {

  @Autowired private SolverDispatcher dispatcher;

  @Autowired private JsonMapper jsonMapper;

  @Autowired private SessionService sessions;

  @Test
  void registerLoginLogoutSuccessfulPath() {
    String user = "user-" + UUID.randomUUID();
    String password = "secret";

    assertEquals("OK", dispatcher.solve("register: " + user + ";" + password + ";" + password));

    String loginBody = dispatcher.solve("login: " + user + ";" + password);
    assertNotNull(loginBody);
    assertTrue(loginBody.startsWith("{"));

    JsonNode json = jsonMapper.readTree(loginBody);
    assertEquals(user, json.get("username").asString());
    assertTrue(json.get("userId").asInt() > 0);
    String token = json.get("token").asString();
    assertFalse(token.isBlank());
    assertTrue(sessions.resolveUserId(token).isPresent());

    assertEquals("OK", dispatcher.solve("logout: " + token));
    assertTrue(sessions.resolveUserId(token).isEmpty());
  }

  @Test
  void registerRejectsMismatchedPasswords() {
    String user = "user-" + UUID.randomUUID();
    assertEquals("Not OK", dispatcher.solve("register: " + user + ";a;b"));
  }

  @Test
  void registerRejectsDuplicateUsername() {
    String user = "user-" + UUID.randomUUID();
    assertEquals("OK", dispatcher.solve("register: " + user + ";secret;secret"));
    assertEquals("Not OK", dispatcher.solve("register: " + user + ";secret;secret"));
  }

  @Test
  void loginRejectsBadPassword() {
    String user = "user-" + UUID.randomUUID();
    assertEquals("OK", dispatcher.solve("register: " + user + ";secret;secret"));
    assertEquals("Not OK", dispatcher.solve("login: " + user + ";wrong"));
  }

  @Test
  void loginRejectsUnknownUser() {
    assertEquals("Not OK", dispatcher.solve("login: nobody-" + UUID.randomUUID() + ";secret"));
  }

  @Test
  void logoutRejectsUnknownToken() {
    assertEquals("Not OK", dispatcher.solve("logout: " + "a".repeat(64)));
  }
}
