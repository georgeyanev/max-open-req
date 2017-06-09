package akka.tests;

import static com.typesafe.config.ConfigValueFactory.fromAnyRef;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.ActorMaterializer;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AkkaHttpTest {
  private static ActorSystem system;
  private static ActorMaterializer materializer;
  private static DummyHttpServer dummyHttpServer = new DummyHttpServer();


  @BeforeClass
  public static void beforeClass() {
    dummyHttpServer.start();
  }

  @AfterClass
  public static void afterClass() {
    dummyHttpServer.stop();
  }

  @Before
  public void before() {
    system = ActorSystem.create("test", ConfigFactory.empty()
        .withValue("akka.loglevel", fromAnyRef("DEBUG"))
        .withValue("akka.http.host-connection-pool.max-connections", fromAnyRef("1"))
        .withValue("akka.http.host-connection-pool.min-connections", fromAnyRef("1"))
        .withValue("akka.http.host-connection-pool.max-retries", fromAnyRef("0"))
        .withValue("akka.http.host-connection-pool.max-open-requests", fromAnyRef("1"))
        .withValue("akka.http.host-connection-pool.idle-timeout", fromAnyRef("infinite")));
    materializer = ActorMaterializer.create(system);
  }

  @After
  public void after() throws TimeoutException, InterruptedException {
    Await.ready(system.terminate(), Duration.apply("5s"));
  }

  @Test(expected = ExecutionException.class)
  public void testOverload() throws ExecutionException, InterruptedException, TimeoutException {
    final String longRunningUrl = "http://localhost:8480/longrunning";
    final String lookUpUrl = "http://localhost:8480/lookup";
    HttpResponse res;
    HttpEntity.Strict strict;

    // issue a http request which gets its response fast:
    final CompletionStage<HttpResponse> firstResponseFuture =
        Http.get(system)
            .singleRequest(
                HttpRequest
                    .POST(lookUpUrl)
                    .withEntity(ContentTypes.APPLICATION_JSON, "{}"),
                materializer);

    res = firstResponseFuture.toCompletableFuture().get();
    strict = res.entity().toStrict(100, materializer).toCompletableFuture().get();

    // issue a http request which gets its response slow:
    final CompletionStage<HttpResponse> secondResponseFuture =
        Http.get(system)
            .singleRequest(
                HttpRequest
                    .POST(longRunningUrl)
                    .withEntity(ContentTypes.APPLICATION_JSON, "{}"),
                materializer);

    // issue the next request right after the previous to cause BufferOverflow due to
    // max-open-requests = 1
    final CompletionStage<HttpResponse> thirdResponseFuture =
        Http.get(system)
            .singleRequest(
                HttpRequest
                    .POST(longRunningUrl)
                    .withEntity(ContentTypes.APPLICATION_JSON, "{}"),
                materializer);

    // expect BufferOverflowException here but it does not happen
    // instead the future never completes
    thirdResponseFuture.toCompletableFuture().get(5, TimeUnit.SECONDS);
  }

  @Test(expected = ExecutionException.class)
  public void testOverloadSuccess() throws ExecutionException, InterruptedException, TimeoutException {
    final String longRunningUrl = "http://localhost:8480/longrunning";
    final String lookUpUrl = "http://localhost:8480/lookup";

    // issue a http request which gets its response slow:
    final CompletionStage<HttpResponse> secondResponseFuture =
        Http.get(system)
            .singleRequest(
                HttpRequest
                    .POST(longRunningUrl)
                    .withEntity(ContentTypes.APPLICATION_JSON, "{}"),
                materializer);

    // issue the next request right after the previous to cause BufferOverflow due to
    // max-open-requests = 1
    final CompletionStage<HttpResponse> thirdResponseFuture =
        Http.get(system)
            .singleRequest(
                HttpRequest
                    .POST(lookUpUrl)
                    .withEntity(ContentTypes.APPLICATION_JSON, "{}"),
                materializer);

    // expect BufferOverflowException here
    thirdResponseFuture.toCompletableFuture().get(10, TimeUnit.SECONDS);
  }
}
