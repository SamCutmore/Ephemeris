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
    Query q = parser.parse("listMarketGroups: root");
    assertEquals("listMarketGroups", q.token());
    assertEquals("root", q.question());
  }

  @Test
  void parseEncodedColonUpper() {
    Query q = parser.parse("listTypes%3A 12");
    assertEquals("listTypes", q.token());
    assertEquals("12", q.question());
  }

  @Test
  void parseEncodedColonLower() {
    Query q = parser.parse("getOrderBook%3a 1001;1");
    assertEquals("getOrderBook", q.token());
    assertEquals("1001;1", q.question());
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
