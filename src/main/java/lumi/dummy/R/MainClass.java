package lumi.dummy.R;

public class MainClass {
  public static void printUsage(String[] args) {
    System.out.println("usage: java -jar dummy-R-generator.jar [OPTIONS] <PROJECT-DIRECTORY>");
    System.out.println(" --no-file    Do not make R.java");
    System.out.println(" --no-print   Do not print the contents of generated R.java to STDOUT");
  }

  public static GeneratorOptions parseArgs(String[] args) {
    GeneratorOptions options = new GeneratorOptions();
    for (String a : args) {
      if (a.length() > 0 && a.charAt(0) == '-') {
        switch (a) {
          case "--no-file":
            options.writeToFile = false;
            break;
          case "--no-print":
            options.printToScreen = false;
            break;
          default:
            System.out.println("[ERROR] Unknown option '" + a + "'");
            System.exit(0);
        }
      } else {
        options.rootDirectory = a;
      }
    }
    return options;
  }

  public static void main(String[] args) {
    GeneratorOptions options = parseArgs(args);

    if (options.rootDirectory == null) {
      printUsage(args);
      System.exit(0);
    }

    DummyRGenerator generator = new DummyRGenerator(options);

    try {
      generator.run();
    } catch (GenerateFailedException e) {
      System.out.println("Generate failed");
    }
  }
}
