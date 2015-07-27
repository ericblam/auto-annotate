import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 * AutoAnnotate
 * 
 * Auto annotator class to add annotations to all public (non static) methods in
 * specified folder (listed by "path" in config file)
 * 
 * Note: CONFIG_PATH must be an input argument to the program Under Run -> Run
 * Configurations -> Arguments, under "Program Arguments" paste the config path
 * 
 * @author ericla2
 *
 */
public class AutoAnnotate {

    public static final String CONFIG_PATH = "C:/Users/ericla2/git/AutoAnnotate/config.txt";
    /**
     * regex for detecting public functions, e.g. public void blah(); public int
     * blah(A a); public Map<Integer, String>blah(Map<Integer, Double> m);
     * 
     * but not public static void blah();
     */
    public static final String PUBLIC_FUNCTION_REGEX = "public\\s+\\w+(<.*>)?\\s+(\\w+)\\(";
    public static final int REGEX_GROUP = 2;

    public static String METRICS_IMPORT_PACKAGE = "";
    public static String METRICS_REPLACE_PACKAGE = "statsd";

    public static boolean ANNOTATE;
    public static boolean REPLACE_IMPORT;
    public static boolean REMOVE_ANNOTATIONS;

    public static void main(String[] args) throws Exception {

	if (args.length < 1) {
	    System.err
		    .println("Incorrect usage\n\nUsage: java AutoAnnotate <config-path>");
	    return;
	}
	String configPath = args[0];
	Properties p = loadProperties(configPath);

	sanityCheck(p);

	String path = p.getProperty("path");
	if (!path.endsWith("/")) {
	    path += "/";
	}
	String project = p.getProperty("project");
	String folder = p.getProperty("folder");
	String annotationType = p.getProperty("annotation");
	String annotationEnd = p.getProperty("annotationEnd");
	ANNOTATE = Boolean.parseBoolean(p.getProperty("annotate"));
	REPLACE_IMPORT = "true".equals(p.getProperty("replace_import"));
	REMOVE_ANNOTATIONS = "true".equals(p.getProperty("remove_annotations"));

	if (REPLACE_IMPORT || REMOVE_ANNOTATIONS) {
	    ANNOTATE = false;
	}

	if (REPLACE_IMPORT) {
	    METRICS_IMPORT_PACKAGE = p.getProperty("replace_orig") == null ? METRICS_IMPORT_PACKAGE
		    : p.getProperty("replace_orig");
	    METRICS_REPLACE_PACKAGE = p.getProperty("replace_new") == null ? METRICS_REPLACE_PACKAGE
		    : p.getProperty("replace_new");
	}

	List<String> classes = loadClasses(path);

	for (String classFile : classes) {
	    String newline = readNewlineType(path + classFile);

	    List<String> fileLines = createAnnotatedFileLines(path + classFile,
		    project, folder, annotationType, annotationEnd);

	    writeFile(path + classFile, fileLines, newline);
	}
    }

    /**
     * Loads properties from config file: folder, project, path, annotation, and
     * annotationEnd
     * 
     * @param path
     *            Path to config file
     * @return Properties from config file at path
     */
    public static Properties loadProperties(String path) {
	Properties p = new Properties();
	try {
	    p.load(new BufferedReader(new FileReader(path)));
	} catch (IOException e) {
	    e.printStackTrace();
	}
	return p;
    }

    /**
     * Checks if properties have been properly loaded
     * 
     * @param properties
     *            Properties from config file
     */
    private static void sanityCheck(Properties properties) {
	String print = "";
	boolean okay = true;
	if (properties.getProperty("path") == null) {
	    print += "\"path\" undefined in configuration file\n";
	    okay = false;
	}
	if (properties.getProperty("project") == null) {
	    print += "\"project\" undefined in configuration file\n";
	    okay = false;
	}
	if (properties.getProperty("folder") == null) {
	    print += "\"folder\" undefined in configuration file\n";
	    okay = false;
	}
	if (properties.getProperty("annotation") == null) {
	    print += "\"annotation\" undefined in configuration file\n";
	    okay = false;
	}
	if (properties.getProperty("annotationEnd") == null) {
	    print += "\"annotationEnd\" undefined in configuration file\n";
	    okay = false;
	}
	if (properties.getProperty("annotate") == null) {
	    print += "\"annotate\" undefined in configuration file\n";
	    okay = false;
	}

	if (!okay) {
	    System.err.println(print);
	    System.exit(1);
	}
    }

