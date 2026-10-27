# CSTree

This repository contains a Java implementation of a count-suffix-tree-based method for sequence classification.

## Requirements

- JDK 21
- Apache Maven 3.9 or later

## Build and run

Run the following commands from the repository root:

```bash
mvn clean package
java -cp target/classes org.example.Main
```

The example entry point builds a CSTree using the included Reuters data set.

## Repository structure

```text
src/main/java/org/example/    Core implementation and experiment code
src/dataset/                  Data sets
pom.xml                       Maven configuration
```

The code and documentation may change as the project develops.
