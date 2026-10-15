import java.util.*;
import java.util.regex.*;
import java.io.*;

/**
 * DocumentionCreation - JavaDoc HTML Documentation Generator.
 * Parses Javadoc comments, multiple classes, interfaces, enums, constructors,
 * and methods across single or multiple Java source files, and generates
 * an indexed single-page HTML API reference document (file.html).
 */
public class DocumentionCreation {

    File f;
    int refno = 1;
    FileInputStream fin;
    BufferedReader br;
    StringBuffer[] sb4 = new StringBuffer[8];
    int maintainIndexOfBuffer;
    StringBuffer sb3 = new StringBuffer();
    StringBuffer sb7 = new StringBuffer();

    static class ClassScope {
        String name;
        int depth;
        ClassScope(String name, int depth) {
            this.name = name;
            this.depth = depth;
        }
    }

    public static void main(String[] args) {
        DocumentionCreation dr = new DocumentionCreation();

        // Initializing each element of the buffer
        for (int i = 0; i < dr.sb4.length; i++) {
            dr.sb4[i] = new StringBuffer("");
        }

        Scanner st = new Scanner(System.in);
        String fileName = null;

        if (args != null && args.length > 0) {
            fileName = args[0];
        }

        while (true) {
            if (fileName == null || fileName.trim().isEmpty()) {
                System.out.println("Enter the name of the file or directory:");
                if (!st.hasNextLine()) {
                    break;
                }
                fileName = st.nextLine().trim();
                if (fileName.isEmpty() && st.hasNextLine()) {
                    fileName = st.nextLine().trim();
                }
            }

            File target = new File(fileName);
            if (!target.exists()) {
                target = new File("C:/Users/Admin/Desktop/" + fileName);
            }
            if (!target.exists()) {
                target = new File(System.getProperty("user.dir"), fileName);
            }

            if (target.exists()) {
                dr.processInput(target);
                System.out.println("Documentation successfully generated in file.html!");
                break;
            } else {
                System.out.println("the file or directory does not exist: " + fileName);
                fileName = null;
            }
        }
    }