    /**
     * Reads in class files from directory
     * 
     * @param path
     *            path of directory
     * @return List of class files in directory path
     */
    public static List<String> loadClasses(String path) {
	List<String> classes = new ArrayList<String>();
	try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths
		.get(path))) {
	    for (Path file : stream) {
		classes.add(file.getFileName().toString());
	    }
	} catch (IOException | DirectoryIteratorException x) {
	    x.printStackTrace();
	}
	return classes;
    }

    /**
     * Reads in the type of newline, \n or \r\n
     * 
     * @param filepath
     *            path to file to determine newline type of
     * @return String representing newline type
     */
    public static String readNewlineType(String filepath) {
	BufferedReader br = null;
	try {
	    br = new BufferedReader(new FileReader(new File(filepath)));
	    char c;
	    while ((c = (char) br.read()) != -1) {
		if (c == '\n') {
		    if ((c = (char) br.read()) == '\r') {
			return "\n\r";
		    } else {
			return "\n";
		    }
		}
		if (c == '\r') {
		    return "\r\n";
		}
	    }
	} catch (FileNotFoundException e) {
	    e.printStackTrace();
	} catch (IOException e) {
	    e.printStackTrace();
	} finally {
	    try {
		if (br != null)
		    br.close();
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}
	return "";
    }

    /**
     * 
     * @param path
     *            Path of file to annotate
     * @param project
     *            Name of project to list as first part of name of annotation
     * @param folder
     *            Name of folder to list as second part of name of annotation
     * @param annotationType
     *            Type of annotation to add to files
     * @param annotationEnd
     *            Name of annotation to list as last part of name of annotation
     * @return List of lines of new annotated file
     * @throws IOException
     */
    public static List<String> createAnnotatedFileLines(String path,
	    String project, String folder, String annotationType,
	    String annotationEnd) throws IOException {
	List<String> fileLines = new ArrayList<String>();

	File file = new File(path);
	BufferedReader br = new BufferedReader(new FileReader(file));

	String line;
	boolean packageFound = false;
	boolean endOfImports = false;
	boolean imported = false;
	String importLine = "import " + METRICS_IMPORT_PACKAGE + "."
		+ annotationType + ";";
	while ((line = br.readLine()) != null) {
	    String trimmed = line.trim();
	    if (!packageFound) {
		// Wait until package found
		if (trimmed.startsWith("package ")) {
		    packageFound = true;
		}
		fileLines.add(line);
	    }
	    // Wait until annotation imported
	    else if (!imported) {
		// End of imports found
		if (!trimmed.startsWith("import ") && !trimmed.equals("")) {
		    endOfImports = true;
		}
		// Check if annotation has been imported
		if (trimmed.equals(importLine)) {
		    imported = true;
		    if (REPLACE_IMPORT) {
			line = "import " + METRICS_REPLACE_PACKAGE + "."
				+ annotationType + ";";
		    }
		}
		// Add import
		else if (endOfImports) {
		    imported = true;
		    if (ANNOTATE) {
			fileLines.add(importLine);
		    }
		}
		fileLines.add(line);
	    }
	    // Go through the rest and find all public methods
	    else {
		if (REMOVE_ANNOTATIONS) {
		    String publicMethodPattern = String.format("@%s",
			    annotationType);
		    Pattern r = Pattern.compile(publicMethodPattern);
		    Matcher m = r.matcher(trimmed);
		    if (m.find()) {
			continue;
		    }
		} else if (ANNOTATE) {
		    String publicMethodPattern = PUBLIC_FUNCTION_REGEX;
		    Pattern r = Pattern.compile(publicMethodPattern);
		    Matcher m = r.matcher(trimmed);
		    if (m.find()) {
			String methodName = m.group(REGEX_GROUP);
			String annotation = String
				.format("    @%s(name = \"%s.%s.%s.%s\", absolute = true)",
					annotationType, project, folder,
					methodName, annotationEnd);
			System.out.printf("Added %s in file %s\n",
				annotation.trim(), file);
			fileLines.add(annotation);
		    }
		}
		fileLines.add(line);
	    }
	}
	br.close();
	return fileLines;
    }

    /**
     * Writes contents of lines to file at path
     * 
     * @param path
     * @param lines
     * @param newline
     * @throws FileNotFoundException
     */
    public static void writeFile(String path, List<String> lines, String newline)
	    throws FileNotFoundException {
	PrintWriter pw = new PrintWriter(path);
	for (String line : lines)
	    pw.print(line + newline);
	pw.close();
    }

}
