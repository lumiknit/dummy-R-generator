package lumi.dummy.R;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Resources {
  private Map<String, Set<String>> map;
  private String cachedContents;

  public Resources() {
    this.map = new HashMap<String, Set<String>>();
    this.cachedContents = null;
  }

  public Resources put(String key, String value) {
    Set set = map.getOrDefault(key, null);
    if (set == null) {
      set = new HashSet<String>();
      map.put(key, set);
    }
    set.add(value);
    // Clear the cached contents
    this.cachedContents = null;
    return this;
  }

  /* Make the contents of R.java */
  public String makeFileContents(String pkgName) {
    String pkgLine = (pkgName != null && pkgName.length() > 0) ?
        "package " + pkgName + ";\n\n" :
        "";

    if(cachedContents == null) {
      int cnt = 0;
      StringBuilder builder = new StringBuilder();
      builder.append("public class R {\n");
      for (String key : map.keySet()) {
      Set<String> set = map.getOrDefault(key, null);
      if (set == null) continue;
      builder.append("  public static final class ").append(key).append(" {\n");
      for (String val : set) {
          builder.append("    public static final int ").append(val).append(" = 0x")
              .append(String.format("%08d", cnt)).append(";\n");
          cnt++;
        }
        builder.append("  }\n");
      }
      builder.append("}");
      cachedContents = builder.toString();
    }

    return pkgLine + cachedContents;
  }
}
