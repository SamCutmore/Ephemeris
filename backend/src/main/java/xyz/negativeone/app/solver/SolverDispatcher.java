package xyz.negativeone.app.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.auth.IngestTokenService;
import xyz.negativeone.app.auth.SessionService;
import xyz.negativeone.app.market.MarketMaker;
import xyz.negativeone.app.password.PasswordCredentialService;
import xyz.negativeone.app.persistence.InvTypeRepository;
import xyz.negativeone.app.persistence.LocationRepository;
import xyz.negativeone.app.persistence.MarketFillRepository;
import xyz.negativeone.app.persistence.MarketGroupRepository;
import xyz.negativeone.app.persistence.MarketHistoryRepository;
import xyz.negativeone.app.persistence.MarketOrderRepository;
import xyz.negativeone.app.persistence.MarketPriceRepository;
import xyz.negativeone.app.persistence.UserRepository;
import xyz.negativeone.app.solver.solvers.CancelOrderSolver;
import xyz.negativeone.app.solver.solvers.CancelSellOrderSolver;
import xyz.negativeone.app.solver.solvers.CancelStaleSellOrdersSolver;
import xyz.negativeone.app.solver.solvers.EditOrderSolver;
import xyz.negativeone.app.solver.solvers.EditSellOrderSolver;
import xyz.negativeone.app.solver.solvers.GetHistorySolver;
import xyz.negativeone.app.solver.solvers.GetMarketPriceSolver;
import xyz.negativeone.app.solver.solvers.GetMyOrdersSolver;
import xyz.negativeone.app.solver.solvers.GetOrderBookSolver;
import xyz.negativeone.app.solver.solvers.ISolver;
import xyz.negativeone.app.solver.solvers.ListLocationsSolver;
import xyz.negativeone.app.solver.solvers.ListMarketGroupsSolver;
import xyz.negativeone.app.solver.solvers.ListMyOrdersSolver;
import xyz.negativeone.app.solver.solvers.ListMyTransactionsSolver;
import xyz.negativeone.app.solver.solvers.ListTypesSolver;
import xyz.negativeone.app.solver.solvers.LoginSolver;
import xyz.negativeone.app.solver.solvers.LogoutSolver;
import xyz.negativeone.app.solver.solvers.PlaceBuyOrderSolver;
import xyz.negativeone.app.solver.solvers.PlaceSellOrderSolver;
import xyz.negativeone.app.solver.solvers.ProtectedSolver;
import xyz.negativeone.app.solver.solvers.RefreshMarketPriceSolver;
import xyz.negativeone.app.solver.solvers.RegisterSolver;
import xyz.negativeone.app.solver.solvers.SearchTypesSolver;

@Component
public class SolverDispatcher extends QueryParser {
  private final List<ISolver> solvers = new ArrayList<>();

  public SolverDispatcher(
      UserRepository users,
      PasswordCredentialService credentials,
      SessionService sessions,
      MarketGroupRepository marketGroups,
      InvTypeRepository invTypes,
      LocationRepository locations,
      MarketOrderRepository marketOrders,
      MarketHistoryRepository marketHistory,
      MarketFillRepository marketFills,
      MarketPriceRepository marketPrices,
      MarketMaker marketMaker,
      IngestTokenService ingestTokens,
      JsonMapper jsonMapper) {

    solvers.add(new RegisterSolver(users, credentials));
    solvers.add(new LoginSolver(users, credentials, sessions, jsonMapper));
    solvers.add(new LogoutSolver(sessions));
    solvers.add(new ListMarketGroupsSolver(marketGroups, jsonMapper));
    solvers.add(new ListTypesSolver(invTypes, jsonMapper));
    solvers.add(new SearchTypesSolver(invTypes, jsonMapper));
    solvers.add(new ListLocationsSolver(locations, jsonMapper));
    solvers.add(new GetOrderBookSolver(marketOrders, jsonMapper));
    solvers.add(new GetHistorySolver(marketHistory, jsonMapper));
    solvers.add(new GetMarketPriceSolver(marketPrices, jsonMapper));
    solvers.add(new RefreshMarketPriceSolver(invTypes, marketPrices, jsonMapper));
    solvers.add(new PlaceBuyOrderSolver(sessions, invTypes, marketOrders, marketMaker, jsonMapper));
    solvers.add(new CancelOrderSolver(sessions, marketOrders, jsonMapper));
    solvers.add(new EditOrderSolver(sessions, marketOrders, marketMaker, jsonMapper));
    solvers.add(
        new PlaceSellOrderSolver(ingestTokens, invTypes, marketOrders, marketMaker, jsonMapper));
    solvers.add(new EditSellOrderSolver(ingestTokens, marketOrders, marketMaker, jsonMapper));
    solvers.add(new CancelSellOrderSolver(ingestTokens, marketOrders, jsonMapper));
    solvers.add(new CancelStaleSellOrdersSolver(ingestTokens, marketOrders, jsonMapper));
    solvers.add(new ListMyOrdersSolver(sessions, marketOrders, jsonMapper));
    solvers.add(new ListMyTransactionsSolver(sessions, marketOrders, marketFills, jsonMapper));
    solvers.add(new GetMyOrdersSolver(sessions, marketOrders, jsonMapper));
  }

  @Override
  public String solve(String question) throws IllegalArgumentException {
    Query q = super.parse(question);
    Matcher m;
    for (ISolver solver : solvers) {
      m = solver.match(q);
      if (null == m) continue;
      return solver.solve(m);
    }
    return null;
  }

  /** Protect secrets for logging. */
  public String describeForLog(String question) {
    if (question == null || question.isBlank()) {
      return "";
    }
    try {
      Query q = super.parse(question);
      for (ISolver solver : solvers) {
        if (solver.match(q) == null) {
          continue;
        }
        if (solver instanceof ProtectedSolver protectedSolver) {
          return protectedSolver.sanitizeForLog(q);
        }
        return question;
      }
    } catch (IllegalArgumentException ignored) {
      return "(unparsed)";
    }
    return question;
  }
}
