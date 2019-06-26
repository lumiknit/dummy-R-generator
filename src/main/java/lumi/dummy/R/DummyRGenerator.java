package lumi.dummy.R;

import java.io.*;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class DummyRGenerator {
  /* Generator Options */
  private GeneratorOptions options;

  /* Collected resource informations during generation */
  private Resources resources;

  /* Path - Package pair structure */
  private static class PathPackagePair {
    public String path;
    public String pkg;

    public PathPackagePair(String path, String pkg) {
      this.path = path;
      this.pkg = pkg;
    }
  }

  /* Container of every path where R.java is required */
  private Set<PathPackagePair> possibleRPaths;

  /* Constructor */
  public DummyRGenerator(GeneratorOptions options) {
    this.options = options;
    /* Below will be initialized in the `run` method */
    this.resources = null;
    this.possibleRPaths = null;
  }

  /* Simple log helpers */
  private void logI(String s) {
    System.out.println("[INFO] " + s);
  }
  private void logE(String s) {
    System.out.println("[ERROR] " + s);
  }

  /* File RW Helpers */
  private String readFile(File file) {
    try {
      FileInputStream fis = new FileInputStream(file);
      byte[] data = new byte[(int)file.length()];
      fis.read(data);
      fis.close();
      return new String(data);
    } catch(IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private void writeFile(File file, String contents) {
    try {
      FileOutputStream fos = new FileOutputStream(file);
      fos.write(contents.getBytes());
      fos.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /* Extract the part of the given filename before the first dot */
  private String firstNameOf(String filename) {
    if (filename == null) return "";
    int idx = filename.indexOf('.');
    return (idx < 0) ? filename : filename.substring(0, idx);
  }

  /* Extract the extension of the given filename */
  private String extensionOf(String filename) {
    if(filename == null) return "";
    int idx = filename.lastIndexOf('.');
    return (idx < 0) ? filename : filename.substring(idx + 1);
  }

  /* Regular expressions for scanning source codes */
  /*  "package <PACKAGE-NAME>;" */
  private static final String packagePatternString = "\\bpackage\\s+([a-zA-Z0-9_$.]+)\\s*;";
  /*  "import <PACKAGE-NAME>;" */
  private static final String importRPatternString = "\\bimport\\s+([a-zA-Z0-9_$.]+).R\\s*;";
  /*  "<JAVA-ID>" */
  private static final String identifierPatternString = "\\b([a-zA-Z0-9_$.]+)\\b";
  /*  "<JAVA-ID>-<JAVA-ID>" */
  private static final String identifier2PatternString = "\\b([a-zA-Z0-9_$.]+)-([a-zA-Z0-9_$.]+)\\b";
  /*  "R.<JAVA-ID>.<JAVA-ID>" */
  private static final String rDotPatternString = "\\bR\\.([a-zA-Z0-9_$]+)\\.([a-zA-Z0-9_$]+)\\b";

  /* Look over the directory which looks like containing resources */
  private void lookResourceFolder(File file) {
    Pattern idPattern = Pattern.compile(identifierPatternString);
    Pattern id2Pattern = Pattern.compile(identifier2PatternString);

    if (file.listFiles() != null) {
      for (File sub : file.listFiles()) {
        if (!sub.isDirectory()) continue;
        // Extract the resource type
        String sn = sub.getName();
        Matcher m = id2Pattern.matcher(sn);
        if (m.matches()) {
          sn = m.group(1);
        } else if (!idPattern.matcher(sn).matches()) {
          continue;
        }

        // Scan all files in the directory
        if (sub.list() != null) {
          for (String fn : sub.list()) {
            String first = firstNameOf(fn);
            if (!idPattern.matcher(first).matches()) continue;
            resources.put(sn, first);
          }
        }
      }
    }
  }

  /* Look over the Java source code */
  private void lookJava(File file) throws Exception {
    Pattern packagePattern = Pattern.compile(packagePatternString);
    Pattern importRPattern = Pattern.compile(importRPatternString);
    Pattern rDotPattern = Pattern.compile(rDotPatternString);

    String contents = readFile(file);
    if (contents == null) {
      throw new Exception("Cannot read file '" + file.getCanonicalPath() + "'");
    }

    // Extract package name
    String pkgName = null;
    Matcher mPkg = packagePattern.matcher(contents);
    if (mPkg.find()) {
      pkgName = mPkg.group(1);
    }

    // Find out whether the code imports R
    String importR = null;
    Matcher mImp = importRPattern.matcher(contents);
    while (mImp.find()) {
      importR = mImp.group(1);
    }

    boolean usingR = false;

    // Extract Identifiers
    Matcher mR = rDotPattern.matcher(contents);
    while (mR.find()) {
      usingR = true;
      resources.put(mR.group(1), mR.group(2));
    }

    if (usingR) { // If the code containing R.~
      // Guess the top directory of source codes
      int depth = (pkgName == null) ? 0 : pkgName.split("\\.").length;
      File top = file.getParentFile();
      for (int i = 0; i < depth; i++) top = top.getParentFile();
      // Guess the possible position and package of R.java
      String path, pkg;
      if (importR != null) {
        path = top.getPath() + "/" + importR.replace('.', '/');
        pkg = importR;
      } else if (pkgName != null) {
        path = top.getPath() + "/" + pkgName.replace('.', '/');
        pkg = pkgName;
      } else {
        path = top.getPath();
        pkg = null;
      }
      possibleRPaths.add(new PathPackagePair(path, pkg));
    }
  }

  /* Look over the XML file */
  private void lookXML(File file) throws Exception {
    String contents = readFile(file);
    if (contents == null) {
      throw new Exception("Cannot read file '" + file.getPath() + "'");
    }

    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = dbf.newDocumentBuilder();
      InputSource is = new InputSource(new StringReader(contents));
      Document document = db.parse(is);

      // Traverse resources/<RES-TYPE>/<VALUE>
      NodeList list = document.getElementsByTagName("resources");
      for (int i = 0; i < list.getLength(); i++) {
        Node e = list.item(i);
        NodeList childNodes = e.getChildNodes();
        for (int j = 0; j < childNodes.getLength(); j++) {
          Node n = childNodes.item(j);
          if (n instanceof Element) {
            Element c = (Element)n;
            resources.put(c.getNodeName(), c.getAttribute("name"));
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /* Traverse the given directory recursively */
  private void traverseDirectory(File base) throws Exception {
    if (base.isDirectory()) {
      if (base.getName().equals("res")) {
        lookResourceFolder(base);
      }
      if (base.listFiles() != null) {
        for (File f : base.listFiles()) {
          traverseDirectory(f);
        }
      }
    } else {
      String filename = base.getName();
      String extension = extensionOf(filename);
      switch (extension) {
        case "java":
          lookJava(base);
          break;
        case "xml":
          lookXML(base);
          break;
      }
    }
  }

  /* Make R.java for every possible directory */
  private void makeRFiles() {
    for (PathPackagePair e : possibleRPaths) {
      File dir = new File(e.path + "/R.java");
      logI("Make '" + dir.getPath() + "'");
      writeFile(dir, resources.makeFileContents(e.pkg));
    }
  }

  public void run() throws GenerateFailedException {
    if (options.rootDirectory == null) {
      logE("No root directory was given");
      throw new GenerateFailedException();
    }

    resources = new Resources();
    possibleRPaths = new HashSet<PathPackagePair>();

    File root = new File(options.rootDirectory);
    try {
      traverseDirectory(root);
    } catch (Exception e) {
      throw new GenerateFailedException();
    }

    if (options.printToScreen) {
      logI("Result");
      System.out.println(resources.makeFileContents(null));
    }

    if (options.writeToFile) {
      makeRFiles();
    }
  }
}
