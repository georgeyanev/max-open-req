package akka.tests;

import spark.Request;
import spark.Response;
import spark.Service;

public class DummyHttpServer {
  private Service http;

  private int httpPort;

  public DummyHttpServer(int httpPort) {
    this.httpPort = httpPort;
  }

  public DummyHttpServer() {
    this(8480);
  }

  public void start() {
    http = Service.ignite();

    if (httpPort > 0) {
      http.port(httpPort);
    }

    http.post("/lookup", this::lookup);

    http.post("/longrunning", this::longRunning);

    http.awaitInitialization();
  }

  public void stop() {
    if (http != null)
      http.stop();
  }
  
  private Object lookup(Request req, Response res) {
    res.body("{}");
    res.type("application/json");
    res.status(200);
    return res.body();
  }

  private Object longRunning(Request req, Response res) throws InterruptedException {
    Thread.sleep(1000); // wait here to simulate long running request
    res.body("{}");
    res.type("application/json");
    res.status(200);
    return res.body();
  }
}
