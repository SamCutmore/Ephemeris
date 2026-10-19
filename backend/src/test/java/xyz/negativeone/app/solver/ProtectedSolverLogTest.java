package xyz.negativeone.app.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class ProtectedSolverLogTest {

  @Autowired private SolverDispatcher dispatcher;

  @Test
  void openSolversLogFullQuery() {
    String q = "getOrderBook: 1001;1";
    assertEquals(q, dispatcher.describeForLog(q));
  }

  @Test
  void ingestSolversRedactIngestionToken() {
    String secret = "dev-ingest-token";
    String logged = dispatcher.describeForLog("placeSellOrder: 1001;1;2;195;1;listing;" + secret);
    assertEquals("placeSellOrder: 1001;1;2;195;1;listing;***", logged);
    assertFalse(logged.contains(secret));
  }

  @Test
  void loginRedactsPassword() {
    String logged = dispatcher.describeForLog("login: alice;hunter2");
    assertEquals("login: alice;***", logged);
    assertFalse(logged.contains("hunter2"));
  }

  @Test
  void registerRedactsPasswords() {
    String logged = dispatcher.describeForLog("register: bob;pw1;pw1");
    assertEquals("register: bob;***;***", logged);
    assertFalse(logged.contains("pw1"));
  }

  @Test
  void sessionTokenSolversRedactPayload() {
    assertEquals(
        "listMyOrders: ***", dispatcher.describeForLog("listMyOrders: " + "ab".repeat(32)));
    assertTrue(dispatcher.describeForLog("placeBuyOrder: 1001;1;195;1;abcdef").endsWith(";***"));
  }
}
