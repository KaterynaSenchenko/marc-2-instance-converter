package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.apache.commons.io.IOUtils;
import org.folio.rest.jaxrs.resource.Load;
import org.folio.rest.tools.ClientGenerator;
import org.folio.rest.tools.utils.TenantTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.folio.rest.service.LoaderHelper.isPrimitiveOrPrimitiveWrapperOrString;


public class LoaderAPI implements Load {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoaderAPI.class);
  private static final String TENANT_ID_NULL = TenantTool.calculateTenantId(null);
  private static final String TENANT_NOT_SET = "tenant not set";

  // rules are not stored in db as this is a test loading module
  static final Map<String, JsonObject> TENANT_RULES_MAP = new HashMap<>();

  @Override
  public void postLoadMarcRules(InputStream entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(ClientGenerator.OKAPI_HEADER_TENANT));

    if (tenantId.equals(TENANT_ID_NULL)) {
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        PostLoadMarcRulesResponse.respond400WithTextPlain(TENANT_NOT_SET)));
      return;
    }

    String rulesFile = null;
    try {
      rulesFile = IOUtils.toString(entity, "UTF8");
    } catch (IOException e) {
      LOGGER.error("Error reading rules file", e);
    }

    try {
      TENANT_RULES_MAP.put(tenantId, new JsonObject(rulesFile));
    } catch (Exception e) {
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        PostLoadMarcRulesResponse.respond400WithTextPlain("File is not a valid json: " + e.getMessage())));
      return;
    }

    asyncResultHandler.handle(
      io.vertx.core.Future.succeededFuture(PostLoadMarcRulesResponse.respond201(PostLoadMarcRulesResponse.headersFor201())));
  }

  @Override
  public void postLoadMarcDataTest(InputStream entity, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String tenantId = TenantTool.calculateTenantId(okapiHeaders.get(ClientGenerator.OKAPI_HEADER_TENANT));

    if (validRequest(asyncResultHandler, okapiHeaders)) {
      Processor processor = new Processor(tenantId);
      processor.process(entity, vertxContext, asyncResultHandler);
    }
  }

  private boolean validRequest(Handler<AsyncResult<Response>> asyncResultHandler, Map<String, String> okapiHeaders){
    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(ClientGenerator.OKAPI_HEADER_TENANT));

    if (tenantId.equals(TENANT_ID_NULL)) {
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        PostLoadMarcDataTestResponse.respond400WithTextPlain(TENANT_NOT_SET)));
      return false;
    }

    JsonObject rulesFile = TENANT_RULES_MAP.get(tenantId);

    if(rulesFile == null){
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(
        PostLoadMarcDataTestResponse.respond400WithTextPlain("no rules file found for tenant " + tenantId)));
      return false;
    }

    return true;
  }

  /**
   *
   * @param object - the root object to start parsing the 'path' from
   * @param path - the target path - the field to place the value in
   * @param newComp - should a new object be created , if not, use the object passed into the
   * complexPreviouslyCreated parameter and continue populating it.
   * @param val
   * @param complexPreviouslyCreated - pass in a non primitive pojo that is already partially
   * populated from previous subfield values
   * @return
   */
  static boolean buildObject(Object object, String[] path, boolean newComp, Object val,
                             Object[] complexPreviouslyCreated) {
    Class<?> type;
    for (String pathSegment : path) {
      try {
        Field field = object.getClass().getDeclaredField(pathSegment);
        type = field.getType();
        if (type.isAssignableFrom(List.class) || type.isAssignableFrom(java.util.Set.class)) {

          Method method = object.getClass().getMethod(columnNametoCamelCaseWithget(pathSegment));
          Collection<Object> coll = setColl(method, object);
          ParameterizedType listType = (ParameterizedType) field.getGenericType();
          Class<?> listTypeClass = (Class<?>) listType.getActualTypeArguments()[0];
          if (isPrimitiveOrPrimitiveWrapperOrString(listTypeClass)) {
            coll.add(val);
          } else {
            object = setObjectCorrectly(newComp, listTypeClass, type, pathSegment, coll, object, complexPreviouslyCreated[0]);
            complexPreviouslyCreated[0] = object;
          }
        } else if (!isPrimitiveOrPrimitiveWrapperOrString(type)) {

          //currently not needed for instances, may be needed in the future
          //non primitive member in instance object but represented as a list or set of non
          //primitive objects
          Method method = object.getClass().getMethod(columnNametoCamelCaseWithget(pathSegment));
          object = method.invoke(object);
        } else { // primitive
          object.getClass().getMethod(columnNametoCamelCaseWithset(pathSegment),
            val.getClass()).invoke(object, val);
        }
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
        return false;
      }
    }
    return true;
  }

  private static Object setObjectCorrectly(boolean newComp, Class<?> listTypeClass, Class<?> type, String pathSegment,
                                           Collection<Object> coll, Object object, Object complexPreviouslyCreated)
    throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {

    if (newComp) {
      Object o = listTypeClass.newInstance();
      coll.add(o);
      object.getClass().getMethod(columnNametoCamelCaseWithset(pathSegment), type).invoke(object, coll);
      return o;
    } else if ((complexPreviouslyCreated != null) &&
      (complexPreviouslyCreated.getClass().isAssignableFrom(listTypeClass))) {
      return complexPreviouslyCreated;
    }
    return object;
  }

  private static Collection<Object> setColl(Method method, Object object) throws InvocationTargetException,
    IllegalAccessException {
    return ((Collection<Object>) method.invoke(object));
  }

  private static String columnNametoCamelCaseWithset(String str) {
    StringBuilder sb = new StringBuilder(str);
    sb.replace(0, 1, String.valueOf(Character.toUpperCase(sb.charAt(0))));
    for (int i = 0; i < sb.length(); i++) {
      if (sb.charAt(i) == '_') {
        sb.deleteCharAt(i);
        sb.replace(i, i + 1, String.valueOf(Character.toUpperCase(sb.charAt(i))));
      }
    }
    return "set" + sb.toString();
  }

  private static String columnNametoCamelCaseWithget(String str) {
    StringBuilder sb = new StringBuilder(str);
    sb.replace(0, 1, String.valueOf(Character.toUpperCase(sb.charAt(0))));
    for (int i = 0; i < sb.length(); i++) {
      if (sb.charAt(i) == '_') {
        sb.deleteCharAt(i);
        sb.replace(i, i + 1, String.valueOf(Character.toUpperCase(sb.charAt(i))));
      }
    }
    return "get" + sb.toString();
  }
}