    /**
     * Processes a single file or a directory containing multiple Java files.
     * @param target file or directory handle
     */
    public void processInput(File target) {
        if (target.isDirectory()) {
            File[] javaFiles = target.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".java");
                }
            });
            if (javaFiles != null && javaFiles.length > 0) {
                Arrays.sort(javaFiles, Comparator.comparing(File::getName));
                for (File jf : javaFiles) {
                    String baseName = jf.getName().replace(".java", "");
                    ExtractCommentFromTheData(ReadDataFromfile(jf), baseName);
                }
            }
        } else {
            String baseName = target.getName().replace(".java", "");
            ExtractCommentFromTheData(ReadDataFromfile(target), baseName);
        }
        CreateHtmlFileAndAddDataToIt(sb3, sb7);
    }

    /**
     * Reads the data from the existing file and returns it as a StringBuffer.
     * @param f target file
     * @return contents of the file
     */
    public StringBuffer ReadDataFromfile(File f) {
        StringBuffer sb = new StringBuffer();
        String data = "";
        try {
            fin = new FileInputStream(f);
            br = new BufferedReader(new InputStreamReader(fin));
            while ((data = br.readLine()) != null) {
                sb.append(data);
                sb.append("\n");
            }
            br.close();
            fin.close();
            return sb;
        } catch (Exception e) {
            System.out.println("error reading file: " + e.getMessage());
            return sb;
        }
    }

    /**
     * Overload for backward compatibility.
     * @param sb source code buffer
     */
    public void ExtractCommentFromTheData(StringBuffer sb) {
        ExtractCommentFromTheData(sb, "MainClass");
    }

    /**
     * Extracts Javadoc comments, classes, interfaces, enums, constructors, and methods
     * from the source code, supporting multiple classes in a file or project.
     * Tracks lexical scope so nested and sibling classes are correctly attributed.
     * @param sb source code buffer
     * @param defaultClassName fallback class name derived from filename
     */
    public void ExtractCommentFromTheData(StringBuffer sb, String defaultClassName) {
        String source = sb.toString();
        // Remove single-line comments so they don't break regex matching
        String cleanSource = source.replaceAll("//.*?\n", "\n");

        Deque<ClassScope> classStack = new ArrayDeque<>();
        int braceDepth = 0;
        int lastPos = 0;

        // Match doc comment blocks /** ... */ and the following code segment
        Pattern docBlockPattern = Pattern.compile("(/\\*\\*)(.*?)(\\*/)(.*?)(?=(/\\*\\*)|$)", Pattern.DOTALL);
        Matcher docMatcher = docBlockPattern.matcher(cleanSource);

        while (docMatcher.find()) {
            int matchStart = docMatcher.start();

            // Track braces between last processed position and current match start
            for (int p = lastPos; p < matchStart; p++) {
                char ch = cleanSource.charAt(p);
                if (ch == '{') {
                    braceDepth++;
                    String preceding = cleanSource.substring(Math.max(0, p - 120), p);
                    Pattern ptn = Pattern.compile("\\b(class|interface|enum|record)\\s+([A-Za-z0-9_]+)[^{]*$");
                    Matcher m = ptn.matcher(preceding);
                    if (m.find()) {
                        classStack.push(new ClassScope(m.group(2), braceDepth));
                    }
                } else if (ch == '}') {
                    if (!classStack.isEmpty() && classStack.peek().depth == braceDepth) {
                        classStack.pop();
                    }
                    braceDepth--;
                }
            }

            String commentContent = docMatcher.group(2);
            String followingCode = docMatcher.group(4).trim();

            // Check if this comment directly precedes a class, interface, enum, or record
            Pattern classPattern = Pattern.compile("^\\s*([a-zA-Z0-9_@\\[\\]\\s<>,]*?)\\b(class|interface|enum|record)\\s+([A-Za-z0-9_]+)(.*?)(?=\\{|;|$)", Pattern.DOTALL);
            Matcher classMatcher = classPattern.matcher(followingCode);

            if (classMatcher.find()) {
                String modifiers = classMatcher.group(1).trim();
                String entityType = classMatcher.group(2).trim();
                String className = classMatcher.group(3).trim();
                String details = classMatcher.group(4).trim();

                String fullSyntax = (modifiers + " " + entityType + " " + className + " " + details)
                        .replaceAll("\\s+", " ").trim();

                parseClassData(commentContent, entityType, className, fullSyntax);
            } else {
                // Determine active enclosing class
                String currentClass = classStack.isEmpty() ? defaultClassName : classStack.peek().name;

                // Check for method or constructor declaration
                Pattern methodPattern = Pattern.compile("^\\s*([a-zA-Z0-9_@\\[\\]\\s<>,]*?)\\b([A-Za-z0-9_]+)\\s*\\(([^)]*)\\)(.*?)(?=\\{|;|$)", Pattern.DOTALL);
                Matcher methodMatcher = methodPattern.matcher(followingCode);

                if (methodMatcher.find()) {
                    String prefix = methodMatcher.group(1).trim();
                    String methodName = methodMatcher.group(2).trim();
                    String paramList = methodMatcher.group(3).trim();
                    String afterParen = methodMatcher.group(4).trim();

                    boolean isConstructor = methodName.equals(currentClass);
                    String returnType = "";

                    if (isConstructor) {
                        returnType = "constructor";
                    } else {
                        // Extract return type by stripping annotations and modifiers from prefix
                        String cleanedPrefix = prefix.replaceAll("@\\w+(\\([^)]*\\))?\\s*", "")
                                .replaceAll("\\b(public|protected|private|static|final|synchronized|abstract|default|native|strictfp)\\b", "")
                                .replaceAll("<[^>]*>", "")
                                .trim();
                        returnType = cleanedPrefix.isEmpty() ? "void" : cleanedPrefix;
                    }

                    String fullSyntax = (prefix + " " + methodName + "(" + paramList + ") " + afterParen)
                            .replaceAll("\\s+", " ").trim();

                    parseMethodData(commentContent, currentClass, methodName, paramList, returnType, fullSyntax);
                }
            }

            // Advance lastPos to after comment delimiter
            lastPos = docMatcher.start(4);
        }
    }

    /**
     * Parses comment and metadata for class-level documentation.
     */
    private void parseClassData(String comment, String entityType, String className, String syntax) {
        String definition = extractDefinition(comment);
        String escapedSyntax = syntax.replaceAll("<", "&lt").replaceAll(">", "&gt");

        // Format summary table row for the class
        StringBuffer makeTable = new StringBuffer("<tr><td style='background-color: #dcdcdc; font-weight: bold;'>");
        makeTable.append(entityType + "</td><td style='background-color: #dcdcdc; font-weight: bold;'><a href='#ref" + refno + "'>");
        makeTable.append(className + "</a><p>" + definition + "</p></td></tr>");

        // Format detailed section card for the class
        StringBuffer makeSection = new StringBuffer("<section class='big' id='ref" + refno + "'><p style='background-color: grey; font-size: 25px;'>");
        makeSection.append(entityType + " " + className + "<br></p>");
        makeSection.append("<p><b>SYNTAX : </b><br>" + escapedSyntax + "<br></p>");
        if (!definition.isEmpty()) {
            makeSection.append("<p><b>DEFINITION : </b><br>" + definition + "<br></p>");
        }
        makeSection.append("</section><br><br>");

        addSectionToTheHtmlCode(makeSection);
        addTableToTheHtmlCode(makeTable);
        refno++;
    }

    /**
     * Parses comment and metadata for method/constructor documentation.
     */
    private void parseMethodData(String comment, String className, String methodName, String paramList, String returnType, String syntax) {
        // Reset working buffer
        for (int i = 0; i < sb4.length; i++) {
            sb4[i].replace(0, sb4[i].length(), "");
        }

        // Definition
        String definition = extractDefinition(comment);
        sb4[0].append(definition);

        // Params
        List<String> params = extractTags(comment, "@param");
        for (String p : params) {
            sb4[1].append(p.replaceAll("<", "&lt").replaceAll(">", "&gt") + "<br>");
        }

        // Returns
        List<String> returns = extractTags(comment, "@return");
        for (String r : returns) {
            sb4[2].append(r.replaceAll("<", "&lt").replaceAll(">", "&gt") + "<br>");
        }

        // Throws
        List<String> throwsList = extractTags(comment, "@throws");
        throwsList.addAll(extractTags(comment, "@exception"));
        for (String th : throwsList) {
            sb4[3].append(th.replaceAll("<", "&lt").replaceAll(">", "&gt") + "<br>");
        }

        // Syntax
        String escapedSyntax = syntax.replaceAll("<", "&lt").replaceAll(">", "&gt");
        sb4[4].append(escapedSyntax + "<br>");

        // Method display name & anchor label
        sb4[5].append(methodName + " (" + className + ")<br>");
        String displayName = className + "." + methodName + "(" + paramList + ")";
        sb4[6].append(displayName.replaceAll("<", "&lt").replaceAll(">", "&gt"));
        sb4[7].append(returnType.replaceAll("<", "&lt").replaceAll(">", "&gt"));

        makeSectionFromTheIndivisualData(sb4, refno);
        refno++;
    }

    /**
     * Extracts pure definition description from Javadoc comment lines.
     */
    private String extractDefinition(String comment) {
        String[] lines = comment.split("\n");
        StringBuilder def = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*")) {
                trimmed = trimmed.substring(1).trim();
            }
            if (!trimmed.startsWith("@") && !trimmed.isEmpty()) {
                def.append(trimmed).append(" ");
            }
        }
        return def.toString().trim().replaceAll("<", "&lt").replaceAll(">", "&gt");
    }

    /**
     * Extracts specific Javadoc tag occurrences from comment.
     */
    private List<String> extractTags(String comment, String tag) {
        List<String> list = new ArrayList<>();
        String[] lines = comment.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*")) {
                trimmed = trimmed.substring(1).trim();
            }
            if (trimmed.startsWith(tag)) {
                String val = trimmed.substring(tag.length()).trim();
                if (!val.isEmpty()) {
                    list.add(val);
                }
            }
        }
        return list;
    }

    /**
     * Generates HTML document with exact original CSS styles and layout.
     * @param s sections content buffer
     * @param s1 summary table rows buffer
     */
    public void CreateHtmlFileAndAddDataToIt(StringBuffer s, StringBuffer s1) {
        StringBuffer sb6 = new StringBuffer("<!DOCTYPE html>\n<html>\n<head><style>");
        sb6.append("body{\nbackground-color:bisque;\n}\n");
        sb6.append("a {\n text-decoration : none ; color:blue;}");
        sb6.append(".allmeth{\nbackground-color:yellow;\ncolor:black;\nfont-weight:bolder;width:max-content;padding-right:10px;padding-left:10px;}");
        sb6.append("td{\nheight: max-content;\nbackground-color:white;\nborder: 1px solid black;padding-right : 30px;}");
        sb6.append(".big{\nheight: max-content;\nwidth: 100%;\ndisplay:flex;\nflex-direction : column;\nalign-content : space-evenly;\nbackground-color : white;\npadding-top:0px;\n}");
        sb6.append("p{\nwidth : 100%;\ncolor:black;\ndisplay: flex;\nflex-direction: column;\njustify-content:center;\nmargin-top:5px;\nmargin-bottom:5px;}\n</style>\n");
        sb6.append("<title>DOCUMENT</title>\n</head>\n<body><div class='allmeth'><a href='#ref1' style='color: black;'>ALL METHODS</a></div>");
        sb6.append("<table class ='one'>");
        sb6.append(s1.toString() + "</table>");
        sb6.append(s.toString());
        sb6.append("</body>\n</html>");

        try {
            File f1 = new File("file.html");
            BufferedWriter bw = new BufferedWriter(new FileWriter(f1));
            bw.write(sb6.toString());
            bw.close();
        } catch (Exception e) {
            System.out.println("error writing html file: " + e.getMessage());
        }
    }

    /**
     * Builds summary table row and detailed specification section card.
     * @param sr buffer array of parsed attributes
     * @param no reference index number
     */
    public void makeSectionFromTheIndivisualData(StringBuffer[] sr, int no) {
        StringBuffer[] sb5 = sr;

        // Table row
        StringBuffer makeTable = new StringBuffer("<tr><td>");
        makeTable.append(sb5[7] + "</td><td><a href='#ref" + no + "'>");
        makeTable.append(sb5[6] + "</a><p>" + sb5[0] + "</p></td></tr>");

        // Section card
        StringBuffer makeSection = new StringBuffer("<section class='big' id='ref" + no + "'><p style='background-color: grey; font-size: 25px;'>" + sb5[5] + "</p>");
        makeSection.append("<p><b>SYNTAX : </b><br>" + sb5[4] + "</p>");
        if (!sb5[0].toString().isEmpty()) {
            makeSection.append("<p><b>DEFINITION : </b><br>" + sb5[0] + "</p>");
        }
        if (!sb5[2].toString().isEmpty()) {
            makeSection.append("<p><b>RETURNS : </b><br>" + sb5[2] + "</p>");
        }
        if (!sb5[1].toString().isEmpty()) {
            makeSection.append("<p><b>PARAM : </b><br>" + sb5[1] + "</p>");
        }
        if (!sb5[3].toString().isEmpty()) {
            makeSection.append("<p><b>THROWS : </b><br>" + sb5[3] + "</p>");
        }
        makeSection.append("</section><br><br>");

        addSectionToTheHtmlCode(makeSection);
        addTableToTheHtmlCode(makeTable);
    }

    public void addSectionToTheHtmlCode(StringBuffer sbr) {
        sb3.append(sbr.toString());
    }

    public void addTableToTheHtmlCode(StringBuffer sbr) {
        sb7.append(sbr.toString());
    }
}