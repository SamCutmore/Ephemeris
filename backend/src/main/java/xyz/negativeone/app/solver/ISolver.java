package xyz.negativeone.app.solver;

import java.util.regex.Matcher;

public interface ISolver {

  Matcher match(Query query);

  String solve(Matcher m);
}
