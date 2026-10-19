package xyz.negativeone.app.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Adapted from SPRE ExtremeSolver.QueryParserTests */
class QueryParserTest {

  private final QueryParser parser =
      new QueryParser() {
        @Override
        public String solve(String question) {
          throw new UnsupportedOperationException();
        }
      };

  @Test
  void parseExplicitColon() {
    Query q = parser.parse("login: alice;secret");
    assertEquals("login", q.token());
    assertEquals("alice;secret", q.question());
  }

  @Test
  void parseEncodedColonUpper() {
    Query q = parser.parse("register%3A bob;secret;secret");
    assertEquals("register", q.token());
    assertEquals("bob;secret;secret", q.question());
  }

  @Test
  void parseEncodedColonLower() {
    Query q = parser.parse("logout%3a " + "a".repeat(64));
    assertEquals("logout", q.token());
    assertEquals("a".repeat(64), q.question());
  }

  @Test
  void parseTrimsWhitespace() {
    Query q = parser.parse("  login:  alice;secret  ");
    assertEquals("login", q.token());
    assertEquals("alice;secret", q.question());
  }

  @Test
  void parseRejectsMissingSeparator() {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("no-separator-here"));
  }
}
