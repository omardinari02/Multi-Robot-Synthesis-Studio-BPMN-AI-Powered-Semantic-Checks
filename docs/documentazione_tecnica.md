# Sintesi di Comportamenti Collaborativi per Sistemi Multi-Robot

## Documentazione Tecnica e Scientifica del Framework Software MRS Synthesis Engine

**Università degli Studi dell'Aquila**  
**Dipartimento di Ingegneria e Scienze dell'Informazione e Matematica (DISIM)**  
*Corso di Laurea Magistrale in Ingegneria Informatica — Curriculum Advanced Software Engineering (ASE)*  
*Documentazione tecnica e computazionale basata sul codice sorgente del progetto e fidelizzata alla Tesi di Laurea Magistrale di **Giovanni Spaziani** (Relatore: Prof. Massimo Tivoli).*

---

## Indice Generale

1. [Introduzione, Contesto e Motivazioni](#capitolo-1--introduzione-contesto-e-motivazioni)
   - 1.1 [Introduzione ai Sistemi Multi-Robot (MRS)](#11-introduzione-ai-sistemi-multi-robot-mrs)
   - 1.2 [Architetture di Coordinamento a Confronto](#12-architetture-di-coordinamento-a-confronto)
   - 1.3 [Il Problema Fondamentale del Coordinamento Distribuito](#13-il-problema-fondamentale-del-coordinamento-distribuito)
   - 1.4 [Scopo della Tesi e Posizionamento del Framework](#14-scopo-della-tesi-e-posizionamento-del-framework)
   - 1.5 [Introduzione al Caso di Studio: Precision Agriculture](#15-introduzione-al-caso-di-studio-precision-agriculture)
2. [Background Teorico e Fondamenti Formali](#capitolo-2--background-teorico-e-fondamenti-formali)
   - 2.1 [Coreografie di Servizi: Definizione Formale e Semantica](#21-coreografie-di-servizi-definizione-formale-e-semantica)
   - 2.2 [Specifica di Coreografie mediante BPMN 2.0](#22-specifica-di-coreografie-mediante-bpmn-20)
   - 2.3 [Il Problema della Realizability nei Sistemi Multi-Robot](#23-il-problema-della-realizability-nei-sistemi-multi-robot)
   - 2.4 [Strategie di Risoluzione della Realizability e Coordination Delegates](#24-strategie-di-risoluzione-della-realizability-e-coordination-delegates)
   - 2.5 [Le Quattro Fasi dell'Approccio Formale](#25-le-quattro-fasi-dellapproccio-formale)
3. [Metodologia di Sintesi Automatica della Collaborazione](#capitolo-3--metodologia-di-sintesi-automatica-della-collaborazione)
   - 3.1 [I Principi Fondamentali del Metodo Automatico (Capitolo 5 della Tesi)](#31-i-principi-fondamentali-del-metodo-automatico-capitolo-5-della-tesi)
   - 3.2 [Le Sei Operazioni Fondamentali di Sintesi](#32-le-sei-operazioni-fondamentali-di-sintesi)
   - 3.3 [Le Tre Fasi Computazionali Principali](#33-le-tre-fasi-computazionali-principali)
   - 3.4 [Corrispondenza tra Metodologia Formale e Pipeline Implementativo](#34-corrispondenza-tra-metodologia-formale-e-pipeline-implementativo)
   - 3.5 [Distinzione tra Specifiche della Tesi ed Estensioni Software](#35-distinzione-tra-specifiche-della-tesi-ed-estensioni-software)
4. [Algoritmi e Meccanismi di Verifica del Framework](#capitolo-4--algoritmi-e-meccanismi-di-verifica-del-framework)
   - 4.1 [Verifica di Compatibilità Sintattica (STRONG, WEAK e Strutturale)](#41-verifica-di-compatibilit-sintattica-strong-weak-e-strutturale)
   - 4.2 [Verifica di Compatibilità Semantica, Ontologie e Subsumption](#42-verifica-di-compatibilit-semantica-ontologie-e-subsumption)
   - 4.3 [Ragionamento Semantico Ibrido e Integrazione LLM](#43-ragionamento-semantico-ibrido-e-integrazione-llm)
   - 4.4 [Testing Comportamentale e Simulazione dello Stato (LTS e Contratti)](#44-testing-comportamentale-e-simulazione-dello-stato-lts-e-contratti)
   - 4.5 [Analisi Grafica della Coreografia Globale e Rilevamento Cicli (Tarjan SCC)](#45-analisi-grafica-della-coreografia-globale-e-rilevamento-cicli-tarjan-scc)
   - 4.6 [Pianificazione e Disambiguazione dei Destinatari (1 a 1 e 1 a N)](#46-pianificazione-e-disambiguazione-dei-destinatari-1-a-1-e-1-a-n)
5. [Il Caso di Studio Applicativo: Missione Agricola Multi-Robot](#capitolo-5--il-caso-di-studio-applicativo-missione-agricola-multi-robot)
   - 5.1 [Definizione dei Robot e delle Operazioni Basilari Locali](#51-definizione-dei-robot-e-delle-operazioni-basilari-locali)
   - 5.2 [Analisi Dettagliata delle Interazioni End-to-End](#52-analisi-dettagliata-delle-interazioni-end-to-end)
   - 5.3 [Estensione dello Scenario 1 a N Parallelo](#53-estensione-dello-scenario-1-a-n-parallelo)
   - 5.4 [Specifica dei Diagrammi BPMN Generati](#54-specifica-dei-diagrammi-bpmn-generati)
6. [Architettura Software e Documentazione Completa delle Classi](#capitolo-6--architettura-software-e-documentazione-completa-delle-classi)
   - 6.1 [Struttura Modulare e Organizzazione dei Package](#61-struttura-modulare-e-organizzazione-dei-package)
   - 6.2 [Modello Dati Centrale](#62-modello-dati-centrale)
   - 6.3 [Documentazione Esaustiva dei 26 Componenti Software](#63-documentazione-esaustiva-dei-26-componenti-software)
7. [Input, Risorse di Configurazione e Artefatti Generati](#capitolo-7--input-risorse-di-configurazione-e-artefatti-generati)
   - 7.1 [Specifiche JSON: `semantics.json`, `taxonomy.json`, `behavioral-state.json`](#71-specifiche-json-semanticsjson-taxonomyjson-behavioral-statejson)
   - 7.2 [Modelli BPMN Locali di Input](#72-modelli-bpmn-locali-di-input)
   - 7.3 [Artefatti Prodotti dal Motore di Sintesi](#73-artefatti-prodotti-dal-motore-di-sintesi)
8. [Modalità Operative e Studio Web Interattivo](#capitolo-8--modalit-operative-e-studio-web-interattivo)
   - 8.1 [Esecuzione da Riga di Comando (CLI)](#81-esecuzione-da-riga-di-comando-cli)
   - 8.2 [Web Server REST e Server-Sent Events (SSE)](#82-web-server-rest-e-server-sent-events-sse)
   - 8.3 [Interfaccia Grafica Web (`index.html`) e BPMN Canvas](#83-interfaccia-grafica-web-indexhtml-e-bpmn-canvas)
   - 8.4 [Editor Modale In-Browser con Formattazione e Ricaricamento Live](#84-editor-modale-in-browser-con-formattazione-e-ricaricamento-live)
9. [Verifica Sperimentale, Suite di Test e Copertura](#capitolo-9--verifica-sperimentale-suite-di-test-e-copertura)
   - 9.1 [Report di Esecuzione dei Test JUnit 5 (63/63 PASS)](#91-report-di-esecuzione-dei-test-junit-5-6363-pass)
   - 9.2 [Dettaglio dei Casi di Test per Singola Classe](#92-dettaglio-dei-casi-di-test-per-singola-classe)
   - 9.3 [Scenari Convalidati e Aspetti Non Testati](#93-scenari-convalidati-e-aspetti-non-testati)
10. [Matrice di Tracciabilità Tesi vs Implementazione](#capitolo-10--matrice-di-tracciabilit-tesi-vs-implementazione)
11. [Tecnologie Utilizzate e Stack Software](#capitolo-11--tecnologie-utilizzate-e-stack-software)
12. [Conclusioni, Discrepanze e Sviluppi Futuri](#capitolo-12--conclusioni-discrepanze-e-sviluppi-futuri)
    - 12.1 [Valutazione dei Risultati Raggiunti](#121-valutazione-dei-risultati-raggiunti)
    - 12.2 [Analisi Trasparente delle Discrepanze](#122-analisi-trasparente-delle-discrepanze)
    - 12.3 [Linee di Ricerca Future](#123-linee-di-ricerca-future)

---

# Capitolo 1 — Introduzione, Contesto e Motivazioni

## 1.1 Introduzione ai Sistemi Multi-Robot (MRS)

Nei moderni contesti applicativi dell'ingegneria del software e dell'automazione — quali l'agricoltura di precisione, la logistica di magazzino, il soccorso in ambienti ostili e il monitoraggio ambientale — l'impiego di un singolo robot monolitico presenta limiti intrinseci di scalabilità, robustezza e tolleranza ai guasti. Al contrario, un **Sistema Multi-Robot (Multi-Robot System - MRS)** è composto da una flotta di entità autonome, fisicamente distribuite ed eterogenee, che collaborano per raggiungere un obiettivo di missione globale non conseguibile da un singolo agente operante in isolamento.

Ciascun robot possiede capacità sensoriali, attuative e computazionali specifiche. Tuttavia, la presenza di eterogeneità hardware e software introduce sfide critiche:
- **Disaccoppiamento dei cicli di vita**: ogni robot esegue il proprio processo di controllo in modo asincrono;
- **Canali di comunicazione inaffidabili**: lo scambio di messaggi wireless può presentare latenze variabili e buffer di ricezione finiti;
- **Assenza di memoria condivisa**: la collaborazione deve realizzarsi esclusivamente mediante protocolli di scambio messaggi espliciti;
- **Autonomia operativa**: i robot devono continuare ad assolvere compiti locali senza attendere costantemente direttive esterne.

## 1.2 Architetture di Coordinamento a Confronto

La letteratura scientifica classifica le architetture di coordinamento nei sistemi multi-robot in quattro categorie fondamentali:

```
+---------------------------------------------------------------------------------------+
| Architettura    | Struttura di Controllo      | Vantaggi               | Svantaggi    |
+-----------------+-----------------------------+------------------------+--------------+
| Centralizzata   | Un unico controllore Master | Semplicità logica;     | Single point |
|                 | dirige tutti i nodi Slave   | visione globale dello  | of failure;  |
|                 |                             | stato della missione   | collo di     |
|                 |                             |                        | bottiglia.   |
+-----------------+-----------------------------+------------------------+--------------+
| Gerarchica      | Nodi di controllo ad albero | Migliore scalabilità   | Rigidità di  |
|                 | su più livelli decisionali  | rispetto al modello    | riconfigura- |
|                 |                             | centralizzato puro     | zione dinam. |
+-----------------+-----------------------------+------------------------+--------------+
| Ibrida          | Controllo locale distribuito| Flessibilità operativa;| Elevata      |
|                 | con supervisori globali     | buon compromesso       | complessità  |
|                 |                             | reattività/decisione   | di design    |
+-----------------+-----------------------------+------------------------+--------------+
| Decentralizzata | Nessun coordinatore master; | Massima robustezza;    | Elevato      |
| (Distribuita)   | interazione peer-to-peer    | scalabilità illimitata;| rischio di   |
|                 | basata su coreografia       | tolleranza ai guasti   | deadlock o   |
|                 |                             | fisici                 | divergenza   |
+---------------------------------------------------------------------------------------+
```

L'architettura **decentralizzata (peer-to-peer)** rappresenta la scelta più indicata per scenari operativi in campo aperto (come l'agricoltura o il disastro ambientale), dove la connettività verso una stazione centrale può venire meno e i robot devono essere in grado di coordinarsi autonomamente.

## 1.3 Il Problema Fondamentale del Coordinamento Distribuito

Nel coordinamento puramente distribuito, il problema centrale — ampiamente sviscerato nella tesi magistrale di Giovanni Spaziani — consiste nell'assicurare che l'insieme dei processi operativi eseguiti localmente dai singoli robot dia luogo a un comportamento collettivo globale privo di deadlock, consistente rispetto all'ordine logico dei task e rispettoso delle dipendenze di dato della missione, **senza richiedere l'intervento di un controllore centralizzato**.

Partendo dai soli modelli operativi di base (i *processi locali* non coordinati che descrivono i compiti interni eseguiti dal robot da solo) e da una *specifica globale astratta* (la coreografia di alto livello), la sintesi manuale del coordinamento distribuito è un'attività altamente incline a errori umani. Gli ingegneri dovrebbero identificare manualmente:
1. Quali task interni producono dati rilevanti per la collaborazione pubblica;
2. Quali robot remoti necessitano di tali informazioni;
3. Se i tipi di dato e i concetti semantici espressi dai robot sono reciprocamente compatibili;
4. In quale posizione esatta del diagramma di processo locale iniettare le attività di invio (*Send Task*) e ricezione (*Receive Task*);
5. Come garantire che l'iniezione non crei stalli (*deadlock*), vicoli ciechi (*dead-ends*) o corse critiche (*race conditions*).

## 1.4 Scopo della Tesi e Posizionamento del Framework

La tesi magistrale di Giovanni Spaziani, svolta presso il DISIM dell'Università degli Studi dell'Aquila con il Prof. Massimo Tivoli, affronta questo problema proponendo un **Metodo Automatico di Sintesi della Collaborazione tra MRS**.

Il framework software oggetto di questa documentazione — denominato **MRS Synthesis Engine** — costituisce la piena implementazione computazionale, formalizzazione ed estensione di tale metodo. Il sistema riceve in ingresso:
- I processi locali BPMN dei singoli robot (modellanti le azioni basilari);
- Il modello BPMN della Coreografia Globale della missione;
- Le specifiche semantiche e ontologiche dei messaggi e dei tipi di dato scambiati (`semantics.json` e `taxonomy.json`);
- Le configurazioni opzionali dei contratti di stato comportamentale (`behavioral-state.json`).

Il motore elabora tali ingressi e genera in uscita:
- I **diagrammi BPMN locali migliorati (Enhanced BPMN Models)** per ciascun robot, contenenti i punti di invio e ricezione correttamente cablati e sincronizzati;
- Il **Diagramma di Collaborazione Unificato (Unified Mission Collaboration Diagram)** in cui tutte le pool dei robot e la coreografia sono allineate visivamente con flussi di messaggi inter-pool (*MessageFlow*);
- I diagrammi visuali compatibili con **Draw.io / diagrams.net**;
- Un ambiente di sviluppo e ispezione interattivo dotato di Web Server REST, streaming real-time degli eventi (SSE) e canvas BPMN.io.

## 1.5 Introduzione al Caso di Studio: Precision Agriculture

Per dimostrare l'efficacia del metodo automatico, sia la tesi sia il framework implementativo adottano come scenario applicativo di riferimento una missione di **Agricoltura di Precisione (Precision Agriculture)** cooperativa, condotta da una flotta di quattro robot eterogenei:

```
  +--------------+
  |    DRONE     |  [Ricognitore e Coordinatore Iniziale]
  +-------+------+
          | (weedPositionMessage)
          v
  +--------------+
  |  TAGLIATORE  |  [Trattore Rasaerba per Rimozione Erbacce]
  +-------+------+
          | (fieldReadyMessage)
          v
  +--------------+
  |  SCAVATORE   |  [Trattore Escavatore per Apertura Solchi]
  +-------+------+
          | (digCompletedMessage)
          v
  +--------------+
  |  SEMINATORE  |  [Robot Seminatore per Distribuzione Sementi]
  +--------------+
```

1. **Drone (Robot Aereo)**: Sorvola il campo agricolo, analizza le immagini tramite sensori ottici multispettrali, individua la zona infestata da erbacce e seleziona il trattore più idoneo e vicino.
2. **Tagliatore (Trattore di Superficie)**: Autovaluta il proprio stato e la propria disponibilità operativa, comunica la propria idoneità, riceve l'assegnazione dell'area dal Drone, naviga fino alla zona designata e taglia le erbacce, lasciando il terreno pulito.
3. **Scavatore (Macchina Operatrice)**: Attende che il campo sia stato liberato dal Tagliatore, si porta sui punti designati ed esegue l'aratura e lo scavo dei solchi necessari alla posa dei semi.
4. **Seminatore (Robot Specializzato)**: Attende la conclusione dello scavo, deposita con precisione le sementi nei solchi, compatta il terreno e dichiara conclusa la missione agricola.


# Capitolo 2 — Background Teorico e Fondamenti Formali

## 2.1 Coreografie di Servizi: Definizione Formale e Semantica

Il concetto di **coreografia** trae origine dall'architettura orientata ai servizi (Service-Oriented Architecture - SOA) e descrive il comportamento di cooperazione multi-partecipante da una prospettiva globale "super partes". 

A differenza dell'**orchestrazione** (in cui un'entità centrale governa il flusso di esecuzione invocando gli altri partecipanti), una coreografia definisce:
- L'insieme dei partecipanti autorizzati a interagire;
- I messaggi che essi possono o devono scambiarsi;
- I vincoli di ordinamento parziale temporale e logico tra gli scambi di messaggi.

Formalmente, una coreografia può essere vista come un automa a stati finiti o un **Labeled Transition System (LTS)** globale:

$$\mathcal{C} = \langle S, S_0, \mathcal{A}, \Delta, F \rangle$$

dove:
- $S$ è l'insieme finito degli stati globali della cooperazione;
- $S_0 \in S$ è lo stato iniziale della missione;
- $\mathcal{A}$ è l'alfabeto delle interazioni elementari, ciascuna modellata come una tripla $\langle m, P_{mittente}, P_{destinatario} \rangle$;
- $\Delta \subseteq S \times \mathcal{A} \times S$ è la relazione di transizione;
- $F \subseteq S$ è l'insieme degli stati finali ammissibili.

## 2.2 Specifica di Coreografie mediante BPMN 2.0

La notazione **BPMN 2.0 (Business Process Model and Notation)** standardizzata da OMG costituisce il linguaggio grafico e formale d'elezione per modellare sia i processi locali dei singoli robot, sia la coreografia globale.

La specifica BPMN 2.0 definisce due costrutti primari con scopi profondamente differenti:

```
+---------------------------------------------------------------------------------------+
| Costrutto BPMN 2.0     | Choreography Diagram         | Collaboration Diagram        |
+------------------------+------------------------------+------------------------------+
| Punto di vista         | Globale ("vista aerea")      | Puntuale ("peer-to-peer")    |
| Elemento principale    | Choreography Task            | Pool / Lane separate         |
| Flusso di controllo    | Sequence Flow tra interaz.   | Sequence Flow interno al robot|
| Comunicazione          | Contenuta nel task           | Message Flow tra Pool distinte|
+---------------------------------------------------------------------------------------+
```

### Componenti Fondamentali della Notazione BPMN:

1. **Choreography Task**: Un rettangolo arrotondato suddiviso in tre bande orizzontali:
   - *Banda Superiore*: Il partecipante iniziatore/mittente (sfondo bianco);
   - *Banda Centrale*: Il nome dell'interazione o del messaggio scambiato;
   - *Banda Inferiore*: Il partecipante ricevente/destinatario (sfondo ombreggiato).
2. **Service Task**: Rappresenta un'attività locale e interna eseguita dal robot in isolamento (contrassegnata dall'icona a ingranaggio). Non genera direttamente messaggi esterni.
3. **Send Task**: Attività con icona a busta piena (nera), che modella l'emissione asincrona di un messaggio verso un altro partecipante.
4. **Receive Task**: Attività con icona a busta vuota (bianca), che modella l'attesa e la ricezione bloccante di un messaggio atteso.
5. **Gateways**:
   - *Parallel Gateway (+)*: Divergenza (fork) per l'avvio concorrente di rami paralleli; convergenza (join) per la sincronizzazione obbligatoria di tutti i rami in ingresso.
   - *Exclusive Gateway (X)*: Scelta condizionale deterministica basata su guardie booleane disgiunte.
6. **Eventi di Controllo**:
   - *Start Event (Cerchio Verde/Sottile)*: Inizio del processo;
   - *End Event (Cerchio Rosso/Spesso)*: Terminazione con successo del processo;
   - *Intermediate Catch Event (Doppio cerchio con orologio)*: Sospensione temporanea del flusso per una durata prefissata (Timer Event).

## 2.3 Il Problema della Realizability nei Sistemi Multi-Robot

Una coreografia astratta $\mathcal{C}$ si definisce **realizzabile (realizable)** se e solo se esiste un insieme di comportamenti locali distribuiti $\{B_1, B_2, \dots, B_n\}$ (uno per ciascun partecipante $P_i$) la cui esecuzione asincrona e concorrente produce esattamente lo stesso insieme di tracce di comunicazione ammesse dalla coreografia globale, senza introdurre deadlock, violazioni di vincoli o comunicazioni spurie:

$$\mathcal{L}(B_1 \parallel B_2 \parallel \dots \parallel B_n) = \mathcal{L}(\mathcal{C})$$

Nei Sistemi Multi-Robot operanti con comunicazioni distribuite, la non-realizzabilità si manifesta tipicamente a causa di tre patologie:
1. **Inconsistenza di Ordinamento Temporale**: Un partecipante $P_3$ riceve un messaggio da $P_2$ prima che $P_2$ abbia ricevuto la notifica da $P_1$, a causa di latenze asimmetriche nei canali radio.
2. **Deadlock da Attesa Circolare**: Due o più robot entrano in una situazione di blocco mutuo in cui ciascuno attende un messaggio dall'altro (es. $R_1$ attende $R_2$ prima di inviare $m_1$, ma $R_2$ attende $m_1$ per sbloccare la propria ricezione).
3. **Corsa Critica e Perdita di Messaggi nei Buffer**: Se un robot trasmette messaggi a raffica senza che il ricevente abbia raggiunto il corrispondente stato di ricezione (*Receive Task*), i messaggi possono andare persi se la dimensione del buffer locale (queue 1-bounded) è superata.

## 2.4 Strategie di Risoluzione della Realizability e Coordination Delegates

Per fronteggiare il problema della realizzabilità, la letteratura individua tre approcci metodologici:
- **Verifica Statica**: Analisi preventiva dei modelli mediante model checking (es. SPIN, PRISM, Uppaal) per identificare cicli negativi e violazioni prima del deployment;
- **Choreography Repair**: Riscrittura algoritmica della coreografia per eliminare le ambiguità strutturali;
- **Runtime Enforcement mediante Coordination Delegates (CDs)**: Introduzione di componenti software intermediari (delegati di coordinamento) collocati a fianco di ciascun robot. I CD intercettano i messaggi in transito e applicano politiche di bufferizzazione, serializzazione e re-inoltro per forzare il sistema distribuito a rispettare l'ordine coreografico prescritto.

## 2.5 Le Quattro Fasi dell'Approccio Formale

Nel quadro metodologico della tesi, il paradigma generale per garantire la cooperazione si articola su quattro fasi:

```
+---------------------------------------------------------------------------------------+
| Fase          | Input                   | Output                  | Livello di        |
|               |                         |                         | Astrazione        |
+---------------+-------------------------+-------------------------+-------------------+
| Proiezione    | Coreografia Globale     | Comportamenti Locali    | Astratto          |
| (Projection)  |                         | Astratti dei Partecip.  |                   |
+---------------+-------------------------+-------------------------+-------------------+
| Selezione     | Service Inventory       | Partecipanti Concreti   | Concreto (senza   |
| (Selection)   | (Robot disponibili)     | Compatibili Selezionati | coordinamento)    |
+---------------+-------------------------+-------------------------+-------------------+
| Sintesi       | Coreografia Globale +   | CD Abstract Behavior    | Astratto          |
| (Synthesis)   | Partecipanti Selezionati| (Logica di Coordinam.)  | (Logica pura)     |
+---------------+-------------------------+-------------------------+-------------------+
| Concretizzaz. | CD Abstract Behavior    | CD Concrete Behavior    | Concreto          |
| (Concretiz.)  |                         | (Code e socket 1-bound) | (Eseguibile)      |
+---------------------------------------------------------------------------------------+
```

Mentre la formalizzazione generale prevede la generazione di Coordination Delegates esterni, il **Metodo Automatico del Capitolo 5** sintetizza la collaborazione **iniettando direttamente la logica di coordinamento nei processi locali dei robot**, eliminando il sovraccarico architetturale di delegati esterni separati.


# Capitolo 3 — Metodologia di Sintesi Automatica della Collaborazione

## 3.1 I Principi Fondamentali del Metodo Automatico (Capitolo 5 della Tesi)

Il Capitolo 5 della tesi di laurea magistrale di Giovanni Spaziani formalizza il cuore innovativo della ricerca: il **Metodo Automatico per la Collaborazione tra MRS**.

Il principio fondante su cui poggia l'intero impianto metodologico è enunciato come segue:

> *"Ogni interazione tra robot nasce nel momento esatto in cui un robot produce un'informazione pubblica che un altro robot deve consumare per consentire la prosecuzione del flusso operativo della missione comune."*

Il metodo non impone ai progettisti di riscrivere da capo il software di ciascun robot. Al contrario, esso opera come un sintetizzatore trasparente che assume come input:
1. **I Processi Locali Originali**: diagrammi BPMN che descrivono i compiti elementari svolti da ciascun robot in modalità isolata (*operazioni basilari*);
2. **La Coreografia Globale BPMN**: specifica astratta di alto livello dei vincoli collaborativi e dei messaggi da scambiare;
3. **L'Ontologia Globale dei Tipi di Dato**: specifica delle relazioni tassonomiche e semantiche tra le informazioni trattate.

## 3.2 Le Sei Operazioni Fondamentali di Sintesi

Per ricostruire automaticamente il coordinamento distribuito, il metodo formale prescrive l'esecuzione delle seguenti **sei operazioni fondamentali**:

```
  +-------------------------------------------------------------------+
  | 1. Analisi dei task interni e delle informazioni prodotte localm. |
  +---------------------------------+---------------------------------+
                                    |
  +---------------------------------v---------------------------------+
  | 2. Confronto con le informazioni richieste nella coreografia      |
  +---------------------------------+---------------------------------+
                                    |
  +---------------------------------v---------------------------------+
  | 3. Verifica di compatibilità (Sintattica e Semantica)            |
  +---------------------------------+---------------------------------+
                                    |
  +---------------------------------v---------------------------------+
  | 4. Determinazione dei robot destinatari da attivare               |
  +---------------------------------+---------------------------------+
                                    |
  +---------------------------------v---------------------------------+
  | 5. Determinazione della modalità di attivazione (SEQ/PAR/EXCL)   |
  +---------------------------------+---------------------------------+
                                    |
  +---------------------------------v---------------------------------+
  | 6. Integrazione (Weaving) delle interazioni nei processi locali   |
  +-------------------------------------------------------------------+
```

1. **Analisi dei task interni**: Ispezione delle firme di input/output di ogni singola attività locale dei robot partecipanti;
2. **Confronto delle informazioni**: Incrocio tra i dati resi pubblici dai produttori e i flussi di messaggio previsti nella coreografia globale;
3. **Verifica di compatibilità sintattica e semantica**: Filtraggio rigoroso dei match candidati per escludere discordanze di tipo, ruoli logici incompatibili o violazioni ontologiche;
4. **Determinazione dei robot destinatari**: Risoluzione delle ambiguità quando un messaggio può essere potenzialmente recapitato a molteplici partecipanti;
5. **Determinazione della modalità di attivazione**: Inferenza delle relazioni di concorrenza, sequenza o esclusione mutua tra i destinatari;
6. **Integrazione automatica nei processi locali (Weaving)**: Modifica strutturale del diagramma BPMN del robot mediante iniezione di *Send Task* e *Receive Task*, ricablando i *Sequence Flow* senza violare la semantica originale.

## 3.3 Le Tre Fasi Computazionali Principali

Dal punto di vista dell'architettura logica e computazionale, la tesi magistrale raggruppa le sei operazioni in **tre macro-fasi sequenziali**:

### STEP 1 — Analisi dello Scambio Dati Input/Output
In questa prima fase il sistema analizza i metadati I/O di tutti i task locali e legge i flussi di messaggio della coreografia globale. Viene costruita una matrice preliminare di candidati di scambio dati $\mathcal{M}_{candidati} = \{\langle R_{prod}, T_{prod}, R_{cons}, T_{cons}, msg \rangle\}$. Il metodo non prende decisioni definitive in questa fase, ma individua tutti i potenziali accoppiamenti informativi.

### STEP 2 — Analisi della Compatibilità Semantica e Comportamentale
I candidati individuati nello STEP 1 vengono sottoposti al filtro di compatibilità:
- *Verifica Sintattica*: compatibilità di tipo esatta o per sottotipo ($T_{prod} \sqsubseteq T_{cons}$);
- *Verifica Semantica*: conformità del concetto ontologico associato all'informazione nel dominio della missione;
- *Testing Comportamentale*: simulazione dell'impatto dell'iniezione sul processo locale del ricevente (raggiungibilità dello stato, assenza di vicoli ciechi, soddisfacimento delle precondizioni).

### STEP 3 — Sintesi dell'Intera Missione e Generazione BPMN
I match validati vengono aggregati in piani di interazione (*InteractionPlan*). Il sistema deduce la modalità di fan-out (sequenziale, parallela o esclusiva) e attiva il modulo di **BPMN Flow Weaving**, che modifica i diagrammi locali clonati, inietta i nodi di comunicazione, calcola le coordinate visuali BPMNDI (*Auto-Layout*) e produce il diagramma unificato di collaborazione.

## 3.4 Corrispondenza tra Metodologia Formale e Pipeline Implementativo

Nell'implementazione software del framework (`MultiRobotSynthesisEngine.java`), il flusso computazionale è stato suddiviso ingegneristicamente in **sette passaggi implementativi**. È fondamentale chiarire la corrispondenza biunivoca tra le 3 fasi formali della tesi e i 7 passaggi del codice:

```
+---------------------------------------------------------------------------------------+
| Fase Formale Tesi | Operazione Tesi | Step Implementativo Codice (Engine Pipeline)     |
+-------------------+-----------------+-------------------------------------------------+
| FASE 1:           | Op. 1           | Step 1: Analisi processi locali (I/O Metadata)  |
| Analisi I/O       | Op. 2           | Step 2: Estrazione flussi messaggio coreografia  |
+-------------------+-----------------+-------------------------------------------------+
| FASE 2:           | Op. 3           | Step 3: Validazione sintattica e semantica      |
| Compatibilità e   | Op. 4           | Step 4: Testing comportamentale su LTS locale   |
| Testing           | Op. 5           | Step 5: Pianificazione interazioni (1->1 e 1->N)|
+-------------------+-----------------+-------------------------------------------------+
| FASE 3:           | Op. 6           | Step 6: BPMN Flow Weaving e proiezione controlli|
| Sintesi Missione  | Op. 6 (visuale) | Step 7: Generazione Unified Diagram e Draw.io   |
+---------------------------------------------------------------------------------------+
```

Questa ripartizione garantisce il pieno disaccoppiamento tra la fase analitica (Step 1-2), la fase di validazione logica (Step 3-4-5) e la fase di manipolazione del codice XML/BPMN (Step 6-7).

## 3.5 Distinzione tra Specifiche della Tesi ed Estensioni Software

Al fine di garantire la massima trasparenza scientifica, la tabella seguente documenta con precisione gli aspetti derivati direttamente dalla tesi e le estensioni implementative introdotte dal progetto:

```
+---------------------------------------------------------------------------------------+
| Aspetto Metodologico      | Tesi Magistrale Spaziani    | Framework MRS Synthesis Eng. |
+---------------------------+-----------------------------+------------------------------+
| Modello Formale           | 6 Operazioni, 3 Macro-Fasi  | 7 Passaggi modulari Java     |
| Rappresentazione Dati     | Tipi di dato astratti       | File JSON (`semantics.json`) |
| Ontologia di Dominio      | Subsumption teorica A ⊑ B   | Tassonomia JSON e Fallback AI|
| Valutazione Semantica     | Concettuale / Manuale       | Matcher Ibrido + LLM Cascade |
| Testing Comportamentale   | Simulazione concettuale LTS | Algoritmo DFS/BFS a 4 livelli|
| Modifica BPMN             | Regole formali di riscritt. | Iniezione DOM Camunda 7.20   |
| Disposizione Visuale      | Diagrammi disegnati a mano  | Layout BPMNDI + Draw.io XML  |
| Fruizione Utente          | Modelli statici             | Web Studio REST + Live SSE   |
+---------------------------------------------------------------------------------------+
```


# Capitolo 4 — Algoritmi e Meccanismi di Verifica del Framework

## 4.1 Verifica di Compatibilità Sintattica (STRONG, WEAK e Strutturale)

La verifica sintattica — implementata nella classe `SyntacticCompatibilityChecker.java` in conformità alla Sezione 5.4 della tesi — accerta che i dati emessi dal task produttore siano strutturalmente e tipologicamente consumabili dal task ricevente.

Il sistema prevede tre livelli di controllo sintattico:

### 1. Compatibilità Forte (STRONG Type Equality)
Prescrive che il tipo dichiarato dal produttore coincida esattamente con quello atteso dal ricevente:

$$\text{Compatible}_{STRONG}(T_{prod}, T_{req}) \iff \text{normalize}(T_{prod}) = \text{normalize}(T_{req})$$

Ad esempio, se il Drone emette un dato di tipo `Boolean` e il Tagliatore richiede `Boolean`, la compatibilità STRONG è soddisfatta.

### 2. Compatibilità Debole (WEAK Subtype Compatibility)
Si basa sulla nozione di sottotipo e subsumption tassonomica. Se il tipo $T_{prod}$ è un sottotipo di $T_{req}$ ($T_{prod} \sqsubseteq T_{req}$), l'informazione trasmessa dal produttore contiene un livello di dettaglio maggiore o uguale rispetto a quello minimo atteso dal consumatore, rendendo il dato pienamente utilizzabile:

$$\text{Compatible}_{WEAK}(T_{prod}, T_{req}) \iff T_{prod} \sqsubseteq T_{req}$$

Esempio: $CoordinateZonaInteresse \sqsubseteq AreaLavoro$. Il tipo specifico di coordinate soddisfa il requisito generico di area di lavoro.

### 3. Compatibilità Strutturale basata sui Campi (Field-Level Structural Match)
Qualora i tipi siano entità complesse composte da molteplici attributi, la compatibilità viene verificata confrontando gli insiemi dei campi dichiarati. Il produttore deve fornire un sovrainsieme dei campi richiesti dal consumatore:

$$\mathcal{F}_{req} \subseteq \mathcal{F}_{prod}$$

Nel metodo `isStructurallyCompatible(producedFields, requiredFields)`:
- Ciascun campo è normalizzato nella forma `nome:tipo` (es. `latitude:double`, `id:int`);
- Se il ricevente richiede `[x:double, y:double]` e il produttore genera `[x:double, y:double, altitude:double, id:int]`, il controllo passa con successo;
- Se anche un solo campo obbligatorio per il ricevente è assente nel tipo prodotto, il controllo fallisce in modo deterministico (*fail-closed*).

## 4.2 Verifica di Compatibilità Semantica, Ontologie e Subsumption

La sola conformità sintattica non previene errori semantici: due informazioni possono possedere lo stesso tipo primitivo (es. `Double`) ma descrivere concetti operativi radicalmente disgiunti (es. `LivelloCaricaBatteria` vs `AltitudineVolo`).

La classe `OntologyManager.java` implementa un gestore ontologico ispirato al framework *Strawberry*, caricando le definizioni dal file `taxonomy.json`. L'ontologia modella due strutture:
1. **Gerarchia dei Tipi di Dato**: alberi di derivazione tassonomica tra tipi concreti;
2. **Gerarchia dei Concetti Semantici**: relazioni di sussunzione ontologica tra ruoli astratti di missione:

$$\text{Concept}_{specifico} \sqsubseteq \text{Concept}_{generale}$$

Ad esempio, nel dominio dell'agricoltura:
$$\text{AgriculturalWorkZone} \sqsubseteq \text{TargetLocation}$$
Il Drone che rileva la zona erbacce genera un'istanza di `AgriculturalWorkZone`, che sussume il concetto di `TargetLocation` atteso dal piano di taglio del trattore.

## 4.3 Ragionamento Semantico Ibrido e Integrazione LLM

La classe `AISemanticMatcher.java` orchestra la validazione semantica supportando tre modalità operative distinte:

```
  [Richiesta di Match Semantico]
                 |
                 v
   +----------------------------+
   |  Tipo Sintatticamente OK?  |----(NO)----> [REJECT: Incompatibilità Sintattica]
   +-------------+--------------+
                 | (SI)
                 v
   +----------------------------+
   |    Modalità Selezionata    |
   +-------------+--------------+
                 |
     +-----------+-----------+
     |                       |                       |
     v                       v                       v
[ONTOLOGY_STRICT]         [HYBRID]             [LLM_REASONER]
     |                       |                       |
Controlla solo          Concetti nel            Interroga sempre
`taxonomy.json`        `taxonomy.json`?          l'LLM Cascade
     |                  /          \                 |
  Esito                (SI)        (NO: assenti)     Esito
  Diretto               |               |            LLM
                    Verifica        Interroga
                   Subsumption     LLM Cascade
                    Ontologica          |
                        |             Esito
                     Esito             LLM
                    (No LLM!)
```

### Analisi Dettagliata delle Modalità:

#### 1. Modalità HYBRID (Predefinita)
Costituisce la modalità di funzionamento standard del motore. La sua logica interna segue rigorosamente queste regole:
1. Verifica preliminare di compatibilità sintattica (STRONG o WEAK);
2. Se i concetti semantici sono dichiarati in entrambi i task, il sistema interroga l'ontologia locale (`ontologyManager.isSemanticConceptCompatible`). 
   - Se l'ontologia valida la sussunzione $\rightarrow$ **MATCH APPROVATO**;
   - Se l'ontologia riscontra incompatibilità $\rightarrow$ **MATCH RIFIUTATO**. **In nessun caso il sistema attiva il fallback LLM se i concetti sono esplicitamente dichiarati e incompatibili** (risolvendo il requisito di non-ambiguità della tesi);
3. **Il fallback LLM viene attivato esclusivamente quando uno o entrambi i concetti ontologici sono assenti o vuoti**, demandando all'intelligenza generativa l'interpretazione del testo libero delle descrizioni funzionali.

#### 2. Modalità ONTOLOGY_STRICT
Esclude totalmente l'intelligenza artificiale generativa. Richiede la presenza formale dei concetti semantici in `taxonomy.json`. Se un concetto non è presente nel file ontologico, la transazione viene respinta immediatamente.

#### 3. Modalità LLM_REASONER
Bypassa le rigidità tassonomiche formali ed esegue l'analisi semantica interpretando le descrizioni in linguaggio naturale mediante la classe `LLMClient.java`.

### Architettura a Cascata di `LLMClient`:
La classe `LLMClient.java` implementa un meccanismo di fallback a più livelli (*Cascade Strategy*):
1. **Google Gemini Flash (Cloud AI con Auto-Discovery Dinamica)**: interroga l'endpoint REST di Google AI. Il client esegue un probing automatico dei modelli disponibili dando priorità a `gemini-2.0-flash`, `gemini-1.5-flash-latest`, `gemini-1.5-flash-001`, `gemini-1.5-flash-002` e `gemini-2.0-flash-exp`.
2. **OpenAI GPT-4o-mini (Cloud AI Secondario)**: invocato qualora le API di Gemini risultino non raggiungibili o esaurite per quota.
3. **Local Semantic Reasoner (Fallback Euristico Locale)**: componente deterministico integrato, privo di dipendenze di rete. Esegue l'analisi semantica delle stringhe testuali sfruttando lemmatizzazione ed euristiche di corrispondenza per il dominio agricolo (`erbacce`/`weed`, `taglio`/`lavoro`, `pronto`/`ready`, `scavo`/`dig`, `semina`/`seed`). Se le descrizioni sono prive di legami logici, fallisce in modo deterministico (*Decision: FALSE*).

### Risoluzione delle Credenziali (`resolveApiKey`):
Le chiavi API sono individuate controllando in ordine:
1. Chiave esplicita iniettata a runtime via interfaccia web o metodo setter;
2. Variabili d'ambiente di sistema (`GEMINI_API_KEY`, `GOOGLE_API_KEY`, `OPENAI_API_KEY`);
3. File locale `gemini.key` presente nella root di esecuzione del progetto.

## 4.4 Testing Comportamentale e Simulazione dello Stato (LTS e Contratti)

Implementato nella classe `BehavioralCompatibilityTester.java` in aderenza alla Sezione 5.6 della tesi, il testing comportamentale verifica che l'inserimento dell'interazione non introduca stalli operativi o violazioni dell'automa a stati del robot ricevente.

Il test si struttura su **quattro livelli di conformità (Livelli A, B, C, D)**:

```
+---------------------------------------------------------------------------------------+
| Livello | Proprietà Verificata | Algoritmo / Metodo          | Criterio di Fallimento |
+---------+----------------------+-----------------------------+------------------------+
| Liv. A  | Raggiungibilità del  | BFS (Breadth-First Search)  | Nessun cammino valido  |
|         | task ricevente       | dallo StartEvent            | dallo Start al Task    |
+---------+----------------------+-----------------------------+------------------------+
| Liv. B  | Terminazione e       | DFS ricorsiva con memoiz-   | Presenza di vicoli     |
|         | assenza di dead-end  | zazione verso EndEvents     | ciechi o stalli locali |
+---------+----------------------+-----------------------------+------------------------+
| Liv. C  | Soddisfacimento      | Controllo su requiredData-  | Tipo prodotto non      |
|         | requisiti di input   | Types con OntologyManager   | presente negli input   |
+---------+----------------------+-----------------------------+------------------------+
| Liv. D  | Simulazione di stato | `BehavioralStateSimulator`: | Precondizione violata; |
|         | contratti pre/post   | accumulo effetti e verifica | stato contraddittorio  |
+---------------------------------------------------------------------------------------+
```

### Simulazione Deterministica dei Contratti (`BehavioralStateSimulator.java`):
I task possono dichiarare precondizioni ed effetti nella forma `chiave=valore` (es. `terreno_pronto=true`, `motore=acceso`).
La classe `BehavioralStateSimulator`:
1. Raccoglie lo stato iniziale definito in `behavioral-state.json`;
2. Propaga gli effetti generati dai prefissi di esecuzione del robot produttore (`stateWithPriorInteractionEffects`);
3. Simula l'esecuzione sequenziale delle attività lungo ogni percorso che attraversa il task consumatore;
4. Se una precondizione richiesta da un task a valle non è soddisfatta dal valore accumulato nello stato, il simulatore restituisce `Status.FAIL`, segnalando la violazione contrattuale.

## 4.5 Analisi Grafica della Coreografia Globale e Rilevamento Cicli (Tarjan SCC)

La classe `GlobalChoreographyAnalyzer.java` effettua un'analisi formale del grafo della coreografia globale. Per distinguere i cicli legittimi di riesecuzione (come i tentativi periodici di interrogazione di disponibilità) da loop infiniti errati, l'analizzatore impiega l'**algoritmo di Tarjan per la ricerca delle Componenti Fortemente Connesse (Strongly Connected Components - SCC)**.

Un ciclo globale è validato e ammesso **se e solo se** la componente fortemente connessa soddisfa il *Pattern di Retry Controllato*:
1. Contiene almeno un evento di attesa temporizzata (*IntermediateCatchEvent* con definizione Timer);
2. Contiene almeno un bivio condizionale (*ExclusiveGateway*) dotato di una guardia booleana che consenta l'uscita deterministica dal ciclo;
3. Tutti i flussi di sequenza interni al ciclo sono coerenti e raggiungibili.

Se un ciclo non presenta un evento timer o una via di fuga condizionale, il framework emette una diagnostica bloccante di potenziale *livelock*.

## 4.6 Pianificazione e Disambiguazione dei Destinatari (1 a 1 e 1 a N)

La classe `InteractionPlanner.java` (Sezione 5.7 della tesi) aggrega tutti i match validati generati da una medesima attività di origine (*anchor task*):

### Regole di Disambiguazione dell'Attivazione:
1. **Modalità SEQUENCE (1 a 1 o 1 a N Sequenziale)**:
   - Se il task produce un messaggio destinato a un unico ricevente, la modalità è `SEQUENCE`.
   - Se il task produce messaggi per più destinatari, ma nella coreografia globale tali attività sono poste in relazione di precedenza temporale stretta (verificata mediante ordinamento topologico totale `areTotallyOrdered`), i match sono ordinati per rango globale (`globalOrderRank`) e attivati in serie.
2. **Modalità PARALLEL (1 a N Parallelo)**:
   - I task riceventi derivano da un `ParallelGateway` divergente comune nella coreografia globale, e ciascun messaggio percorre un ramo indipendente. Il motore programma l'invio simultaneo broadcast.
3. **Modalità EXCLUSIVE (1 a N Esclusivo)**:
   - I destinatari sono separati da un `ExclusiveGateway` divergente, e ogni ramo possiede una guardia mutuamente esclusiva (es. `disponibile=true` vs `disponibile=false`). Il robot mittente attiva solo il destinatario la cui guardia risulta verificata.
4. **Modalità UNRESOLVED (Fail-Closed)**:
   - Qualora non vi sia evidenza strutturale certa nella coreografia sull'ordinamento dei destinatari, il pianificatore non inventa un comportamento arbitrario, ma etichetta il piano come `UNRESOLVED` segnalando la necessità di chiarire la specifica.


# Capitolo 5 — Il Caso di Studio Applicativo: Missione Agricola Multi-Robot

## 5.1 Definizione dei Robot e delle Operazioni Basilari Locali

In conformità al Capitolo 5 della tesi di Giovanni Spaziani, il sistema convalida la metodologia applicandola al caso di studio della missione agricola cooperativa con 4 robot eterogenei:

```
+---------------------------------------------------------------------------------------+
| Robot       | Identificativo BPMN | Operazioni Basilari (Processo Locale Isolato)     |
+-------------+---------------------+---------------------------------------------------+
| Drone       | `Drone.bpmn`        | Start -> Accensione (Task_Coordinate) ->          |
|             |                     | Calcolo coordinate erbacce ->                     |
|             |                     | Selezione trattore vicino -> Spegnimento -> End   |
+-------------+---------------------+---------------------------------------------------+
| Tagliatore  | `Tagliatore.bpmn`   | Start -> Accensione -> Autovalutazione stato ->   |
|             |                     | Taglio erbacce (Task_Taglio) -> Spegnimento -> End|
+-------------+---------------------+---------------------------------------------------+
| Scavatore   | `Scavatore.bpmn`    | Start -> Accensione -> Scavo solchi per semina    |
|             |                     | (Task_Scavo) -> Spegnimento -> End                |
+-------------+---------------------+---------------------------------------------------+
| Seminatore  | `Seminatore.bpmn`   | Start -> Accensione -> Distribuzione sementi      |
|             |                     | (Task_Semina) -> Chiusura missione -> End         |
+---------------------------------------------------------------------------------------+
```

Questi processi locali, prima dell'elaborazione, sono del tutto disgiunti: non contengono riferimenti reciproci, né code di messaggistica.

## 5.2 Analisi Dettagliata delle Interazioni End-to-End

Il motore di sintesi identifica e convalida esattamente **sei interazioni collaborative** tra i robot:

```
  (1) weedPositionMessage [CoordinateZonaInteresse -> AreaLavoro]
  Drone ========================================================> Tagliatore

  (2) disponibilitaTagliatoreMessage [Boolean -> Boolean]
  Tagliatore ===================================================> Drone

  (3) closestTractorMessage [CoordinateTrattoreVicino -> CoordinateTrattoreVicino]
  Drone ========================================================> Tagliatore

  (4) fieldReadyMessage [Boolean -> Boolean]
  Tagliatore ===================================================> Scavatore

  (5) digCompletedMessage [Boolean -> Boolean]
  Scavatore ====================================================> Seminatore

  (6) missionCompletedMessage [Boolean -> Boolean]
  Seminatore ===================================================> [Global End]
```

### 1. Interazione Drone $\rightarrow$ Tagliatore: `weedPositionMessage`
- **Mittente**: Drone (`Task_Coordinate`).
- **Destinatario**: Tagliatore (`Task_Autovalutazione`).
- **Tipo Emesso**: `CoordinateZonaInteresse` (strutturato con latitudine, longitudine, ampiezza).
- **Tipo Richiesto**: `AreaLavoro` (area generica di lavoro).
- **Verifica Sintattica**: WEAK Subtyping ($CoordinateZonaInteresse \sqsubseteq AreaLavoro$).
- **Verifica Semantica**: Sussunzione ontologica convalidata: la coordinata delle erbacce identifica l'area di lavoro per il trattore.
- **Weaving**: Il Drone inietta un `SendTask_weedPositionMessage`; il Tagliatore inietta un `ReceiveTask_weedPositionMessage` posizionato immediatamente prima del task di autovalutazione.

### 2. Interazione Tagliatore $\rightarrow$ Drone: `disponibilitaTagliatoreMessage`
- **Mittente**: Tagliatore (`Task_Autovalutazione`).
- **Destinatario**: Drone (`Task_SelectClosestTractor`).
- **Tipo Dati**: `Boolean` (TRUE = disponibile, pronto al lavoro; FALSE = batteria scarica / guasto).
- **Verifica**: Sintassi STRONG esatta; semantica di autovalutazione disponibilità.
- **Weaving**: Iniezione di Send Task nel Tagliatore e Receive Task nel Drone.

### 3. Interazione Drone $\rightarrow$ Tagliatore: `closestTractorMessage`
- **Mittente**: Drone (`Task_SelectClosestTractor`).
- **Destinatario**: Tagliatore (`Task_Taglio`).
- **Tipo Dati**: `CoordinateTrattoreVicino` (ID intero del trattore selezionato e punto di ingresso nel campo).
- **Verifica**: Corrispondenza esatta di tipo e concetto ontologico di assegnazione target.
- **Weaving**: Consente al Tagliatore di attivare l'operazione fisica di trinciatura erbacce.

### 4. Interazione Tagliatore $\rightarrow$ Scavatore: `fieldReadyMessage`
- **Mittente**: Tagliatore (`Task_Taglio`).
- **Destinatario**: Scavatore (`Task_Scavo`).
- **Tipo Dati**: `Boolean` (con valore TRUE a indicare campo pulito).
- **Verifica**:STRONG matching su tipo primitivo; il concetto descrive il completamento del diserbo.
- **Weaving**: Lo Scavatore attende la ricezione di questo messaggio prima di iniziare l'operazione di aratura.

### 5. Interazione Scavatore $\rightarrow$ Seminatore: `digCompletedMessage`
- **Mittente**: Scavatore (`Task_Scavo`).
- **Destinatario**: Seminatore (`Task_Semina`).
- **Tipo Dati**: `Boolean` (TRUE = scavo ultimato, solchi pronti).
- **Verifica**: Corrispondenza semantica e sintattica.
- **Weaving**: Iniezione della sincronizzazione prima del rilascio delle sementi.

### 6. Interazione Seminatore $\rightarrow$ Chiusura: `missionCompletedMessage`
- **Mittente**: Seminatore (`Task_Semina`).
- **Destinatario**: Chiusura globale della coreografia.
- **Tipo Dati**: `Boolean` (missione compiuta con successo).

## 5.3 Estensione dello Scenario 1 a N Parallelo

Nel paragrafo "Esempio esteso 1 $\rightarrow$ N" del Capitolo 5 della tesi, Giovanni Spaziani introduce uno scenario avanzato in cui il Drone, subito dopo aver individuato la zona erbacce, non trasmette l'informazione solo al Tagliatore, ma la **notifica in broadcast parallelo anche a Scavatore e Seminatore** a fini di preallerta e pre-pianificazione energetica dei percorsi futuri.

Il framework `mrs-synthesis-engine` gestisce nativamente questa configurazione:
1. `InteractionPlanner` rileva tre match aventi come produttore il medesimo task `Task_Coordinate` del Drone;
2. L'analisi sul grafo della coreografia rileva la presenza di un `ParallelGateway` divergente a valle del task;
3. Il piano viene marcato con modalità `PARALLEL`;
4. `BpmnFlowWeaver` inietta nel modello del Drone un gateway parallelo divergente, tre rami concorrenti con tre distinti `SendTask`, e un gateway parallelo convergente di risincronizzazione prima delle fasi successive.

## 5.4 Specifica dei Diagrammi BPMN Generati

L'esecuzione del motore produce quattro tipologie distinte di artefatti BPMN:

### 1. Choreography Diagram Globale
Rappresenta la vista omnicomprensiva della missione. Include la fase di raccolta disponibilità mediante gateway parallelo convergente, il gateway esclusivo decisionale `Tagliatore disponibile?`, il ramo di successo con l'invio di `closestTractorMessage`, e il ramo ciclico di retry contenente l'evento timer a 10 secondi (*IntermediateCatchEvent* con `PT10S`).

### 2. Diagrammi di Proiezione (Projection Diagrams)
Rappresentano la proiezione pura della coreografia su ciascun singolo partecipante, isolando i soli messaggi di cui il robot è mittente o destinatario, eliminando le attività degli altri partner.

### 3. Diagrammi Enhanced (Enhanced BPMN Models)
Rappresentano il risultato centrale del framework. Le attività basilari originali del robot vengono arricchite con i *SendTask* e *ReceiveTask* sintetizzati. Inoltre, grazie al metodo `projectAvailabilityRetryControl` di `BpmnFlowWeaver.java`, il **loop di retry del Tagliatore viene proiettato localmente**: il Tagliatore non esegue una sequenza lineare cieca, ma integra l'ExclusiveGateway e l'evento timer, rieseguendo l'autovalutazione se riceve una notifica di attesa.

### 4. Unified Collaboration Diagram (`UnifiedMission.bpmn`)
Un unico modello BPMN 2.0 contenente la pool della Coreografia Globale e le quattro pool orizzontali dei singoli robot (`Pool_Drone`, `Pool_Tagliatore`, `Pool_Scavatore`, `Pool_Seminatore`), reciprocamente interconnesse tramite elementi visuali `MessageFlow` tratteggiati con freccia aperta.


# Capitolo 6 — Architettura Software e Documentazione Completa delle Classi

## 6.1 Struttura Modulare e Organizzazione dei Package

Il progetto è sviluppato in linguaggio **Java (compatibile con Java 11, 17 e 21 LTS)** ed è organizzato secondo un'architettura modulare rigorosamente stratificata sotto il package radice `it.univaq.disim.mrs.synthesis`:

```
it.univaq.disim.mrs.synthesis
├── Main.java                          [Entry Point CLI e Web Server]
├── core
│   └── MultiRobotSynthesisEngine.java [Orchestratore della Pipeline a 7 Step]
├── model                              [Entità di Dominio e Modello Dati]
│   ├── MissionContext.java            [Stato Globale della Missione]
│   ├── InteractionMatch.java          [Corrispondenza Validata Produttore/Consumatore]
│   ├── InteractionPlan.java           [Piano di Attivazione 1->1 e 1->N]
│   ├── DataNode.java                  [Specifica I/O del Messaggio da JSON]
│   └── RobotTaskMetadata.java         [Metadati del Task Locale e Contratti LTS]
├── analyzer                           [Analisi Statica e Comportamentale]
│   ├── LocalProcessAnalyzer.java      [Estrazione Firme e Metadati dai BPMN Locali]
│   ├── GlobalChoreographyAnalyzer.java[Parsing Coreografia e Rilevamento Cicli SCC]
│   ├── BehavioralCompatibilityTester.java [Verifica Comportamentale a 4 Livelli]
│   └── BehavioralStateSimulator.java  [Simulatore di Stato Determinista key=value]
├── semantic                           [Compatibilità Sintattica e Ontologica]
│   ├── SyntacticCompatibilityChecker.java [Verifica STRONG, WEAK e Strutturale]
│   └── OntologyManager.java           [Caricamento Tassonomia JSON e Subsumption]
├── ai                                 [Intelligenza Artificiale Generativa]
│   ├── AISemanticMatcher.java         [Matcher Ibrido: Ontologia + LLM Reasoning]
│   └── LLMClient.java                 [Client REST Cloud/Local con Model Discovery]
├── planner                            [Pianificazione Interazioni]
│   └── InteractionPlanner.java        [Disambiguazione SEQ, PARALLEL, EXCLUSIVE]
├── weaver                             [Tessitura dei Processi BPMN]
│   ├── BpmnFlowWeaver.java            [Iniezione Send/Receive e Retry Projection]
│   └── BpmnAutoLayout.java            [Layout Automatico BPMNDI e Shift Ricorsivo]
├── generator                          [Esportazione Artefatti e Visualizzazione]
│   ├── UnifiedDiagramBuilder.java     [Costruzione Diagramma Unificato Multi-Pool]
│   ├── DrawioExporter.java            [Esportatore Visuale per diagrams.net]
│   └── BpmndiGenerator.java           [Generatore di Primitive Visuali BPMNDI]
├── input                              [Gestione Input Utente]
│   └── UserInputManager.java          [Validazione e Parsing di `semantics.json`]
├── persistence                        [Salvataggio e Convalida Modelli]
│   └── ModelRepository.java           [Lettura/Scrittura File BPMN con Camunda]
├── event                              [Monitoraggio Real-Time della Sintesi]
│   ├── SynthesisEvent.java            [Modello Evento Seriale JSON per SSE]
│   └── SynthesisEventListener.java    [Interfaccia Observer per il Monitoraggio]
└── web                                [Interfaccia Utente e Servizi REST]
    └── SynthesisWebServer.java        [Server HTTP Spark, REST Endpoints e SSE]
```

## 6.2 Modello Dati Centrale

La pipeline opera manipolando un insieme coeso di classi modello:

- **`MissionContext`**: Mantiene lo stato completo della sintesi. Contiene il modello della Coreografia Globale, la mappa dei modelli BPMN dei robot (`robotModels`), l'elenco dei metadati estratti (`extractedMetadata`), la lista dei match convalidati (`validatedMatches`), le diagnostiche bloccanti (`diagnostics`) e le note comportamentali non bloccanti (`behavioralNotes`).
- **`InteractionMatch`**: Modella la singola connessione validata tra un produttore e un consumatore. Memorizza: `senderRobot`, `senderTaskId`, `receiverRobot`, `receiverTaskId`, `messageName`, `dataType`, `semanticDescription`, `generatedSendTaskId`, `generatedReceiveTaskId` e l'eventuale espressione condizionale (`branchConditionExpression`).
- **`InteractionPlan`**: Aggrega i match originati dal medesimo task. Definisce la modalità di fan-out (`activationMode`: `SEQUENCE`, `PARALLEL`, `EXCLUSIVE` o `UNRESOLVED`), il robot mittente, il task di ancoraggio e la lista dei match verso i destinatari (`receiverMatches`).
- **`DataNode`**: Rappresenta la specifica semantica I/O estratta da `semantics.json`. Contiene i tipi di dato dichiarati, i concetti ontologici opzionali, le descrizioni funzionali in linguaggio naturale e gli elenchi dei campi strutturati per produttore e ricevente (`producedFields`, `receiverFields`).
- **`RobotTaskMetadata`**: Rappresenta il contratto operativo di un task locale estratto dal BPMN. Include `robotId`, `taskId`, `taskName`, `producedDataType`, gli input richiesti (`requiredDataTypes`), le precondizioni (`preconditions`) e gli effetti di stato (`effects`) nella forma `chiave=valore`.
- **`SynthesisEvent`**: Modella un evento di log o progresso emesso durante la pipeline. Contiene timestamp ISO-8601, numero dello step, nome dello step, messaggio, robot coinvolti, esito booleano (`success`) e dettagli formali serializzabili in JSON.

## 6.3 Documentazione Esaustiva dei 26 Componenti Software

Di seguito viene documentata in dettaglio ogni singola classe Java presente nel progetto:

### 1. `Main.java`
- **Responsabilità**: Punto d'ingresso principale dell'applicazione da riga di comando.
- **Ruolo**: Interpreta gli argomenti della riga di comando (`--web`, `--port`, `--matcher`, `--api-key`, `--no-llm`, `--output-dir`), istanzia l'engine di sintesi o avvia il server web integrato.
- **Metodi Principali**:
  - `public static void main(String[] args)`: Esegue il dispatching verso la modalità Web Server (`SynthesisWebServer.startServer()`) o verso la pipeline batch CLI (`runCliPipeline()`).
  - `private static void runCliPipeline(...)`: Inizializza i repository, carica i modelli da `src/main/resources/` o dalla cartella specificata, avvia `engine.runSynthesisPipeline()` e stampa a video il report riassuntivo.
- **Interazioni**: Interagisce direttamente con `MultiRobotSynthesisEngine` e `SynthesisWebServer`.

### 2. `MultiRobotSynthesisEngine.java`
- **Responsabilità**: Coordinatore ed esecutore centrale dell'intera pipeline a 7 step.
- **Ruolo**: Cuore operativo del framework; orchestra sequenzialmente tutti i moduli analitici, semantici, comportamentali e di tessitura BPMN.
- **Metodi Principali**:
  - `public boolean runSynthesisPipeline(MissionContext context, File semanticsFile)`: Esegue l'intera pipeline di sintesi. Restituisce `true` se la sintesi viene completata con successo e senza diagnostiche bloccanti.
  - `public void addListener(SynthesisEventListener listener)`: Registra un osservatore per lo streaming live degli eventi.
  - `public void notifyEvent(SynthesisEvent event)`: Emette un evento verso tutti i listener registrati.
  - `private void executeStep1(...)` fino a `executeStep7(...)`: Implementano i singoli step operativi descritti nella Sezione 3.4.
- **Interazioni**: Collabora con tutte le classi dei package `analyzer`, `semantic`, `ai`, `planner`, `weaver`, `generator` e `persistence`.

### 3. `LocalProcessAnalyzer.java`
- **Responsabilità**: Parsing statico dei processi BPMN locali dei robot.
- **Ruolo**: Ispeziona il DOM XML di ciascun modello locale per estrarre la topologia dei task e i metadati I/O.
- **Metodi Principali**:
  - `public List<RobotTaskMetadata> extractMetadata(Map<String, BpmnModelInstance> robotModels)`: Cicla sui modelli, individua i nodi di tipo `Task` e ne ricava gli identificativi, i nomi e le proprietà estese Camunda relative ai tipi prodotti e richiesti.
- **Interazioni**: Popola la lista `extractedMetadata` in `MissionContext`.

### 4. `GlobalChoreographyAnalyzer.java`
- **Responsabilità**: Analisi del Choreography Diagram globale e verifica strutturale del grafo.
- **Ruolo**: Estrae i messaggi globali e valida che la coreografia sia priva di vicoli ciechi e rispetti il pattern dei cicli con timer.
- **Metodi Principali**:
  - `public List<MessageFlow> extractGlobalMessages(BpmnModelInstance globalChoreography)`: Restituisce l'elenco di tutti i flussi di messaggio dichiarati nella coreografia.
  - `public List<String> validateStructuralCoherence(BpmnModelInstance model)`: Verifica la presenza di Start/End events e la raggiungibilità di ogni nodo.
  - `private void validateCycles(...)`: Esegue la ricerca di componenti fortemente connesse (SCC) con l'algoritmo di Tarjan, garantendo che ogni ciclo contenga un `IntermediateCatchEvent` con Timer e un `ExclusiveGateway` di uscita.
- **Interazioni**: Utilizzata nello Step 2 della pipeline di `MultiRobotSynthesisEngine`.

### 5. `SyntacticCompatibilityChecker.java`
- **Responsabilità**: Verifica della compatibilità sintattica forte, debole e strutturale.
- **Ruolo**: Implementa i controlli di tipo previsti dalla Sezione 5.4 della tesi.
- **Metodi Principali**:
  - `public boolean isTypeCompatible(String prodType, String reqType, String mode)`: Valuta se il tipo prodotto soddisfa il tipo richiesto secondo la modalità `STRONG` o `WEAK`.
  - `public boolean isStructurallyCompatible(Collection<String> prodFields, Collection<String> reqFields)`: Verifica che tutti i campi richiesti dal consumatore siano presenti nel produttore.
  - `public boolean isStructurallyCompatibleTypes(String prodType, String reqType)`: Ricava i campi definiti nell'ontologia e ne verifica la copertura strutturale.
- **Interazioni**: Dipende da `OntologyManager`.

### 6. `OntologyManager.java`
- **Responsabilità**: Gestore della tassonomia locale e delle gerarchie ontologiche.
- **Ruolo**: Carica `taxonomy.json`, indicizza le relazioni di sottotipo e i campi strutturati, esponendo i metodi di sussunzione.
- **Metodi Principali**:
  - `public boolean isStronglyCompatible(String prod, String req)`: Verifica l'uguaglianza case-insensitive dei tipi.
  - `public boolean isWeaklyCompatible(String prod, String req)`: Verifica se `prod` è uguale o sottotipo di `req` risalendo l'albero di ereditarietà.
  - `public boolean isSemanticConceptCompatible(String prodConcept, String reqConcept)`: Verifica la sussunzione ontologica tra due concetti semantici ($A \sqsubseteq B$).
  - `public Set<String> getFieldsForType(String type)`: Restituisce i campi strutturati associati a un tipo complesso.
- **Interazioni**: Interagisce con `SyntacticCompatibilityChecker` e `AISemanticMatcher`.

### 7. `AISemanticMatcher.java`
- **Responsabilità**: Motore decisionale di compatibilità semantica multi-modalità.
- **Ruolo**: Unifica la validazione ontologica deterministica con le capacità di ragionamento del Large Language Model.
- **Metodi Principali**:
  - `public boolean isSemanticMatch(String pType, String pDesc, String rType, String rDesc, String pConcept, String rConcept)`: Esegue il controllo semantico secondo la modalità impostata (`HYBRID`, `ONTOLOGY_STRICT`, `LLM_REASONER`). In modalità `HYBRID`, interroga l'ontologia se i concetti sono definiti (senza fallback LLM in caso di incompatibilità accertata); attiva il fallback LLM solo se i concetti mancano.
  - `public void setMode(String mode)`: Configura la modalità operativa.
  - `public String getLastExplanation()`: Restituisce la motivazione tecnica del giudizio semantico.
  - `public String getLastProvider()`: Restituisce il provider che ha risposto (`GEMINI`, `OPENAI`, `LOCAL`).
- **Interazioni**: Collabora strettamente con `OntologyManager` e `LLMClient`.

### 8. `LLMClient.java`
- **Responsabilità**: Gestione della comunicazione HTTP verso i provider di intelligenza artificiale.
- **Ruolo**: Implementa la cascata di fallback: Google Gemini Flash $\rightarrow$ OpenAI GPT-4o-mini $\rightarrow$ Local Semantic Reasoner.
- **Metodi Principali**:
  - `public boolean askAIForCompatibility(String pType, String pDesc, String rType, String rDesc)`: Interroga i provider disponibili in ordine di priorità fino a ottenere una decisione valida.
  - `private String discoverWorkingGeminiEndpoint(String apiKey)`: Interroga le API di Google per identificare dinamicamente il modello Flash disponibile e funzionante.
  - `private boolean evaluateLocalSemanticReasoning(...)`: Esegue il ragionamento semantico locale deterministico basato su parole chiave e regole del dominio agricolo.
  - `private String resolveApiKey(...)`: Cerca la chiave API tra parametri espliciti, variabili d'ambiente e file `gemini.key`.
- **Interazioni**: Utilizzato da `AISemanticMatcher`.

### 9. `BehavioralCompatibilityTester.java`
- **Responsabilità**: Analisi di correttezza comportamentale formale (Sezione 5.6 della tesi).
- **Ruolo**: Convalida i quattro livelli di vincoli: Livello A (raggiungibilità), Livello B (terminazione), Livello C (input), Livello D (percorsi di stato).
- **Metodi Principali**:
  - `public Assessment assess(BpmnModelInstance model, RobotTaskMetadata consumer, String prodType, OntologyManager ontology)`: Esegue la valutazione completa dei livelli A, B e C.
  - `public ContinuationPaths prefixPaths(...)`: Enumera tutti i cammini finiti dallo StartEvent al task target.
  - `public ContinuationPaths continuationPaths(...)`: Enumera tutti i cammini di continuazione dal task consumatore fino agli EndEvent.
- **Interazioni**: Utilizza `OntologyManager` e fornisce cammini a `BehavioralStateSimulator`.

### 10. `BehavioralStateSimulator.java`
- **Responsabilità**: Simulatore di stato deterministico per contratti espliciti precondizione/effetto.
- **Ruolo**: Implementa il Livello D del testing comportamentale verificando la tenuta dello stato lungo i percorsi di processo.
- **Metodi Principali**:
  - `public Result simulate(List<RobotTaskMetadata> path, Map<String, String> initialState)`: Esegue simbolicamente la sequenza di task; per ogni task controlla le precondizioni `chiave=valore` e applica gli effetti nello stato transiente. Ritorna `PASS`, `FAIL` o `UNRESOLVED`.
- **Interazioni**: Invocato da `MultiRobotSynthesisEngine` a valle di `BehavioralCompatibilityTester`.

### 11. `InteractionPlanner.java`
- **Responsabilità**: Raggruppamento e pianificazione delle interazioni (Sezione 5.7 della tesi).
- **Ruolo**: Risolve la cardinalità 1 a 1 e 1 a N deducendo se l'attivazione debba avvenire in sequenza, in parallelo o con diramazione esclusiva.
- **Metodi Principali**:
  - `public List<InteractionPlan> buildInteractionPlans(MissionContext context)`: Raggruppa i match validati per robot mittente e task di ancoraggio, analizza il grafo globale e genera i piani d'interazione con modalità `SEQUENCE`, `PARALLEL`, `EXCLUSIVE` o `UNRESOLVED`.
- **Interazioni**: Riceve i dati da `MissionContext` e prepara i piani per `BpmnFlowWeaver`.

### 12. `BpmnFlowWeaver.java`
- **Responsabilità**: Tessitura ed integrazione strutturale delle interazioni nei modelli BPMN locali.
- **Ruolo**: Esegue l'operazione 6 della metodologia, inserendo fisicamente SendTask e ReceiveTask e gestendo il flusso di controllo.
- **Metodi Principali**:
  - `public void weaveInteractions(MissionContext context)`: Cicla sui piani d'interazione, inietta le attività nei modelli BPMN dei robot mittenti e riceventi, ricabla i Sequence Flow e applica l'offset grafico.
  - `private void projectAvailabilityRetryControl(MissionContext context)`: Identifica il pattern di retry della disponibilità nella coreografia globale e lo proietta nel modello locale del Tagliatore, inserendo l'ExclusiveGateway e l'evento timer a 10s.
- **Interazioni**: Collabora attivamente con `BpmnAutoLayout` e `BpmndiGenerator`.

### 13. `BpmnAutoLayout.java`
- **Responsabilità**: Calcolo e aggiornamento automatico delle coordinate visuali BPMNDI.
- **Ruolo**: Previene sovrapposizioni grafiche quando nuove attività vengono introdotte in un modello preesistente.
- **Metodi Principali**:
  - `public void shiftRightRecursive(BpmnModelInstance model, FlowNode startNode, double offsetAmount)`: Esegue una visita BFS nel grafo BPMN spostando verso destra (incrementando la coordinata X di forme e waypoint) tutti i nodi a valle del punto di inserimento.
  - `public SequenceFlow connect(BpmnModelInstance model, BpmnPlane plane, FlowNode from, FlowNode to, String name)`: Crea un nuovo flusso di sequenza e ne genera il corrispondente BpmnEdge visivo.
- **Interazioni**: Invocata durante la tessitura da `BpmnFlowWeaver`.

### 14. `BpmndiGenerator.java`
- **Responsabilità**: Generazione a basso livello di elementi BPMNDI (Diagram, Plane, Shape, Edge).
- **Ruolo**: Funge da utility grafica per creare rettangoli di delimitazione (*Bounds*) e waypoint conformi allo standard OMG.
- **Metodi Principali**:
  - `public static BpmnShape createShape(BpmnModelInstance model, BpmnPlane plane, BaseElement element, double x, double y, double w, double h)`: Genera un'istanza di `BpmnShape` con relative coordinate grafiche.
- **Interazioni**: Utilizzato da `BpmnAutoLayout`, `UnifiedDiagramBuilder` e `BpmnFlowWeaver`.

### 15. `UnifiedDiagramBuilder.java`
- **Responsabilità**: Sintesi del Diagramma di Collaborazione Unificato multi-pool.
- **Ruolo**: Crea il file `UnifiedMission.bpmn` in cui ogni robot e la coreografia possiedono una propria corsia (*Pool*), collegate da flussi di messaggi inter-pool.
- **Metodi Principali**:
  - `public BpmnModelInstance build(MissionContext context)`: Istanzia una `Collaboration`, clona i processi dei singoli robot assegnando loro coordinate verticali impilate, crea i partecipanti e traccia i `MessageFlow` con waypoint BPMNDI tra i task di invio e ricezione.
- **Interazioni**: Legge i modelli migliorati da `MissionContext` e produce il modello per `ModelRepository`.

### 16. `DrawioExporter.java`
- **Responsabilità**: Esportazione visuale dei diagrammi nel formato XML standard di Diagrams.net (Draw.io).
- **Ruolo**: Genera file grafici altamente leggibili con stili visuali personalizzati e Choreography Tasks a tre bande.
- **Metodi Principali**:
  - `public void exportAllToDrawio(MissionContext context, String outputDir)`: Genera i file `.drawio` per ciascun robot e per l'intera missione unificata (`UnifiedMission_Visual_Thesis.drawio`).
- **Interazioni**: Invocato nello Step 7 della pipeline.

### 17. `ModelRepository.java`
- **Responsabilità**: Persistenza su file system dei modelli BPMN.
- **Ruolo**: Incapsula le operazioni di I/O su disco sfruttando l'API Camunda BPMN, garantendo che i modelli generati siano sintatticamente validi tramite ri-parsing di convalida.
- **Metodi Principali**:
  - `public BpmnModelInstance loadModel(File file)`: Carica e valida un file BPMN da disco.
  - `public void saveModel(BpmnModelInstance model, String fileName)`: Salva il modello in `target/output/` e ne verifica l'integrità rileggendolo.
  - `public void saveUnifiedDiagram(MissionContext context)`: Invoca `UnifiedDiagramBuilder` e scrive su disco `UnifiedMission.bpmn`.
- **Interazioni**: Utilizzato in ingresso e in uscita da `MultiRobotSynthesisEngine`.

### 18. `UserInputManager.java`
- **Responsabilità**: Caricamento e validazione strutturale delle specifiche semantiche utente.
- **Ruolo**: Effettua il parsing di `semantics.json`, convertendo il JSON nell'elenco di oggetti `DataNode` tipizzati.
- **Metodi Principali**:
  - `public List<DataNode> loadSemanticSpecifications(File semanticsFile)`: Valida la presenza di tutti i campi obbligatori del produttore ed estrae le specifiche I/O.
- **Interazioni**: Fornisce i dati di input a `MultiRobotSynthesisEngine`.

### 19. `SynthesisWebServer.java`
- **Responsabilità**: Server HTTP e interfaccia REST per lo studio interattivo.
- **Ruolo**: Avvia un'istanza di Spark-Java sulla porta 18080 (o configurata), gestendo gli endpoint API e lo streaming Server-Sent Events (SSE).
- **Metodi Principali**:
  - `public static void startServer(int port)`: Inizializza le route REST, abilita il CORS e serve i file statici della cartella `web/`.
  - `public static void stopServer()`: Arresta il server web e rilascia la porta.
- **Interazioni**: Interfaccia tra il frontend web (`index.html`) e `MultiRobotSynthesisEngine`.

### 20. `MissionContext.java` (Descritto nella Sezione 6.2)
### 21. `InteractionMatch.java` (Descritto nella Sezione 6.2)
### 22. `InteractionPlan.java` (Descritto nella Sezione 6.2)
### 23. `DataNode.java` (Descritto nella Sezione 6.2)
### 24. `RobotTaskMetadata.java` (Descritto nella Sezione 6.2)
### 25. `SynthesisEvent.java` (Descritto nella Sezione 6.2)
### 26. `SynthesisEventListener.java`
- **Responsabilità**: Interfaccia funzionale per il pattern Observer.
- **Ruolo**: Consente a componenti esterni (es. endpoint SSE del Web Server o console logger) di ricevere notifiche asincrone durante l'avanzamento della pipeline.
- **Metodi**:
  - `void onSynthesisEvent(SynthesisEvent event)`: Invocato a ogni traguardo o diagnostica emessa dal motore.


# Capitolo 7 — Input, Risorse di Configurazione e Artefatti Generati

## 7.1 Specifiche JSON: `semantics.json`, `taxonomy.json`, `behavioral-state.json`

Il funzionamento del motore di sintesi è guidato da tre file di configurazione dichiarativi collocati in `src/main/resources/`:

### 1. `semantics.json` (Specifiche di Interazione I/O)
Costituisce l'interfaccia con cui il progettista dichiara quali dati sono emessi dai robot e come essi mappano sui messaggi della coreografia. Ogni elemento dell'array definisce:
- `robotId`: Identificativo del robot produttore (es. `"Drone"`);
- `taskId`: ID del task BPMN che produce il dato (es. `"Task_Coordinate"`);
- `messageName`: Nome del messaggio associato nella coreografia (es. `"weedPositionMessage"`);
- `dataType`: Tipo di dato formale (es. `"CoordinateZonaInteresse"`);
- `semanticDescription`: Descrizione discorsiva del significato operativo;
- `semanticConcept`: Concetto ontologico associato (opzionale, es. `"AgriculturalWorkZone"`);
- `syntacticCompatibility`: Modalità di controllo sintattico (`"STRONG"` o `"WEAK"`);
- `producedFields`: Array opzionale di campi strutturati generati dal task.

### 2. `taxonomy.json` (Tassonomia e Ontologia di Dominio)
Rappresenta la base di conoscenza formale del dominio applicativo. È strutturato in due sezioni:
- `dataTypes`: Definisce le relazioni di generalizzazione/specializzazione tra tipi concreti (es. `CoordinateZonaInteresse` estende `AreaLavoro`, `CoordinateTrattoreVicino` estende `CoordinateGPS`), corredate dai rispettivi campi attributo;
- `semanticConcepts`: Definisce l'albero di ereditarietà tra i concetti astratti di missione (es. `AgriculturalWorkZone` sussume `TargetLocation`).

### 3. `behavioral-state.json` (Stato Iniziale dell'Automa LTS)
Definisce le condizioni al contorno operative all'avvio della missione mediante coppie `chiave: valore` (es. `{"campo_libero": "false", "batteria_drone": "carica", "tagliatore_disponibile": "true"}`). Tali coppie costituiscono lo stato di partenza consumato da `BehavioralStateSimulator`.

## 7.2 Modelli BPMN Locali di Input

I modelli di partenza — presenti in `src/main/resources/models/` — sono file conformi allo standard OMG BPMN 2.0:
- `GlobalChoreography.bpmn`: Modella la sequenza globale dei Choreography Tasks e i gateway decisionali;
- `Drone.bpmn`, `Tagliatore.bpmn`, `Scavatore.bpmn`, `Seminatore.bpmn`: Modellano i comportamenti elementari dei robot composti esclusivamente da `StartEvent`, `ServiceTask`, `SequenceFlow` ed `EndEvent`.

## 7.3 Artefatti Prodotti dal Motore di Sintesi

Al completamento della sintesi, il framework genera nella cartella `target/output/` i seguenti artefatti verificabili:

```
target/output/
├── Drone_enhanced.bpmn         [BPMN locale con SendTask iniettati]
├── Tagliatore_enhanced.bpmn    [BPMN locale con Receive/Send e Loop Retry proiettato]
├── Scavatore_enhanced.bpmn     [BPMN locale con Receive/Send di sincronizzazione]
├── Seminatore_enhanced.bpmn    [BPMN locale con ReceiveTask e notifica di chiusura]
├── UnifiedMission.bpmn         [Diagramma di Collaborazione Unificato a 4 Pool]
├── Drone_Visual_Thesis.drawio  [File grafico Diagrams.net per il Drone]
├── Tagliatore_Visual_Thesis.drawio
├── Scavatore_Visual_Thesis.drawio
├── Seminatore_Visual_Thesis.drawio
└── UnifiedMission_Visual_Thesis.drawio [Diagramma unificato in formato Diagrams.net]
```


# Capitolo 8 — Modalità Operative e Studio Web Interattivo

## 8.1 Esecuzione da Riga di Comando (CLI)

Il framework può essere eseguito in modalità headless/batch mediante invocazione Maven o comando Java diretto:

```bash
mvn exec:java -Dexec.args="--matcher HYBRID"
```

### Opzioni e Flag Supportati:
- `--web`: Avvia il server web interattivo anziché eseguire la pipeline batch;
- `--port <numero>`: Imposta la porta di ascolto HTTP (predefinita: `18080`);
- `--matcher <HYBRID|ONTOLOGY_STRICT|LLM_REASONER>`: Seleziona la modalità di validazione semantica;
- `--api-key <chiave>`: Inietta esplicitamente la chiave API per i servizi LLM;
- `--no-llm`: Forza la disabilitazione delle chiamate di rete ai modelli linguistici;
- `--output-dir <cartella>`: Sovrascrive la directory di emissione degli artefatti generati.

## 8.2 Web Server REST e Server-Sent Events (SSE)

La classe `SynthesisWebServer.java` implementa un'infrastruttura backend reattiva basata su Spark-Java. Il server espone le seguenti API REST:

```
+---------------------------------------------------------------------------------------+
| Metodo | Endpoint                     | Descrizione Funzionale                        |
+--------+------------------------------+-----------------------------------------------+
| GET    | `/api/status`                | Restituisce lo stato del server e la porta    |
| GET    | `/api/models`                | Elenco dei file BPMN e JSON disponibili       |
| GET    | `/api/models/content`        | Carica il contenuto testuale di un file       |
| POST   | `/api/models/save`           | Salva modifiche al contenuto XML/JSON         |
| POST   | `/api/synthesis/run`         | Avvia l'esecuzione della pipeline di sintesi   |
| GET    | `/api/synthesis/stream`      | Canale SSE (text/event-stream) in tempo reale |
| GET    | `/api/output/files`          | Elenco dei file generati in `target/output`   |
| GET    | `/api/output/content`        | Recupera un artefatto BPMN o Draw.io generato |
+---------------------------------------------------------------------------------------+
```

Durante l'esecuzione, ogni fase di controllo sintattico, semantico e comportamentale viene serializzata istantaneamente in un oggetto `SynthesisEvent` e inviata al client browser tramite il canale SSE aperto su `/api/synthesis/stream`.

## 8.3 Interfaccia Grafica Web (`index.html`) e BPMN Canvas

L'interfaccia web integrata — accessibile all'indirizzo `http://localhost:18080` — offre una dashboard ingegneristica avanzata articolata in quattro pannelli principali:
1. **Pannello di Configurazione e Controllo**: Consente di selezionare il matcher desiderato, inserire la chiave API, impostare la modalità batch e attivare la sintesi con un clic sul pulsante *Start Synthesis*;
2. **Console Live degli Eventi**: Visualizza il feed degli eventi ricevuti in streaming SSE con badge colorati per step (INFO, CHECK PASS, WARNING, ERROR);
3. **BPMN.io Visualizer**: Canvas interattivo basato sulla libreria ufficiale `bpmn-js` (versione 17.11.1), che renderizza graficamente qualsiasi diagramma BPMN di input o di output con supporto a zoom, pan e ispezione dei singoli task;
4. **Albero dei Modelli e dei Risultati**: Consente di passare istantaneamente tra i modelli locali dei quattro robot, la coreografia globale e il diagramma unificato.

## 8.4 Editor Modale In-Browser con Formattazione e Ricaricamento Live

Per agevolare il collaudo e l'ispezione empirica da parte dei ricercatori, l'interfaccia utente include un **editor integrato per i file di progetto** accessibile tramite il pulsante interattivo `📝` collocato accanto a ciascun modello nella lista dei robot:

```
  +-------------------------------------------------------------------------+
  |  Modifica Modello: Drone.bpmn                      [Formatta XML] [ x ]  |
  +-------------------------------------------------------------------------+
  |  <?xml version="1.0" encoding="UTF-8"?>                                 |
  |  <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524... |
  |    <bpmn:process id="Process_Drone" isExecutable="false">               |
  |      <bpmn:startEvent id="StartEvent_1"/>                               |
  |      ...                                                                |
  +-------------------------------------------------------------------------+
  |  [Salva Modifiche]                                                      |
  +-------------------------------------------------------------------------+
```

### Funzionalità Implementate nel Frontend:
- **`formatModalContent()`**: Riconosce dinamicamente se il file aperto nel modale è di tipo XML (BPMN) o JSON (`semantics.json`), invocando il formattatore appropriato;
- **`formatXml()`**: Pretty-printer client-side basato su `DOMParser` e indentazione gerarchica ricorsiva a 2 spazi;
- **`editCurrentActiveModel()`**: Apre immediatamente l'editor per il modello attualmente visualizzato nel canvas;
- **`saveModalFileContent()`**: Invia il contenuto modificato all'endpoint POST `/api/models/save`. Al completamento con successo del salvataggio su disco, se il file è un diagramma `.bpmn`, invoca automaticamente la funzione `loadBpmnFile()` per **ricaricare e aggiornare istantaneamente il canvas grafico BPMN.io**, offrendo un ciclo di feedback visivo immediato.


# Capitolo 9 — Verifica Sperimentale, Suite di Test e Copertura

## 9.1 Report di Esecuzione dei Test JUnit 5 (63/63 PASS)

La correttezza del framework è garantita da una suite di collaudo automatizzata basata su **JUnit 5 (Jupiter Platform)**. 

L'esecuzione del comando `mvn test` certifica il seguente risultato ufficiale:

```
-------------------------------------------------------
 T E S T S
-------------------------------------------------------
Running it.univaq.disim.mrs.synthesis.ai.LLMClientTest
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
Running it.univaq.disim.mrs.synthesis.analyzer.BehavioralCompatibilityTesterTest
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
Running it.univaq.disim.mrs.synthesis.analyzer.BehavioralStateSimulatorTest
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
Running it.univaq.disim.mrs.synthesis.core.MultiRobotSynthesisEngineTest
Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
Running it.univaq.disim.mrs.synthesis.input.UserInputManagerCompatibilityTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Running it.univaq.disim.mrs.synthesis.MainInputDiscoveryTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
Running it.univaq.disim.mrs.synthesis.semantic.OntologyManagerTest
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
Running it.univaq.disim.mrs.synthesis.semantic.SyntacticCompatibilityCheckerTest
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
Running it.univaq.disim.mrs.synthesis.web.SynthesisWebServerTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0

Results:
Tests run: 63, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (Total time: ~26 s)
```

## 9.2 Dettaglio dei Casi di Test per Singola Classe

I 63 test unitari e di integrazione verificano puntualmente ogni invariante metodologico:

1. **`LLMClientTest` (5 test)**: Valida la risposta dell'LLM su concetti compatibili del dominio agricolo, il rigetto di descrizioni incompatibili (es. meteo vs batteria trattore), la gestione del fallback locale euristico e la resilienza a chiavi API nulle o non valide.
2. **`BehavioralCompatibilityTesterTest` (5 test)**: Convalida la raggiungibilità del task ricevente dallo StartEvent (Livello A), l'intercettazione di vicoli ciechi verso gli EndEvent (Livello B), il rifiuto di nodi non collegati e l'enumerazione corretta dei cammini finiti.
3. **`BehavioralStateSimulatorTest` (10 test)**: Valida la propagazione di stati con contratti `chiave=valore`, il successo su percorsi consistenti, il fallimento immediato su precondizioni violate (es. `campo_libero=false` quando richiesto `true`), il rigetto di stati iniziali contraddittori e la gestione di percorsi vuoti.
4. **`MultiRobotSynthesisEngineTest` (20 test)**: Convalida l'intero ciclo end-to-end della pipeline a 7 step, la corretta iniezione dei 6 messaggi nello scenario agricolo, la generazione dei file BPMN conformi allo schema XML Camunda, l'accuratezza del layout BPMNDI, la gestione dell'estensione 1 a N parallela e la corretta propagazione degli eventi Observer.
5. **`UserInputManagerCompatibilityTest` (3 test)**: Valida il parsing di `semantics.json`, il rigetto di file con campi produttore mancanti o malformati e la compatibilità con formati estesi.
6. **`MainInputDiscoveryTest` (1 test)**: Valida la capacità della classe `Main` di localizzare autonomamente le risorse e i modelli BPMN nella struttura delle directory di progetto.
7. **`OntologyManagerTest` (9 test)**: Valida il caricamento di `taxonomy.json`, la verifica della compatibilità STRONG tra tipi identici, la compatibilità WEAK per ereditarietà, la sussunzione concettuale transittiva ($A \sqsubseteq B \sqsubseteq C$) e il parsing dei campi strutturati.
8. **`SyntacticCompatibilityCheckerTest` (7 test)**: Valida la distinzione rigorosa tra modalità STRONG e WEAK, la compatibilità strutturale su insiemi di campi identici o sovrabbondanti e il fallimento in caso di omissione di campi obbligatori.
9. **`SynthesisWebServerTest` (3 test)**: Valida l'avvio e arresto pulito del server Spark-Java su porta dedicata, la rispondenza dell'endpoint `/api/status` e l'erogazione dei modelli BPMN statici.

## 9.3 Scenari Convalidati e Aspetti Non Testati

### Comportamenti Formalmente Verificati:
- Correttezza sintattica XML e BPMNDI di tutti gli output generati;
- Terminazione garantita e assenza di loop infiniti nel weaving;
- Preservazione della semantica originale dei processi locali;
- Proiezione coerente del timer di retry della disponibilità nel Tagliatore.

### Aspetti Non Testati (Limitazioni Attuali):
- **Esecuzione Fisica su Robot Reali**: Il framework valida e sintetizza i modelli logici BPMN, ma non include driver ROS 2 o interfacce di attuazione hardware per il deployment fisico in campo;
- **Perdita Pacchetti di Rete a Basso Livello**: L'ambiente assume comunicazioni a livello di trasporto affidabili (es. TCP/IP o code message-broker con ack); guasti radio fisici non sono simulati a runtime.


# Capitolo 10 — Matrice di Tracciabilità Tesi vs Implementazione

La tabella seguente fornisce la mappatura puntuale tra i requisiti teorici formalizzati nella tesi magistrale di Giovanni Spaziani e la loro realizzazione concreta nel codice sorgente:

```
+------------------------------------------------------------------------------------------------------------------------------------+
| Requisito / Fase Tesi  | Sezione Tesi | Classe e Metodo Software             | Comportamento Effettivo nel Codice        | Stato    |
+------------------------+--------------+--------------------------------------+-------------------------------------------+----------+
| Analisi Task Interni   | Sez. 5.1,    | `LocalProcessAnalyzer`               | Esegue il parsing BPMN, estraendo nomi,  | FEDELE   |
| e Dati Prodotti        | Op. 1        | `extractMetadata()`                  | ID, tipi prodotti e input richiesti.      |          |
+------------------------+--------------+--------------------------------------+-------------------------------------------+----------+
| Analisi Flussi         | Sez. 5.1,    | `GlobalChoreographyAnalyzer`         | Estrae i nodi `MessageFlow` dalla         | FEDELE   |
| Coreografia Globale    | Op. 2        | `extractGlobalMessages()`            | coreografia e mappa mittenti/riceventi.   |          |
+------------------------+--------------+--------------------------------------+-------------------------------------------+----------+
| Rilevamento Cicli      | Sez. 5.10    | `GlobalChoreographyAnalyzer`         | Algoritmo Tarjan SCC: valida cicli solo   | ESTENS.  |
| Controllati con Timer  |              | `validateCycles()`                   | se contengono TimerEvent e guardia di exit| ROBUSTA  |
+------------------------+--------------+--------------------------------------+-------------------------------------------+----------+
| Compatibilità          | Sez. 5.4     | `SyntacticCompatibilityChecker`      | Supporta modalità STRONG (uguaglianza) e  | FEDELE   |
| Sintattica STRONG/WEAK |              | `isTypeCompatible()`                 | WEAK (sottotipo tassonomico A ⊑ B).       |          |
+------------------------+--------------+--------------------------------------+-------------------------------------------+----------+
| Compatibilità          | Sez. 5.4     | `SyntacticCompatibilityChecker`      | Confronta gli insiemi di campi: richiede  | FEDELE   |
| Strutturale Campi      |              | `isStructurallyCompatible()`         | che il produttore copra i campi attesi.   |          |
+------------------------+--------------+--------------------------------------+-------------------------------------------+----------+
| Ontologia e            | Sez. 5.5     | `OntologyManager`                    | Carica `taxonomy.json`, calcola la        | FEDELE   |
| Subsumption Concettuale|              | `isSemanticConceptCompatible()`      | chiusura transitiva delle relazioni ⊑.   |          |
+------------------------+--------------+--------------------------------------+-------------------------------------------+----------+
| Ragionamento Semantico | Sez. 5.4     | `AISemanticMatcher`                  | Modalità HYBRID: ontologia prioritaria;   | ESTENS.  |
| Ibrido con LLM         | e 5.5        | `isSemanticMatch()`                  | fallback LLM solo per concetti assenti.   | AI-BASED |
+------------------------+--------------+--------------------------------------+-------------------------------------------+----------+
| Fallback e Auto-Disc.  | N/D          | `LLMClient`                          | Cascata Gemini Flash -> GPT-4o-mini ->   | SCELTA   |
| Modelli Linguistici    | (Ingegner.)  | `askAIForCompatibility()`            | Local Semantic Reasoner euristico.        | IMPLEM.  |
+------------------------+--------------+--------------------------------------+-------------------------------------------+----------+
| Testing Comportamentale| Sez. 5.6     | `BehavioralCompatibilityTester`      | BFS per raggiungibilità Start (Liv. A);  | FEDELE   |
| Raggiungib./Dead-end   |              | `assess()`                           | DFS per terminazione senza dead-end (B).  |          |
+------------------------+--------------+--------------------------------------+-------------------------------------------+----------+
| Simulazione di Stato   | Sez. 5.6     | `BehavioralStateSimulator`           | Simula contratti `chiave=valore` lungo i  | FEDELE   |
| con Contratti LTS      |              | `simulate()`                         | cammini, accumulando effetti e verificando| (Liv. D) |
+------------------------+--------------+--------------------------------------+-------------------------------------------+----------+
| Disambiguazione        | Sez. 5.7     | `InteractionPlanner`                 | Raggruppa i match e deduce l'attivazione: | FEDELE   |
| Destinatari (1->1/1->N)|              | `buildInteractionPlans()`            | SEQUENCE, PARALLEL, EXCLUSIVE o UNRESOLVED|          |
+------------------------+--------------+--------------------------------------+-------------------------------------------+----------+
| Integrazione BPMN      | Sez. 5.1,    | `BpmnFlowWeaver`                     | Inietta Send/ReceiveTasks, ricabla i      | FEDELE   |
| (Weaving dei Flussi)   | Op. 6        | `weaveInteractions()`                | SequenceFlows e calcola l'auto-layout.    |          |
+------------------------+--------------+--------------------------------------+-------------------------------------------+----------+
| Proiezione Locale del  | Sez. 5.10    | `BpmnFlowWeaver`                     | Proietta l'ExclusiveGateway e il timer 10s| FEDELE   |
| Loop di Retry          | e 5.11.2     | `projectAvailabilityRetryControl()`  | nel processo locale del Tagliatore.       |          |
+------------------------+--------------+--------------------------------------+-------------------------------------------+----------+
| Unified Collaboration  | Sez. 5.13    | `UnifiedDiagramBuilder`              | Genera `UnifiedMission.bpmn` con 4 pool   | FEDELE   |
| Diagram Multi-Pool     |              | `build()`                            | allineate e flussi `MessageFlow` inter-pool|         |
+------------------------+--------------+--------------------------------------+-------------------------------------------+----------+
| Esportazione Visuale   | N/D          | `DrawioExporter`                     | Produce diagrammi XML compatibili con     | ESTENS.  |
| Formato Diagrams.net   | (Ingegner.)  | `exportAllToDrawio()`                | Diagrams.net con task a tre bande.        | VISUALE  |
+------------------------+--------------+--------------------------------------+-------------------------------------------+----------+
| Studio Interattivo Web | N/D          | `SynthesisWebServer`                 | Dashboard web con BPMN.io, live streaming | ESTENS.  |
| e Editor In-Browser    | (Ingegner.)  | `startServer()` + `index.html`       | SSE, editor modale e formattatore XML.    | TOOLING  |
+------------------------------------------------------------------------------------------------------------------------------------+
```


# Capitolo 11 — Tecnologie Utilizzate e Stack Software

Il framework integra uno stack tecnologico solido e conforme agli standard dell'ingegneria del software:

1. **Linguaggio di Programmazione**:
   - **Java (versioni supportate: 11 LTS, 17 LTS, 21 LTS)**: garantisce portabilità multipiattaforma, forte tipizzazione e prestazioni stabili nell'elaborazione del grafo.
2. **Manipolazione e Convalida BPMN 2.0**:
   - **Camunda BPMN Model API (v7.20.0)**: libreria industriale di riferimento per la lettura, manipolazione strutturale dell'albero DOM e serializzazione XML conforme alle specifiche OMG BPMN 2.0.
3. **Serializzazione e Parsing Dati**:
   - **Jackson Databind (v2.15.2)**: parsing ad alte prestazioni delle specifiche JSON (`semantics.json`, `taxonomy.json`, `behavioral-state.json`) e serializzazione degli eventi SSE.
4. **Server Web e Connettività REST**:
   - **Spark-Java (v2.9.4)**: micro-framework embedded rapido e leggero per la pubblicazione degli endpoint HTTP REST e la gestione dei flussi continui Server-Sent Events.
5. **Intelligenza Artificiale Generativa**:
   - **Google Gemini Generative Language API (v1beta / v1)**: modelli Foundation per il ragionamento semantico avanzato in linguaggio naturale con auto-discovery dinamica.
   - **OpenAI REST API (v1)**: provider secondario per ridondanza e continuità di servizio.
6. **Frontend e Visualizzazione Grafica**:
   - **bpmn-js (v17.11.1)**: visualizzatore e renderer BPMN ufficiale client-side basato su Web standards (SVG/HTML5/JavaScript).
   - **Draw.io / diagrams.net XML**: specifica di interscambio per diagrammi grafici ingegneristici.
7. **Build Automation e Testing**:
   - **Apache Maven (v3.9+)**: gestione delle dipendenze, compilazione e packaging del modulo jar.
   - **JUnit 5 (Jupiter Platform)**: framework per il test-driven development e la verifica di regressione.

---

# Capitolo 12 — Conclusioni, Discrepanze e Sviluppi Futuri

## 12.1 Valutazione dei Risultati Raggiunti

Il progetto **MRS Synthesis Engine** dimostra con successo la fattibilità scientifica e ingegneristica della sintesi automatica di comportamenti collaborativi per sistemi multi-robot eterogenei.

Partendo dai soli modelli isolati delle operazioni basilari e da una specifica di coreografia globale, il motore è in grado di:
- Ricostruire l'intero grafo delle interazioni distribuite senza l'ausilio di un controllore master;
- Verificare rigorosamente le compatibilità sintattiche, semantiche e i contratti di stato comportamentali;
- Proiettare costrutti di controllo avanzati (quali i loop temporizzati di retry della disponibilità);
- Generare modelli BPMN 2.0 standard immediatamente eseguibili o ispezionabili in Camunda Modeler e Draw.io;
- Offrire ai ricercatori un ambiente visuale moderno dotato di Web Studio reattivo.

## 12.2 Analisi Trasparente delle Discrepanze

In ossequio al principio di rigore accademico, si evidenziano le seguenti discrepanze e particolarità tra il testo della tesi e l'implementazione:

1. **Ripartizione delle Fasi (3 Fasi Formali vs 7 Step Software)**: La tesi organizza concettualmente il metodo in 3 macro-fasi e 6 operazioni. L'implementazione Java scompone il flusso in 7 passaggi modulari. Come ampiamente dimostrato nel Capitolo 3, si tratta di una scelta puramente architetturale che preserva fedelmente tutte le 6 operazioni formali.
2. **Formalismo Ontologico (OWL DL Completo vs Tassonomia JSON)**: La tesi cita formalismi basati su OWL e Description Logics. Il framework implementativo adotta una rappresentazione JSON strutturata (`taxonomy.json`), che modella efficacemente alberi di sussunzione e campi, ma non include un reasoner OWL-DL completo (come Pellet o HermiT). L'integrazione del client LLM colma tale semplificazione consentendo ragionamenti semantici flessibili.
3. **Limiti del Local Reasoner Euristico**: Il modulo `evaluateLocalSemanticReasoning` di `LLMClient` è calibrato su parole chiave relative al dominio agricolo italiano. In scenari completamente differenti (es. esplorazione spaziale o robotica chirurgica), in assenza di chiavi API cloud, il fallback locale rifiuterà i match poiché disgiunti.

## 12.3 Linee di Ricerca Future

I risultati conseguiti aprono promettenti direttrici di sviluppo futuro:
1. **Integrazione con ROS 2 e Behavior Trees**: Traduzione automatica dei diagrammi BPMN enhanced in alberi di comportamento (*BehaviorTree.CPP*) e nodi ROS 2 per il deployment immediato sui robot fisici.
2. **Supporto a Reasoner OWL Completi**: Integrazione della libreria Apache Jena o OWL API per supportare ontologie formali complesse espresse in standard W3C OWL/RDF.
3. **Modelli LLM Locali On-Premise**: Integrazione nativa con server Ollama o vLLM per eseguire modelli linguistici open-source (es. Llama 3, Mistral) direttamente sui calcolatori di bordo dei robot, eliminando la dipendenza da API cloud.
4. **Verifica Formale a Runtime (Runtime Verification)**: Estensione dei contratti LTS con monitoraggio a runtime tramite tecniche di Complex Event Processing per intercettare anomalie sensoriali durante l'esecuzione fisica della missione.
