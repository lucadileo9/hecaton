# Java Networking & Concurrency - Guida Concettuale

**Documento di riferimento per i concetti Java avanzati utilizzati in Hecaton**

Questo documento spiega in dettaglio i concetti di programmazione concorrente e networking utilizzati nel sistema di discovery UDP. È una guida di approfondimento per comprendere le tecnologie alla base dell'implementazione.

---

## Indice

1. [Thread - Esecuzione Parallela](#1-thread---esecuzione-parallela)
2. [Socket UDP - Comunicazione di Rete](#2-socket-udp---comunicazione-di-rete)
3. [ExecutorService - Gestione Thread](#3-executorservice---gestione-thread)
4. [Volatile - Sincronizzazione Variabili](#4-volatile---sincronizzazione-variabili)
5. [Daemon Thread - Thread Non Bloccanti](#5-daemon-thread---thread-non-bloccanti)
6. [Serializzazione - Oggetti su Rete](#6-serializzazione---oggetti-su-rete)
7. [Pattern e Best Practice](#7-pattern-e-best-practice)

---

## 1. Thread - Esecuzione Parallela

### Cos'è un Thread

Un **thread** è un flusso di esecuzione indipendente all'interno di un processo. Permette di eseguire più operazioni contemporaneamente.

**Analogia**: Immagina una cucina di ristorante:
- **Processo** = Il ristorante intero
- **Main Thread** = Cuoco principale
- **Worker Thread** = Assistente cuoco

Il cuoco principale può cucinare mentre l'assistente lava i piatti - lavorano **in parallelo**.

### Perché Servono i Thread in Hecaton

Perché senza thread il broadcasting UDP bloccherebbe il main thread del Leader, impedendo di accettare nuovi Worker o rispondere a heartbeat durante l'invio dei messaggi.

### Timeline di Esecuzione

```
Tempo →
═══════════════════════════════════════════════════════════

Main Thread (Leader):
    ├─ startAsLeader()
    ├─ Crea broadcaster thread
    ├─ broadcasterThread.start()
    ├─ Ritorna
    ├─ Accetta nuovi Worker (registerNode)
    ├─ Risponde a heartbeat ping
    ├─ Gestisce task
    └─ ...continua normalmente...

Broadcaster Thread:
                      ├─ Thread parte
                      ├─ Broadcast UDP #1
                      ├─ sleep(5000)
                      ├─ Broadcast UDP #2
                      ├─ sleep(5000)
                      ├─ Broadcast UDP #3
                      └─ ...continua ogni 5s...
```

**Vantaggi**:
- ✅ Main thread libero di gestire il cluster
- ✅ Broadcasting continuo in background
- ✅ Nessun blocco delle operazioni critiche

### Creazione Thread in Java

**Modalità 1: Estendere Thread**
**Modalità 2: Implementare Runnable**
**Modalità 3: Con ExecutorService** - vedi [sezione 3](#3-executorservice---gestione-thread)


### Thread Safety - Il Problema della Concorrenza

**Problema**: Due thread modificano la stessa variabile contemporaneamente.

```java
private int counter = 0;  // Variabile condivisa
// Thread 1
counter++;  // Legge 0, scrive 1
// Thread 2 (contemporaneamente)
counter++;  // Legge 0, scrive 1
// Risultato: counter = 1 (dovrebbe essere 2!)
```

**Soluzione 1: Synchronized**
```java
private int counter = 0;

public synchronized void increment() {
    counter++;  // Solo un thread alla volta
}
```

**Soluzione 2: Atomic**
```java
private AtomicInteger counter = new AtomicInteger(0);

public void increment() {
    counter.incrementAndGet();  // Operazione atomica
}
```

**Soluzione 3: Volatile** - vedi [sezione 4](#4-volatile---sincronizzazione-variabili)

---

## 2. Socket UDP - Comunicazione di Rete

### TCP vs UDP

| Caratteristica | TCP (Transmission Control Protocol) | UDP (User Datagram Protocol) |
|---------------|--------------------------------------|------------------------------|
| **Analogia** | Telefonata | Walkie-talkie |
| **Connessione** | Orientato alla connessione (handshake) | Senza connessione |
| **Affidabilità** | Garantita (ritrasmissione pacchetti persi) | Non garantita |
| **Ordine** | Messaggi arrivano in ordine | Può arrivare disordinato |
| **Velocità** | Lento (overhead protocollo) | Veloce (fire-and-forget) |
| **Overhead** | Header 20+ bytes | Header 8 bytes |
| **Uso tipico** | HTTP, FTP, RMI | DNS, streaming video, discovery |

### Perché UDP per Discovery?

**Vantaggi in discovery:**
- ✅ **Velocità**: Nessun handshake, invio immediato
- ✅ **Broadcast**: Può inviare a tutti senza sapere chi c'è
- ✅ **Semplicità**: Non serve gestire connessioni
- ✅ **Idempotenza**: Se un pacchetto si perde, ne arriva un altro tra 5s

**Svantaggi accettabili:**
- ⚠️ **Perdita pacchetti**: OK, ripetiamo broadcast ogni 5s
- ⚠️ **Duplicazione**: OK, ignoriamo duplicati
- ⚠️ **Disordine**: Non rilevante (ogni messaggio è indipendente)

### Anatomia di un Pacchetto UDP

```
┌─────────────────────────────────────────────────────┐
│ Header UDP (8 bytes)                                │
├───────────────┬─────────────────────────────────────┤
│ Source Port   │ Destination Port                    │
│ 54321         │ 6789                                │
├───────────────┼─────────────────────────────────────┤
│ Length        │ Checksum                            │
│ 195 bytes     │ 0xA3F2                              │
├───────────────┴─────────────────────────────────────┤
│ Payload (187 bytes)                                 │
│ [Serialized DiscoveryMessage object]                │
│ 0xAC 0xED 0x00 0x05 0x73 0x72 0x00 0x2C ...        │
└─────────────────────────────────────────────────────┘
```

**In Hecaton usiamo Broadcast** perché:
- ✅ Semplice (no gestione gruppi multicast)
- ✅ Funziona su tutte le reti LAN
- ✅ Worker non devono registrarsi a gruppi

### DatagramSocket - API Java

**Sender (Broadcaster)**
```java
// 1. Creazione socket
DatagramSocket socket = new DatagramSocket();
// Il SO assegna una porta random (es. 54321)

// 2. Abilitazione broadcast
socket.setBroadcast(true);
// Senza questo, invio a 255.255.255.255 fallisce
// Protezione: "Sei sicuro di voler spammare la LAN?"

// 3. Preparazione dati
byte[] data = "HELLO".getBytes();  // Payload
InetAddress broadcast = InetAddress.getByName("255.255.255.255");

// 4. Creazione pacchetto
DatagramPacket packet = new DatagramPacket(
    data,           // Byte da inviare
    data.length,    // Lunghezza
    broadcast,      // Indirizzo destinazione
    6789            // Porta destinazione
);

// 5. Invio
socket.send(packet);

// 6. Chiusura (quando finito)
socket.close();
```

**Receiver (Listener)**
```java
// 1. Creazione socket SULLA PORTA DI RICEZIONE
DatagramSocket socket = new DatagramSocket(6789);
// Questa porta DEVE corrispondere a quella del sender

// 2. Timeout (opzionale ma raccomandato)
socket.setSoTimeout(5000);  // Timeout 5 secondi
// Senza timeout, receive() blocca per sempre!

// 3. Buffer di ricezione
byte[] buffer = new byte[4096];  // 4KB
DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

// 4. Ricezione (BLOCCA QUI!)
try {
    socket.receive(packet);  // ⚠️ BLOCCA il thread
    
    // Pacchetto ricevuto
    byte[] receivedData = packet.getData();
    int length = packet.getLength();
    String message = new String(receivedData, 0, length);
    
    System.out.println("Ricevuto: " + message);
    
} catch (SocketTimeoutException e) {
    System.out.println("Nessun pacchetto ricevuto in 5s");
}

// 5. Chiusura
socket.close();
```
L'applicazione reale può essere vista nella classe `UdpDiscoveryService` in Hecaton.

### Rete LAN - Cosa Succede Fisicamente

```
┌──────────────────────────────────────────────────────┐
│ Rete LAN (192.168.1.0/24)                            │
│                                                      │
│  Leader (192.168.1.10)                               │
│  └─ socket.send(packet)                              │
│      │                                               │
│      ▼                                               │
│  [Scheda di rete] → [Cavo Ethernet/Wi-Fi]           │
│      │                                               │
│      ▼                                               │
│  [Switch/Router]                                     │
│      │                                               │
│      ├─────────────────┬─────────────────┐          │
│      ▼                 ▼                 ▼          │
│  Worker 1          Worker 2         Laptop          │
│  (.20:6789)        (.30:6789)       (.50:6789)      │
│      │                 │                 │          │
│      └─ Riceve        └─ Riceve         └─ Ignora  │
│         (porta 6789)    (porta 6789)     (non ascolta) │
└──────────────────────────────────────────────────────┘
```

**Note importanti:**
- Solo i dispositivi con socket aperto su porta 6789 ricevono effettivamente
- Gli altri ricevono il pacchetto a livello hardware ma il SO lo scarta
- Il broadcast non attraversa router (limitato alla subnet locale)

---

## 3. ExecutorService - Gestione Thread

### Perché Non Usare `new Thread()` Direttamente

**Problemi con gestione manuale:**
```java
// Approccio SCONSIGLIATO
Thread t1 = new Thread(() -> task1());
Thread t2 = new Thread(() -> task2());
Thread t3 = new Thread(() -> task3());
t1.start();
t2.start();
t3.start();

// PROBLEMI:
// 1. Creazione costosa (ogni thread = ~1MB stack memory)
// 2. Difficile shutdown pulito
// 3. Nessun riutilizzo thread
// 4. Difficile limitare numero thread
// 5. Gestione errori complessa
```

**Soluzione: ExecutorService**
```java
// Approccio RACCOMANDATO
ExecutorService executor = Executors.newFixedThreadPool(3);
executor.submit(() -> task1());
executor.submit(() -> task2());
executor.submit(() -> task3());

// Thread vengono riutilizzati automaticamente
// Shutdown pulito e gestito
executor.shutdown();
```

### Tipi di ExecutorService

**1. SingleThreadExecutor (usato in Hecaton)**
```java
ExecutorService executor = Executors.newSingleThreadExecutor();
// - 1 solo thread worker
// - Tasks eseguiti sequenzialmente
// - Ideale per: broadcasting periodico
```

**2. FixedThreadPool**
```java
ExecutorService executor = Executors.newFixedThreadPool(10);
// - Pool di 10 thread worker
// - Tasks eseguiti in parallelo
// - Ideale per: elaborazione batch, web server
```

**3. CachedThreadPool**
```java
ExecutorService executor = Executors.newCachedThreadPool();
// - Crea thread on-demand
// - Riutilizza thread inattivi
// - Ideale per: task brevi e numerosi
```

**4. ScheduledThreadPool**
```java
ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
// - Esecuzione programmata (come cron)
// - Ideale per: task periodici, timeout
```

### Ciclo di Vita di ExecutorService

```
┌─────────────────────────────────────────────────────┐
│ Lifecycle di ExecutorService                        │
└─────────────────────────────────────────────────────┘

1. CREAZIONE
   executor = Executors.newSingleThreadExecutor()
   ↓
   [RUNNING] - Accetta nuovi task

2. SUBMIT TASK
   executor.submit(() -> broadcast())
   ↓
   [RUNNING] - Esegue task in background

3. SHUTDOWN (graceful)
   executor.shutdown()
   ↓
   [SHUTDOWN] - Non accetta nuovi task
                Aspetta completamento task in corso
   ↓
   [TERMINATED] - Tutti i task completati

4. SHUTDOWN NOW (forzato)
   executor.shutdownNow()
   ↓
   [STOP] - Interrompe task in corso (interrupt)
   ↓
   [TERMINATED] - Forza terminazione
```

### Submit vs Execute

```java
// execute(): void, non ritorna nulla
executor.execute(() -> {
    System.out.println("Hello");
});

// submit(): ritorna Future<T> per tracking
Future<String> future = executor.submit(() -> {
    return "Result";
});

// Aspetta risultato (blocca)
String result = future.get();

// O verifica se completato
if (future.isDone()) {
    String result = future.get();
}
```

**In Hecaton usiamo `submit()`** ma ignoriamo il `Future` (fire-and-forget).

---

## 4. Volatile - Sincronizzazione Variabili

### Il Problema: CPU Cache

**Architettura moderna:**
```
┌─────────────────────────────────────────────┐
│ CPU Core 1          CPU Core 2              │
│  ┌─────────┐         ┌─────────┐            │
│  │ Cache   │         │ Cache   │            │
│  │ L1/L2   │         │ L1/L2   │            │
│  └────┬────┘         └────┬────┘            │
│       │                   │                 │
│       └─────────┬─────────┘                 │
│                 │                           │
│          ┌──────▼──────┐                    │
│          │ RAM Memoria │                    │
│          │ Principale  │                    │
│          └─────────────┘                    │
└─────────────────────────────────────────────┘
```
**Problema**: Un thread aggiorna una variabile, ma l'altro thread legge una versione "vecchia" dalla sua cache.
**Soluzione**: Usare `volatile` per forzare la visibilità tra thread.
Ossia volatile assicura che:
- Le scritture su una variabile volatile sono immediatamente visibili a tutti i thread
- Le letture di una variabile volatile leggono sempre l'ultimo valore scritto

### Garanzie di Volatile

**1. Visibility (Visibilità)**
```java
private volatile boolean flag = false;

// Thread A
flag = true;  // ✅ Visibile IMMEDIATAMENTE a Thread B

// Thread B
if (flag) {   // ✅ Vede il valore aggiornato
    // ...
}
```

**2. Happens-Before**
```java
private int data = 0;
private volatile boolean ready = false;

// Thread A
data = 42;        // 1. Scrive data
ready = true;     // 2. Scrive volatile (happens-before garantisce che 1 è visibile)

// Thread B
if (ready) {      // 3. Legge volatile
    print(data);  // 4. ✅ Vede data=42 (grazie a happens-before)
}
```

**Happens-Before Rule**: Tutte le scritture prima di un volatile write sono visibili prima di un volatile read.
---

## 5. Daemon Thread - Thread Non Bloccanti

### Thread Normale vs Daemon

**Thread Normale (User Thread)**
```java
Thread t = new Thread(() -> {
    while (true) {
        doWork();
        Thread.sleep(1000);
    }
});
t.start();

// Problema: Il JVM non termina finché questo thread è vivo
// Anche se main() finisce, il processo continua a girare
```

**Daemon Thread**
```java
Thread t = new Thread(() -> {
    while (true) {
        doWork();
        Thread.sleep(1000);
    }
});
t.setDaemon(true);  // ← Marca come daemon
t.start();

// Il JVM termina anche se questo thread è ancora attivo
// Quando main() finisce, il daemon thread viene UCCISO
```

### Regola del JVM

**Il JVM termina quando:**
- Tutti i **user thread** sono terminati
- Ignorando i **daemon thread**

```
┌─────────────────────────────────────────────────┐
│ Processo Java                                   │
│                                                 │
│ Main Thread (user)                              │
│     │                                           │
│     ├─ Worker Thread (user)                     │
│     │                                           │
│     └─ Broadcaster Thread (daemon)              │
│                                                 │
│ Quando terminano:                               │
│ - Main Thread → OK, ma JVM aspetta Worker       │
│ - Worker Thread → JVM termina (ignora daemon)   │
│ - Daemon automaticamente ucciso                 │
└─────────────────────────────────────────────────┘
```

### Quando Usare Daemon Thread

✅ **Task di background non critici**
- Logging asincrono
- Garbage collection (JVM interno)
- Monitoring / heartbeat
- **UDP broadcasting** (Hecaton)

❌ **Task critici che DEVONO completare**
- Scrittura su database
- Invio email
- Salvataggio file

---

## 6. Serializzazione - Oggetti su Rete

### Il Problema: Trasmettere Oggetti Java

**Rete trasmette solo byte**, non oggetti Java.

### Serializzazione Java

**Requisito**: La classe deve implementare `Serializable`
```java
public class DiscoveryMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final MessageType messageType;
    private final String nodeId;
    private final int port;
    // ...
}
```

**serialVersionUID**: Numero di versione del formato. Se cambi struttura classe (aggiungi/rimuovi field), incrementa questo numero.

### Processo di Serializzazione

**Sender: Oggetto → Byte**
```java
DiscoveryMessage msg = new DiscoveryMessage(
    LEADER_ANNOUNCEMENT,
    "node-localhost-5001-...",
    "localhost",
    5001
);

// Serializzazione
ByteArrayOutputStream baos = new ByteArrayOutputStream();
ObjectOutputStream oos = new ObjectOutputStream(baos);
oos.writeObject(msg);  // Scrive oggetto nello stream
byte[] data = baos.toByteArray();

// data = [172, 237, 0, 5, 115, 114, ...] (187 bytes)
```

**Receiver: Byte → Oggetto**
```java
byte[] receivedData = packet.getData();  // Byte dal socket UDP

// Deserializzazione
ByteArrayInputStream bais = new ByteArrayInputStream(receivedData);
ObjectInputStream ois = new ObjectInputStream(bais);
DiscoveryMessage msg = (DiscoveryMessage) ois.readObject();

// msg è ora un oggetto Java completo
System.out.println(msg.getNodeId());  // "node-localhost-5001-..."
```
### Campo transient

**Non tutti i field vanno serializzati:**
```java
public class NodeImpl implements Serializable {
    private String nodeId;              // ✅ Serializzato
    private int port;                   // ✅ Serializzato
    
    private transient Logger log;       // ❌ NON serializzato
    private transient DatagramSocket socket;  // ❌ NON serializzato
}
```

**transient**: Dice alla serializzazione di ignorare il field (utile per risorse non serializzabili come socket, thread, logger).

---

## Riferimenti e Approfondimenti

### Documentazione Ufficiale Java
- [Oracle: Concurrency Tutorial](https://docs.oracle.com/javase/tutorial/essential/concurrency/)
- [DatagramSocket API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/DatagramSocket.html)
- [ExecutorService API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)
- [Object Serialization](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/index.html)
