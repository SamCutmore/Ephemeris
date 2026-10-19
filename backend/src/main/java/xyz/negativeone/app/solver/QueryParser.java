package xyz.negativeone.app.solver;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class QueryParser implements IQueryParser {
  private static final Pattern TOKEN =
      Pattern.compile("^([^:]+)(?::|%3[Aa])\\s*(.*)$", Pattern.CASE_INSENSITIVE);

  public Query parse(String query) throws IllegalArgumentException {
    Matcher m = TOKEN.matcher(query);
    if (!m.find())
      throw new IllegalArgumentException(
          "Query does not match expected format: 'q=token: question'");
    String token = m.group(1).trim();
    String question = m.group(2).trim();
    return new Query(token, question);
  }

  @Override
  public abstract String solve(String question);
}
