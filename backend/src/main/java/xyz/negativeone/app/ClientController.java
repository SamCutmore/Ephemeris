package xyz.negativeone.app;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import xyz.negativeone.app.solver.SolverDispatcher;

@RestController
public class ClientController {
  private static final Logger logger = LoggerFactory.getLogger(ClientController.class);

  private final SolverDispatcher dispatcher;
  private final JsonMapper jsonMapper;

  public ClientController(SolverDispatcher dispatcher, JsonMapper jsonMapper) {
    this.dispatcher = dispatcher;
    this.jsonMapper = jsonMapper;
  }

  @GetMapping({"/api", "/api/"})
  public ResponseEntity<?> get(@RequestParam Map<String, String> allParams) {
    return handleRequest(allParams, HttpMethod.GET);
  }

  @PostMapping({"/api", "/api/"})
  public ResponseEntity<?> post(@RequestParam Map<String, String> allParams) {
    return handleRequest(allParams, HttpMethod.POST);
  }

  private ResponseEntity<?> handleRequest(Map<String, String> allParams, HttpMethod method) {
    if (allParams.isEmpty()
        || !allParams.containsKey("q")
        || allParams.get("q") == null
        || allParams.get("q").isBlank()) {
      return ResponseEntity.badRequest().body("No queries passed");
    }

    String question = allParams.get("q");
    logger.info("{} q={}", method.name(), dispatcher.describeForLog(question));

    try {
      String result = dispatcher.solve(question);
      if (result == null) {
        return ResponseEntity.badRequest().body("No solver matched");
      }
      if (result.startsWith("[") || result.startsWith("{")) {
        JsonNode node = jsonMapper.readTree(result);
        return ResponseEntity.ok(node);
      }
      return ResponseEntity.ok(result);
    } catch (IllegalArgumentException e) {
      logger.warn("Failed to solve query: {}", e.getMessage());
      return ResponseEntity.badRequest().body(e.getMessage());
    } catch (Exception e) {
      logger.warn("Failed to solve query: {}", e.getMessage());
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
