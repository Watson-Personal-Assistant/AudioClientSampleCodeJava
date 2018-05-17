package wa.util;
import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

public class Utils {
    // Initialize our logger
    private static final Logger LOG = LogManager.getLogger(Utils.class);

  public static void printJSON(JSONObject jdata) {
    if(jdata == null) {
      return;
    }
    Iterator<?> keys = jdata.keys();
    while( keys.hasNext() ) {
      String key = (String)keys.next();
      if( !key.equalsIgnoreCase("data")){
        Object value  = jdata.get(key);
          if ( value!=null ) {
            LOG.info( key + ":" + value.toString());
        }
      }
    }
  }
}
