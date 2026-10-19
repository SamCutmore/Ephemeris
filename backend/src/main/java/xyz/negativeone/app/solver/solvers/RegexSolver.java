package xyz.negativeone.app.solver.solvers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import xyz.negativeone.app.solver.Query;

public abstract class RegexSolver implements ISolver {

  private final Pattern pattern;

  protected RegexSolver(String regex) {
    this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
  }

  @Override
  public Matcher match(Query query) {
    Matcher m = pattern.matcher(query.question());
    if (m.find()) return m;
    return null;
  }

  public abstract String solve(Matcher m);
}
