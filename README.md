# Hecaton - Sistema di Calcolo Distribuito P2P


## 🎯 Vision del Progetto

**Hecaton** è un sistema di calcolo distribuito peer-to-peer che trasforma un gruppo eterogeneo di computer (PC, laptop, server, persino smartphone) in un **supercomputer virtuale**. Il sistema è progettato per essere completamente modulare: oggi può crackare password, domani calcolare simulazioni meteo o rendering video, senza cambiare l'infrastruttura di rete.

### Caratteristiche Principali

- **🔗 Distribuito e Decentralizzato**: Nessun single point of failure permanente
- **🔄 Auto-Organizzante**: Leader election automatica in caso di guasti
- **🧩 Modulare**: Cambia il tipo di calcolo senza modificare l'infrastruttura
- **💪 Fault Tolerant**: Gestisce automaticamente i nodi che cadono
- **🌐 Eterogeneo**: Funziona su Windows, Linux, macOS, Android (Termux)

---

## 🏗️ Architettura Software

Il sistema è strutturato in **tre livelli logici** distinti, ciascuno con responsabilità specifiche:

### 1️⃣ Livello Network & Control (Il Cervello)

Gestisce la vita del cluster senza conoscere i dettagli dei calcoli.

**Tecnologia**: Java RMI (Remote Method Invocation)

**Responsabilità**:
- **Discovery**: Permette ai nuovi nodi di unirsi al cluster
- **Heartbeat**: Monitoraggio continuo della salute dei nodi
- **Leader Election**: Algoritmo per eleggere automaticamente un coordinatore
- **Fault Detection**: Rileva nodi caduti e riassegna il lavoro

### 2️⃣ Livello Application Logic (Il Motore Estendibile)

Contiene l'intelligenza del lavoro da svolgere, progettato con il **Pattern Strategy**.

**Componenti**:
- **Interfaccia `Task`**: Contratto generico per qualsiasi tipo di calcolo
- **Implementazioni Concrete**: 
  - `PasswordCrackTask`: Brute-force di password MD5/SHA
  - *(Future)* `MonteCarloTask`, `VideoRenderTask`, ecc.

**Vantaggi**: Cambiare tipo di calcolo significa solo sostituire la classe Task, non riscrivere il sistema di rete.

### 3️⃣ Livello User Interface (Il Comando)

Interfaccia per l'interazione umana con il sistema.

**CLI (Command Line Interface)**:
```bash
# Avvio di un nodo
java -jar hecaton-node.jar --port 5001 --leader

# Sottomissione di un job
hecaton submit --task password-crack --hash a8f9c7e2b...

# Monitoring
hecaton status
```
Questo è totalmente teorico, ancora non so quale sarà effettivamente la CLI
---

## 🚀 Architettura Infrastrutturale

Il sistema evolve attraverso **tre stadi** di deployment progressivi:

### 📍 Stadio 1: Local Development

**Hardware**: 1 PC  
**Ambiente**: 3 terminali/shell  
**Rete**: `localhost` (127.0.0.1)  
**Differenziazione**: Porte diverse (5001, 5002, 5003)

**Scopo**: Sviluppo rapido, debugging, test degli algoritmi

---

### 🐳 Stadio 2: Containerized Simulation

**Hardware**: 1 PC  
**Ambiente**: Docker Compose  
**Rete**: Bridge network virtuale (172.18.0.x)  
**Differenziazione**: IP diversi, stessa porta

**Scopo**: Simulare una vera rete LAN, deployment pulito, isolamento dei processi


---

### 🌐 Stadio 3: Physical Hybrid Cluster

**Hardware**: PC + Laptop + Smartphone Android (Termux)  
**Ambiente**: OS nativi (Windows/Linux/Android)  
**Rete**: WiFi reale (Hotspot mobile)  
**Differenziazione**: IP reali assegnati dal router

**Scopo**: Dimostrazione tangibile di un vero sistema distribuito eterogeneo

---

## 🔄 Workflow Completo

### 1. Bootstrap (Genesi del Cluster)

```
1. Avvio Nodo A → Diventa Leader provvisorio
2. Avvio Nodo B con IP di A → B si registra presso A
3. A aggiorna lista membri: [A, B]
```

### 2. Job Submission

```
1. Utente: "Trova password per hash XYZ"
2. Leader esamina nodi disponibili
3. Leader suddivide il problema (keyspace splitting)
4. Leader invia oggetti Task serializzati via RMI
```

### 3. Execution

```
1. Worker riceve Task
2. Worker esegue task.execute() (calcolo intensivo)
3. Worker invia aggiornamenti periodici al Leader
```

### 4. Fault Tolerance

```
Scenario: Leader cade durante l'esecuzione

1. Worker rileva mancanza heartbeat
2. Worker inizializza Leader Election
3. Nuovo Leader eletto
4. Nuovo Leader riassegna task orfani
```

### 5. Completion

```
1. Worker trova soluzione
2. Worker chiama leaderService.solutionFound("password123")
3. Leader notifica tutti: STOP (job completato)
4. Leader mostra risultato all'utente
```

---

## 🛠️ Stack Tecnologico

- **Linguaggio**: Java 17+
- **Build Tool**: Maven / Gradle
- **Networking**: Java RMI
- **Serializzazione**: Java Serialization / Protocol Buffers
- **Containerizzazione**: Docker, Docker Compose
- **Testing**: JUnit 5, Mockito
- **Logging**: SLF4J + Logback

---

## 📚 Documentazione Aggiuntiva

Per la documentazione delle varie parti del progetto consultare la cartella `docs/`.

---

## 🤝 Contribuire

Questo è un progetto educativo/accademico. Suggerimenti e miglioramenti sono benvenuti!

---

## 📄 Licenza

... 

---

## 🎓 Contesto Accademico

Progetto sviluppato come dimostrazione di:
- Sistemi distribuiti P2P
- Algoritmi di consenso (Leader Election)
- Fault tolerance in sistemi decentralizzati
- Design pattern (Strategy, Factory)
- Containerizzazione e orchestrazione