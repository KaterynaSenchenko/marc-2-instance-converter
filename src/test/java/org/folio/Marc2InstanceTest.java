package org.folio;

import io.restassured.RestAssured;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import static org.junit.Assert.assertTrue;

@RunWith(VertxUnitRunner.class)
public class Marc2InstanceTest extends AbstractRestVerticleTest {

  @Test
  public void marc2Instance(TestContext context) throws IOException {
    Async async = context.async();
    ClassLoader classLoader = getClass().getClassLoader();
    File rulesFile = new File(Objects.requireNonNull(classLoader.getResource("rules.json")).getFile());
    RestAssured.given()
      .spec(spec)
      .when()
      .body(FileUtils.openInputStream(rulesFile))
      .post("/load/marc-rules")
      .then()
      .log().all()
      .statusCode(HttpStatus.SC_CREATED);
    async.complete();

    async = context.async();
    File file = new File(Objects.requireNonNull(classLoader.getResource("CornellFOLIOExemplars_Bibs.mrc")).getFile());

    RestAssured.given()
      .spec(spec)
      .when()
      .body(FileUtils.openInputStream(file))
      .post("/load/marc-data/test")
      .then()
      .log().all()
      .statusCode(HttpStatus.SC_CREATED);

    assertTrue(new File("instanceObjects").exists());
    async.complete();
  }

}
