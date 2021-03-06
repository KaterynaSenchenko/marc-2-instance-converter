package org.folio.rest.javascript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

/**
 * @author shale
 *
 */
public class JSManager {

  private static final Logger log = LoggerFactory.getLogger(JSManager.class);

  private static final ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
  private static final Map<Integer, CompiledScript> preCompiledJS = new HashMap<>();

  public static Object runJScript(String jscript, String data) throws ScriptException {
    CompiledScript script = preCompiledJS.get(jscript.hashCode());
    if(script == null){
      log.debug("compiling JS function: {}", jscript);
      script = ((Compilable) engine).compile(jscript);
      preCompiledJS.put(jscript.hashCode(), script);
    }
    Bindings bindings = new SimpleBindings();
    bindings.put("DATA", data);
    return script.eval(bindings);
  }
}
