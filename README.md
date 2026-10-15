# Java Documentation Generator

A zero-dependency Java documentation engine that parses Javadoc comments, class structures, interfaces, enums, constructors, and method signatures to generate a standalone, indexed single-page HTML API specification (`file.html`).

Built using pure Java standard library components (`java.util.regex`, `java.io`), this tool extracts structured metadata directly from source code and compiles it into an interactive reference table and detailed specification sheets.

---

## Architecture & Data Pipeline

The generator processes Java source files through a 5-stage pipeline:

```
+-------------------------------------------------------------+
| 1. Source Ingestion & Discovery                             |
|    Single file or directory scan (*.java)                   |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
| 2. Lexical Scope & Brace Tracking                           |
|    Tracks { ... } depth and manages ClassScope stack        |
|    (Outer class -> Inner class -> Enum -> Return to Outer)  |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
| 3. Javadoc Block & Token Extraction                         |
|    Matches /** ... */ blocks and following declarations     |
|    Distinguishes: Classes, Interfaces, Enums,               |
|                   Constructors, and Methods                 |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
| 4. Tag & Signature Parsing                                  |
|    Extracts: Definition, @param, @return, @throws           |
|    Isolates return type, parameters, and modifiers          |
|    Sanitizes HTML entities (< to &lt, > to &gt)             |
+-------------------------------------------------------------+
                              |
                              v
+-------------------------------------------------------------+
| 5. HTML Document Generation                                 |
|    Assembles Quick Nav, Summary Table, and Section Cards    |
|    Writes output to file.html                               |
+-------------------------------------------------------------+
```

---

## Key Features

- **Multi-Class & Multi-File Support**: Documents entire projects or multi-class source files, including top-level classes, non-public helper classes, interfaces, abstract classes, static inner classes, and enums.
- **Lexical Scope Resolution**: Uses a brace-depth stack to track enclosing classes across nested structures. Methods declared after inner classes or enums correctly map back to their enclosing class.
- **Entity Classification**: Automatically differentiates between classes, interfaces, enums, constructors, and methods.
- **Full Javadoc Tag Coverage**: Parses method descriptions alongside `@param`, `@return`, and `@throws` / `@exception` tags.
- **HTML Sanitization**: Escapes Java generics (`<T>`, `Map<K, V>`) into HTML entities (`&lt`, `&gt`) to avoid browser rendering collisions.
- **Quick-Jump Indexing**: Summary table links directly to individual method and class specification cards via `#refN` anchor references.
- **Zero Third-Party Dependencies**: Runs directly on any standard Java 8+ Runtime Environment without external libraries, build plugins, or network access.

---

## Repository Structure

```
.
├── DocumentionCreation.java         # Main documentation generator source
├── Demo.java                        # Comprehensive multi-class test file (350+ lines)
├── file.html                        # Generated HTML documentation output
└── README.md                        # Documentation and architecture guide
```

---

## Quickstart

### Prerequisites

- Java Development Kit (JDK 8 or higher)
- Terminal / Command Prompt / PowerShell

### Compilation

Compile the generator using standard `javac`:

```bash
javac DocumentionCreation.java
```

### Execution

The generator supports both command-line arguments and interactive prompts.

#### Option A: Pass target as command-line argument (Recommended)

```bash
# Document a specific Java file
java DocumentionCreation Demo.java

# Or document an entire directory of Java files
java DocumentionCreation .
```

#### Option B: Interactive console prompt

Run the generator without arguments:

```bash
java DocumentionCreation
```

When prompted:
```
Enter the name of the file or directory:
Demo.java
```

The generator checks the following locations in order:
1. Exact relative/absolute path provided (e.g., `Demo.java` in current directory)
2. `C:/Users/Admin/Desktop/<input>`
3. Current working directory fallback (`System.getProperty("user.dir")`)

Upon completion, `file.html` is written to the root directory.

---

## How It Works (Internals)

### 1. Scope Tracking (`ClassScope`)

To handle nested classes and enums within a single file without misattributing methods, `DocumentionCreation` maintains a stack of active scopes:

```java
static class ClassScope {
    String name;
    int depth;
    ClassScope(String name, int depth) {
        this.name = name;
        this.depth = depth;
    }
}
```

