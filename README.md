# SyncNest

## About
This is an application for setting up automatic backing up of a directory to a second directory.
Created with the assistance of Copilot and ChatGPT 4o.

## Build and Run

✅ **How to build and run `SyncNest` JavaFX project (Maven)**

**1️⃣ Prerequisites:**
- Install [Java 17+ JDK](https://adoptopenjdk.net/)
- Install [Apache Maven](https://maven.apache.org/install.html)

**2️⃣ Project structure:**  
```
SyncNest/
 ├─ pom.xml
 └─ src/main/java/
      ├─ SyncNestApp.java
      ├─ BackupRunner.java
```

**3️⃣ Build & run:**
```bash
# From the project root
mvn clean javafx:run
```

✅ This will download JavaFX + Jackson, compile, and run the JavaFX UI.

**4️⃣ To create an executable JAR:**
```bash
mvn clean package
```
This creates `target/syncnest-1.0.0.jar`

Run it with:
```bash
java -jar target/syncnest-1.0.0.jar
```

**5️⃣ Common tip:**  
JavaFX needs module info sometimes for native packaging. The `javafx-maven-plugin` handles this for dev runs.

**6️⃣ Optional native installer:**
For a true `.exe` or `.dmg`, use `jpackage`:
```bash
jpackage --input target --name SyncNest --main-jar syncnest-1.0.0.jar --main-class SyncNestApp
```
