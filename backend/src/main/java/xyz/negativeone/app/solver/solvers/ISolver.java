package xyz.negativeone.app.solver.solvers;

import java.util.regex.Matcher;
import xyz.negativeone.app.solver.Query;

public interface ISolver {

  Matcher match(Query query);

  String solve(Matcher m);
}