As the file is traversed:
- Opening brace `{` after a `class`, `interface`, or `enum` pushes a new `ClassScope` with the current brace depth.
- Closing brace `}` matching the top scope's depth pops the scope from the stack.
- Any method or constructor encountered automatically binds to `classStack.peek().name`.

### 2. Regex Engine Patterns

| Component | Pattern / Strategy | Description |
| :--- | :--- | :--- |
| **Doc Block** | `(/\*\*)(.*?)(\*/)(.*?)(?=(/\*\*)\|\$)` | Isolates each Javadoc comment and the declaration immediately following it. |
| **Class Declaration** | `^\s*([a-zA-Z0-9_@\[\]\s<>,]*?)\b(class\|interface\|enum\|record)\s+([A-Za-z0-9_]+)(.*?)(?=\{;\|$)` | Captures modifiers, entity type, name, and generic/inheritance bounds. |
| **Method Declaration** | `^\s*([a-zA-Z0-9_@\[\]\s<>,]*?)\b([A-Za-z0-9_]+)\s*\(([^)]*)\)(.*?)(?=\{;\|$)` | Extracts modifiers, return type, name, parameters, and throws clause. |
| **Return Type Filter** | Strips annotations (`@\w+`), modifiers (`public`, `static`, etc.), and type parameters (`<...>`) | Isolates the clean return type (e.g., `String`, `List`, `int`). |
| **Tag Extraction** | Lines prefixed with `@param`, `@return`, `@throws`, `@exception` | Separates tag values from main narrative descriptions. |

---

## Demo Input File: `Demo.java`

The included `Demo.java` is a comprehensive test file designed to exercise all aspects of the generator:

- **Top-Level Class**: `public class Demo` (main application runner and diagnostics)
- **Generic Interface**: `interface DataProcessor<T, R>` (abstract methods, generics, multi-throws)
- **Abstract Base Class**: `abstract class AbstractTaskRunner<T>` (lifecycle states, protected fields)
- **Complex Service Class**: `class ServicePipeline` (implements interface, extends abstract class, synchronized methods, method overrides)
- **Static Inner Class**: `public static class PipelineConfig` (configuration object with constructor, getters)
- **Nested Enum**: `public enum PayloadState` (enum constants and static lookup methods)
- **Independent Helper Class**: `class MetricsCollector` (averages, maps, latency logging)

### Perspectives Covered in Demo

- **Constructors vs Methods**: Distinguishes constructors from methods using class context.
- **Modifier Permutations**: `public`, `protected`, `synchronized`, `static`, `abstract`, `@Override`.
- **Parameter Types**: Zero params, single primitive, multi-parameter, collections (`List<String>`), maps (`Map<String, String>`), arrays (`byte[]`).
- **Return Types**: `void`, primitives (`boolean`, `int`, `long`, `double`), objects (`String`), generics (`List`, `Map`), and arrays (`byte[]`).
- **Javadoc Tags**:
  - `@param` with variable name and explanation
  - `@return` with result details
  - `@throws` and `@exception` for error conditions
  - Multi-line descriptive definitions before tags

---

## Output Layout & Visual Hierarchy

The generated `file.html` uses an integrated CSS stylesheet providing:

- **Canvas**: Clean `bisque` background with high-contrast content containers.
- **Header Button**: `.allmeth` yellow navigation badge linking directly to `#ref1`.
- **Summary Table (`<table class='one'>`)**:
  - Gray header rows for classes, interfaces, and enums (`#dcdcdc`).
  - White rows for constructors and methods showing Return Type, Qualified Name, and Brief Description.
  - Clickable anchor links jumping directly to the corresponding detail card.
- **Specification Sections (`<section class='big'>`)**:
  - Gray banner header displaying the method or class title.
  - **SYNTAX**: Monospaced declaration with HTML-escaped generics.
  - **DEFINITION**: Full narrative documentation extracted from the comment.
  - **RETURNS**: Expected output specifications.
  - **PARAM**: Individual parameter descriptions separated by line breaks.
  - **THROWS**: List of possible exceptions and fault conditions.

---

## Running on Custom Projects

To document any other Java project or directory:

```bash
# 1. Compile the generator
javac DocumentionCreation.java

# 2. Run pointing to your target folder
java DocumentionCreation /path/to/your/java/project

# 3. Open the output
start file.html     # Windows
open file.html      # macOS
xdg-open file.html  # Linux
```
