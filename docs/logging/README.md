# Hecaton Logging System

This project uses **Logback** (via SLF4J) to manage application logging. The configuration is designed to separate operational noise from debugging details, ensuring a clean console for development while preserving granular data for post-mortem analysis.

## 📋 Logging Strategy

We utilize a **Multi-Appender Strategy** to route messages based on their importance and intended audience:

| Destination | Filename | Level | Purpose |
| --- | --- | --- | --- |
| **Console** | *stdout* | `INFO` (Default) | Real-time monitoring. Human-readable, colorized, stripped of clutter. |
| **Operational Log** | `logs/hecaton.log` | `INFO`+ | Permanent history of important events. Clean and concise. |
| **Debug Log** | `logs/hecaton-debug.log` | `DEBUG`+ | **Full Verbosity.** Contains internal state, node handshakes, and granular trace data. |

---

## 🖥️ Console Output

The console output is optimized for **readability**.

* **Format:** `%gray(%d{HH:mm:ss}) %highlight(%-5level) %cyan(%-30.30logger{29}) - %msg%n`

Features:
* **Color-coded levels** (INFO is blue, WARN is red, etc.).
* **No Date:** Only time is shown to save horizontal space.
* **No Thread Name:** Thread names are hidden to reduce clutter during development.
* **Smart Truncation:** Package names are abbreviated (e.g., `c.h.n.NodeImpl`) to keep columns aligned.



**Example:**

```text
21:30:01 INFO  c.h.discovery.Service          - Discovery service started
21:30:02 WARN  c.h.rmi.Connection             - Retrying connection to node-5...

```

---

## 📂 File Outputs

Log files are located in the `logs/` directory. They differ from the console in that they include **full timestamps** and **thread names** for precise debugging in multi-threaded environments (RMI, Elections).

### 1. `logs/hecaton.log` (Operational)

* **Filter:** `INFO`, `WARN`, `ERROR` only.
* **Format:** Includes full date and thread name.
* **Padding:** Uses smart padding to keep logs visually aligned.
* **Thread Handling:** `[%-56thread]` - Reserves 56 spaces. If the thread name is longer, it expands without truncation (preserving the full name), but this should be the longest thread name in practice.

### 2. `logs/hecaton-debug.log` (Full Trace)

* **Filter:** **None**. Captures `DEBUG` logs from `com.hecaton` packages.
* **Use Case:** If the application crashes or behaves unexpectedly, check this file first. It contains the step-by-step execution flow.

**Example (File Content):**

```text
2026-01-11 21:30:00.005 [main                     ] DEBUG com.hecaton.node.NodeImpl                - Initializing variables...
2026-01-11 21:30:01.123 [main                     ] INFO  com.hecaton.discovery.Service            - Discovery service started
2026-01-11 21:30:01.450 [RMI TCP Connection(2)-... ] DEBUG com.hecaton.rmi.Server                   - Incoming handshake from 192.168.1.5

```

---

## ⚙️ Configuration & Dynamic Debugging

The logging configuration is defined in `src/main/resources/logback.xml`.

### Changing Console Verbosity

By default, the console shows only `INFO`. You can enable `DEBUG` output in the console **without changing the XML file** by passing a system property at runtime.

**Standard Run (Default):**

```bash
java -jar hecaton.jar

```

**Debug Run (Verbose Console):**

```bash
java -Dconsole.level=DEBUG -jar hecaton.jar

```

### Changing Internal Log Levels

The project default for the `com.hecaton` package is set to `DEBUG` internally.

* This ensures the `hecaton-debug.log` always receives detailed data.
* The `CONSOLE` and `hecaton.log` filter these out automatically via `ThresholdFilter`.

To change specific package levels permanently, edit `logback.xml`:

```xml
<logger name="com.hecaton.rmi" level="INFO"/>

```

---

## 🧑‍💻 Usage for Developers

### 1. Standard Logging

Use SLF4J in your classes. Do not use `System.out.println`.

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeImpl {
    private static final Logger logger = LoggerFactory.getLogger(NodeImpl.class);

    public void start() {
        // Appears in debug log only
        logger.debug("Binding to port {}", 5000); 
        
        // Appears in console, info log, and debug log
        logger.info("Node started successfully");
        
        // Appears in all logs (colored red in console)
        logger.error("Failed to connect to peer", new RuntimeException("Timeout"));
    }
}

```

### 2. Using MDC (Mapped Diagnostic Context)

Since Hecaton is a distributed system, it is recommended to tag threads with the Node ID context if applicable.

```java
import org.slf4j.MDC;

// At the start of the node/thread
MDC.put("nodeId", "node-5001");

// ... logging operations ...

// Clean up
MDC.clear();

```

*(Note: To see the `nodeId` in the logs, the pattern in `logback.xml` would need to be updated to include `%X{nodeId}`).*