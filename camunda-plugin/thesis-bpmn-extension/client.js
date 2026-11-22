(function () {
  'use strict';

  // ═══════════════════════════════════════════════════════════════════════════
  // MRS-Choreography Plugin v2 – Buste Cliccabili via Overlays API
  // Versione target: Camunda Modeler 5.47.0
  //
  // PROBLEMA RISOLTO: il layer djs-hit di diagram-js copre il djs-visual,
  // impedendo ogni click sugli SVG custom. Questa versione usa l'Overlays
  // API di bpmn-js per creare elementi HTML SOPRA tutti i layer del canvas,
  // garantendo click affidabili su buste, bande e form di modifica.
  // ═══════════════════════════════════════════════════════════════════════════

  console.log('[MRS-Choreography] Plugin v2 – Buste Cliccabili via Overlays caricato...');

  var svgNS = 'http://www.w3.org/2000/svg';

  // Servizi bpmn-js iniettati globalmente
  var globalModeling = null;
  var globalElementRegistry = null;
  var globalOverlays = null;

  // ─────────────────────────────────────────────────────────────────────────
  // 1. METAMODELLO MODDLE PER COREOGRAFIA (BPMN 2.0 OMG)
  // ─────────────────────────────────────────────────────────────────────────
  var choreoModdle = {
    name: 'choreo',
    uri: 'http://bpt.hpi.uni-potsdam.de/data',
    prefix: 'choreo',
    xml: { tagAlias: 'lowerCase' },
    types: [
      {
        name: 'ChoreographyTask',
        superClass: ['bpmn:Activity'],
        properties: [
          { name: 'initiatingParticipantRef', type: 'bpmn:Participant', isAttr: true, isReference: true },
          { name: 'participantRef', type: 'bpmn:Participant', isMany: true, isReference: true },
          { name: 'messageFlowRef', type: 'bpmn:MessageFlow', isMany: true, isReference: true }
        ]
      }
    ]
  };

  // ─────────────────────────────────────────────────────────────────────────
  // 2. MAPPA DEFAULT PER IL CASO DI STUDIO (Spaziani Sezione 5.10)
  // ─────────────────────────────────────────────────────────────────────────
  var DEFAULT_META = {
    'Task_weedPosition':       { sender: 'Drone',      action: 'Invia posizione erbacce',       receiver: 'Tagliatore',  parallel: true,  topMsg: 'weedPositionMessage',    bottomMsg: 'weedPositionReceived' },
    'Task_availability':       { sender: 'Tagliatore',  action: 'Invia disponibilità',           receiver: 'Drone',       parallel: true,  topMsg: 'availabilityMessage',    bottomMsg: 'availabilityReceived' },
    'Task_closestTractor':     { sender: 'Drone',      action: 'Notifica trattore più vicino',  receiver: 'Tagliatore',  parallel: false, topMsg: 'closestTractorMessage',  bottomMsg: 'closestTractorAck' },
    'Task_fieldReady':         { sender: 'Tagliatore',  action: 'Segnale terreno pronto',        receiver: 'Scavatore',   parallel: false, topMsg: 'fieldReadyMessage',      bottomMsg: 'fieldReadyAck' },
    'Task_digDone':            { sender: 'Scavatore',   action: 'Scavo completato',              receiver: 'Seminatore',  parallel: false, topMsg: 'digDoneMessage',         bottomMsg: 'digdoneAck' },
    'Task_seedingDone':        { sender: 'Seminatore',  action: 'Segnale missione completata',   receiver: 'Broadcast',   parallel: false, topMsg: 'seedingDoneMessage',     bottomMsg: null },
    'Task_Choreography_Legend':{ sender: 'Drone',      action: 'Interazione Collaborativa',     receiver: 'Tagliatore',  parallel: false, topMsg: 'weedPositionMessage',    bottomMsg: 'weedPositionReceived' }
  };

  // ─────────────────────────────────────────────────────────────────────────
  // 3. PARSER – estrae metadata dal nome del task BPMN
  //    Formato: "Azione [msg: X | ack: Y]\n(Sender -> Receiver)"
  // ─────────────────────────────────────────────────────────────────────────
  function parseChoreographyMeta(element) {
    if (!element) return null;
    var bo = element.businessObject || {};
    var id = element.id || '';
    var text = (bo.name || '').trim();

    var topMsg = null;
    var bottomMsg = null;

    // Estrazione messaggi espliciti [msg: ... | ack: ...]
    var msgMatch = text.match(/\[(?:msg:\s*([^\|\]]+))?(?:\s*\|\s*)?(?:ack:\s*([^\]]+))?\]/i);
    if (msgMatch) {
      if (msgMatch[1]) topMsg = msgMatch[1].trim();
      if (msgMatch[2]) bottomMsg = msgMatch[2].trim();
    }

    var cleanText = text.replace(/\[.*?\]/g, '').trim();

    // Fallback su DEFAULT_META per ID noti
    if (DEFAULT_META[id] && (!cleanText || cleanText.indexOf('->') === -1)) {
      var d = Object.assign({}, DEFAULT_META[id]);
      if (topMsg) d.topMsg = topMsg;
      if (bottomMsg) d.bottomMsg = bottomMsg;
      return d;
    }

    // Parse del formato (Sender -> Receiver)
    var match = cleanText.match(/\((.*?)\s*->\s*(.*?)\)/);
    if (match) {
      return {
        sender: match[1].trim(),
        action: cleanText.substring(0, cleanText.indexOf('(')).trim() || 'Task di Coreografia',
        receiver: match[2].trim(),
        parallel: text.indexOf('|||') !== -1,
        topMsg: topMsg || (DEFAULT_META[id] ? DEFAULT_META[id].topMsg : null),
        bottomMsg: bottomMsg || (DEFAULT_META[id] ? DEFAULT_META[id].bottomMsg : null)
      };
    }

    // Fallback per task con keyword coreografia
    if (text.toLowerCase().indexOf('choreography') !== -1 || text.toLowerCase().indexOf('coreografia') !== -1) {
      return {
        sender: 'Sender (Mittente)',
        action: cleanText.replace(/\[choreography\]/i, '').trim() || 'Attività',
        receiver: 'Receiver (Destinatario)',
        parallel: false,
        topMsg: topMsg,
        bottomMsg: bottomMsg
      };
    }

    return null;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 4. AGGIORNAMENTO PROPRIETÀ tramite modeling.updateProperties
  // ─────────────────────────────────────────────────────────────────────────
  function updateModelElement(element, newSender, newAction, newReceiver, newTopMsg, newBottomMsg, newParallel) {
    var meta = parseChoreographyMeta(element) || {
      sender: 'Drone', action: 'Task', receiver: 'Tagliatore',
      topMsg: null, bottomMsg: null, parallel: false
    };
    var sender   = newSender   !== null && newSender   !== undefined ? newSender   : meta.sender;
    var action   = newAction   !== null && newAction   !== undefined ? newAction   : meta.action;
    var receiver = newReceiver !== null && newReceiver !== undefined ? newReceiver : meta.receiver;
    var topMsg   = newTopMsg   !== undefined ? newTopMsg   : meta.topMsg;
    var bottomMsg= newBottomMsg!== undefined ? newBottomMsg: meta.bottomMsg;
    var parallel = newParallel !== undefined ? newParallel : (meta.parallel || false);

    var msgPart = '';
    if (topMsg && bottomMsg) {
      msgPart = ' [msg: ' + topMsg + ' | ack: ' + bottomMsg + ']';
    } else if (topMsg) {
      msgPart = ' [msg: ' + topMsg + ']';
    } else if (bottomMsg) {
      msgPart = ' [ack: ' + bottomMsg + ']';
    }

    var parallelMark = parallel ? ' |||' : '';
    var newName = action + msgPart + '\n(' + sender + ' -> ' + receiver + ')' + parallelMark;

    if (globalModeling) {
      globalModeling.updateProperties(element, { name: newName });
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 5. GESTIONE DIALOG DI MODIFICA (chiusura globale)
  // ─────────────────────────────────────────────────────────────────────────
  var outsideClickBound = null;

  function closeEditDialogs() {
    try { globalOverlays.remove({ type: 'choreo-edit' }); } catch (e) { /* noop */ }
    if (outsideClickBound) {
      document.removeEventListener('mousedown', outsideClickBound, true);
      outsideClickBound = null;
    }
  }

  function setupOutsideClickClose() {
    if (outsideClickBound) {
      document.removeEventListener('mousedown', outsideClickBound, true);
    }
    outsideClickBound = function (e) {
      var dialog = document.querySelector('.choreo-edit-dialog');
      if (dialog && !dialog.contains(e.target)) {
        closeEditDialogs();
      }
    };
    setTimeout(function () {
      document.addEventListener('mousedown', outsideClickBound, true);
    }, 120);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 6. DIALOG INLINE PER SINGOLO CAMPO (busta messaggio)
  // ─────────────────────────────────────────────────────────────────────────
  function showMessageEditDialog(element, position, label, currentValue, onConfirm) {
    closeEditDialogs();

    var container = document.createElement('div');
    container.className = 'choreo-edit-dialog';

    var title = document.createElement('div');
    title.className = 'choreo-edit-title';
    title.textContent = label;
    container.appendChild(title);

    var input = document.createElement('input');
    input.type = 'text';
    input.className = 'choreo-edit-input';
    input.placeholder = 'Nome del messaggio...';
    input.value = currentValue || '';
    container.appendChild(input);

    var actions = document.createElement('div');
    actions.className = 'choreo-edit-actions';

    var confirmBtn = document.createElement('button');
    confirmBtn.className = 'choreo-btn choreo-btn-confirm';
    confirmBtn.textContent = '✓ Conferma';

    var cancelBtn = document.createElement('button');
    cancelBtn.className = 'choreo-btn choreo-btn-cancel';
    cancelBtn.textContent = '✕ Annulla';

    var deleteBtn = document.createElement('button');
    deleteBtn.className = 'choreo-btn choreo-btn-delete';
    deleteBtn.textContent = '🗑 Elimina';

    actions.appendChild(confirmBtn);
    actions.appendChild(cancelBtn);
    if (currentValue) {
      actions.appendChild(deleteBtn);
    }
    container.appendChild(actions);

    function close() { closeEditDialogs(); }

    confirmBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      var val = input.value.trim();
      close();
      if (val) onConfirm(val);
    });
    cancelBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      close();
    });
    deleteBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      close();
      onConfirm(null);
    });
    input.addEventListener('keydown', function (e) {
      e.stopPropagation();
      if (e.key === 'Enter') {
        var val = input.value.trim();
        close();
        if (val) onConfirm(val);
      } else if (e.key === 'Escape') {
        close();
      }
    });

    // Blocca propagazione verso il canvas per tutti gli eventi mouse/tastiera
    ['click', 'mousedown', 'mouseup', 'dblclick', 'keydown', 'keyup'].forEach(function (evt) {
      container.addEventListener(evt, function (e) { e.stopPropagation(); });
    });

    globalOverlays.add(element, 'choreo-edit', {
      position: position,
      html: container,
      show: { minZoom: 0.2 }
    });

    setupOutsideClickClose();
    setTimeout(function () { input.focus(); input.select(); }, 80);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 7. DIALOG COMPLETO (doppio clic su task – modifica tutti i 5 campi)
  // ─────────────────────────────────────────────────────────────────────────
  function showFullEditDialog(element) {
    closeEditDialogs();
    var meta = parseChoreographyMeta(element);
    if (!meta) return;

    var fields = [
      { key: 'sender',    label: 'Sender (Iniziatore)',                value: meta.sender   || '' },
      { key: 'action',    label: 'Azione (Nome Task)',                 value: meta.action   || '' },
      { key: 'receiver',  label: 'Receiver (Destinatario)',            value: meta.receiver || '' },
      { key: 'topMsg',    label: '✉ Messaggio Iniziale (Busta Bianca)',value: meta.topMsg   || '' },
      { key: 'bottomMsg', label: '✉ Risposta / ACK (Busta Scura)',    value: meta.bottomMsg|| '' }
    ];

    var container = document.createElement('div');
    container.className = 'choreo-edit-dialog choreo-edit-full';

    var title = document.createElement('div');
    title.className = 'choreo-edit-title';
    title.textContent = '✏️ Modifica Choreography Task';
    container.appendChild(title);

    var inputs = {};
    fields.forEach(function (f) {
      var lbl = document.createElement('label');
      lbl.className = 'choreo-field-label';
      lbl.textContent = f.label;
      container.appendChild(lbl);

      var inp = document.createElement('input');
      inp.type = 'text';
      inp.className = 'choreo-edit-input';
      inp.value = f.value;
      if (f.key === 'topMsg' || f.key === 'bottomMsg') {
        inp.placeholder = 'Lascia vuoto per nessun messaggio';
      }
      container.appendChild(inp);
      inputs[f.key] = inp;
    });

    // ── Checkbox Multi-Instance Parallelo ──
    var parallelDiv = document.createElement('div');
    parallelDiv.className = 'choreo-parallel-row';

    var parallelCb = document.createElement('input');
    parallelCb.type = 'checkbox';
    parallelCb.id = 'choreo-parallel-cb-' + (element.id || 'x');
    parallelCb.checked = meta.parallel || false;
    parallelCb.className = 'choreo-parallel-checkbox';

    var parallelLbl = document.createElement('label');
    parallelLbl.htmlFor = parallelCb.id;
    parallelLbl.className = 'choreo-parallel-label';
    parallelLbl.textContent = ' Multi-Instance Parallelo  |||';

    parallelDiv.appendChild(parallelCb);
    parallelDiv.appendChild(parallelLbl);
    container.appendChild(parallelDiv);

    var actions = document.createElement('div');
    actions.className = 'choreo-edit-actions';

    var saveBtn = document.createElement('button');
    saveBtn.className = 'choreo-btn choreo-btn-confirm';
    saveBtn.textContent = '✓ Salva Tutto';

    var cancelBtn = document.createElement('button');
    cancelBtn.className = 'choreo-btn choreo-btn-cancel';
    cancelBtn.textContent = '✕ Annulla';

    actions.appendChild(saveBtn);
    actions.appendChild(cancelBtn);
    container.appendChild(actions);

    function close() { closeEditDialogs(); }

    saveBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      var s   = inputs.sender.value.trim()    || meta.sender;
      var a   = inputs.action.value.trim()    || meta.action;
      var r   = inputs.receiver.value.trim()  || meta.receiver;
      var tm  = inputs.topMsg.value.trim()    || null;
      var bm  = inputs.bottomMsg.value.trim() || null;
      var par = parallelCb.checked;
      close();
      updateModelElement(element, s, a, r, tm, bm, par);
    });

    cancelBtn.addEventListener('click', function (e) {
      e.stopPropagation();
      close();
    });

    // Blocca propagazione verso il canvas
    ['click', 'mousedown', 'mouseup', 'dblclick', 'keydown', 'keyup'].forEach(function (evt) {
      container.addEventListener(evt, function (e) { e.stopPropagation(); });
    });

    // Posiziona il pannello a destra dell'elemento
    var w = element.width || 160;
    globalOverlays.add(element, 'choreo-edit', {
      position: { top: -10, left: w + 18 },
      html: container,
      show: { minZoom: 0.2 }
    });

    setupOutsideClickClose();
    setTimeout(function () { inputs.sender.focus(); inputs.sender.select(); }, 80);
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 8. CREAZIONE OVERLAY HTML PER BUSTE (cliccabili)
  // ─────────────────────────────────────────────────────────────────────────
  function createEnvelopeOverlay(label, isDark, element) {
    var container = document.createElement('div');
    container.className = 'choreo-envelope-overlay ' + (isDark ? 'choreo-envelope-dark' : 'choreo-envelope-white');
    container.title = isDark
      ? '✉ Clicca per modificare la Risposta (Busta Scura)'
      : '✉ Clicca per modificare il Messaggio (Busta Bianca)';

    // Icona SVG della busta
    var iconDiv = document.createElement('div');
    iconDiv.className = 'choreo-envelope-icon';
    iconDiv.innerHTML =
      '<svg width="24" height="16" viewBox="0 0 24 16">' +
        '<rect x="0" y="0" width="24" height="16" ' +
          'fill="' + (isDark ? '#5D6D7E' : '#FFFFFF') + '" ' +
          'stroke="#2C3E50" stroke-width="1.5" rx="2"/>' +
        '<path d="M 0 0 L 12 10 L 24 0" fill="none" ' +
          'stroke="' + (isDark ? '#FFFFFF' : '#2C3E50') + '" stroke-width="1.5"/>' +
      '</svg>';
    container.appendChild(iconDiv);

    // Label del messaggio
    var labelSpan = document.createElement('span');
    labelSpan.className = 'choreo-envelope-label';
    labelSpan.textContent = label || '';
    container.appendChild(labelSpan);

    // Click → apre dialog inline
    container.addEventListener('click', function (e) {
      e.stopPropagation();
      var editLabel = isDark
        ? '✉ Modifica Risposta / ACK (Busta Scura)'
        : '✉ Modifica Messaggio Iniziale (Busta Bianca)';
      var h = element.height || 90;
      var editPos = isDark
        ? { top: h + 42, left: 0 }
        : { top: -80, left: 0 };
      showMessageEditDialog(element, editPos, editLabel, label, function (newVal) {
        if (isDark) {
          updateModelElement(element, null, null, null, undefined, newVal);
        } else {
          updateModelElement(element, null, null, null, newVal, undefined);
        }
      });
    });
    container.addEventListener('mousedown', function (e) { e.stopPropagation(); });

    return container;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 9. BOTTONE "+✉" PER AGGIUNGERE UNA BUSTA MANCANTE
  // ─────────────────────────────────────────────────────────────────────────
  function createAddEnvelopeOverlay(label, isDark, element, meta) {
    var container = document.createElement('div');
    container.className = 'choreo-add-envelope ' + (isDark ? 'choreo-add-dark' : 'choreo-add-white');
    container.title = 'Aggiungi ' + label;
    container.textContent = '+✉ ' + label;

    container.addEventListener('click', function (e) {
      e.stopPropagation();
      var editLabel = isDark
        ? '✉ Aggiungi Risposta / ACK (Busta Scura)'
        : '✉ Aggiungi Messaggio Iniziale (Busta Bianca)';
      var h = element.height || 90;
      var editPos = isDark
        ? { top: h + 32, left: 0 }
        : { top: -80, left: 0 };
      var senderKey = (meta.sender || 'sender').toLowerCase().replace(/\s+/g, '_');
      var receiverKey = (meta.receiver || 'receiver').toLowerCase().replace(/\s+/g, '_');
      var defaultVal = isDark ? ('ack_' + receiverKey) : ('msg_' + senderKey);

      showMessageEditDialog(element, editPos, editLabel, defaultVal, function (newVal) {
        if (newVal) {
          if (isDark) {
            updateModelElement(element, null, null, null, undefined, newVal);
          } else {
            updateModelElement(element, null, null, null, newVal, undefined);
          }
        }
      });
    });
    container.addEventListener('mousedown', function (e) { e.stopPropagation(); });

    return container;
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 10. AGGIUNTA OVERLAY INTERATTIVI A UN ELEMENTO
  // ─────────────────────────────────────────────────────────────────────────
  function addInteractiveOverlays(element) {
    var meta = parseChoreographyMeta(element);
    if (!meta) return;

    // Rimuovi vecchi overlay per questo elemento
    try { globalOverlays.remove({ element: element.id, type: 'choreo-envelope' }); } catch (e) { /* noop */ }

    var w = element.width  || 160;
    var h = element.height || 90;

    // ── Busta Bianca (Messaggio Iniziale) ──
    if (meta.topMsg) {
      var topHtml = createEnvelopeOverlay(meta.topMsg, false, element);
      globalOverlays.add(element, 'choreo-envelope', {
        position: { top: -32, left: 20 },
        html: topHtml,
        show: { minZoom: 0.3 }
      });
    } else {
      var addTopHtml = createAddEnvelopeOverlay('Invia Msg', false, element, meta);
      globalOverlays.add(element, 'choreo-envelope', {
        position: { top: -26, left: w - 100 },
        html: addTopHtml,
        show: { minZoom: 0.3 }
      });
    }

    // ── Busta Scura (Messaggio di Risposta / ACK) ──
    if (meta.bottomMsg) {
      var bx = (element.id === 'Task_availability') ? -120 : (w - 30);
      var botHtml = createEnvelopeOverlay(meta.bottomMsg, true, element);
      globalOverlays.add(element, 'choreo-envelope', {
        position: { top: h + 8, left: bx },
        html: botHtml,
        show: { minZoom: 0.3 }
      });
    } else {
      var addBotHtml = createAddEnvelopeOverlay('Risposta', true, element, meta);
      globalOverlays.add(element, 'choreo-envelope', {
        position: { top: h + 4, left: w - 100 },
        html: addBotHtml,
        show: { minZoom: 0.3 }
      });
    }

    // ── Icona ✏️ in alto a destra (indicatore di modifica) ──
    var editHint = document.createElement('div');
    editHint.className = 'choreo-edit-hint';
    editHint.textContent = '✏️';
    editHint.title = 'Doppio clic per modificare tutte le proprietà';
    editHint.addEventListener('click', function (e) {
      e.stopPropagation();
      showFullEditDialog(element);
    });
    editHint.addEventListener('mousedown', function (e) { e.stopPropagation(); });
    globalOverlays.add(element, 'choreo-envelope', {
      position: { top: 2, left: w - 22 },
      html: editHint,
      show: { minZoom: 0.5 }
    });


  }

  // ─────────────────────────────────────────────────────────────────────────
  // 11. RENDERING SVG DEL BLOCCO A 3 BANDE (puramente visivo, nessun click)
  // ─────────────────────────────────────────────────────────────────────────
  function decorateChoreographyTask(visual, element) {
    if (!visual || !element) return;
    var rect = visual.querySelector('rect');
    if (!rect) return;

    var meta = parseChoreographyMeta(element);
    if (!meta) return;

    // Fingerprint per evitare rendering duplicati
    var fp = (element.id || '') + '::' + (element.businessObject && element.businessObject.name) +
             '::' + meta.sender + '::' + meta.receiver + '::' + meta.topMsg + '::' + meta.bottomMsg;
    if (visual.getAttribute('data-thesis-rendered') === fp) return;
    visual.setAttribute('data-thesis-rendered', fp);

    // Pulizia vecchie decorazioni
    var old = visual.querySelectorAll('.thesis-choreography-band');
    old.forEach(function (b) { b.remove(); });

    var width  = parseFloat(rect.getAttribute('width'))  || element.width  || 160;
    var height = parseFloat(rect.getAttribute('height')) || element.height || 90;
    var bandH  = 26;
    var r      = 8;

    // Stile rettangolo base
    rect.setAttribute('stroke', '#2C3E50');
    rect.setAttribute('stroke-width', '2');
    rect.setAttribute('fill', '#FFFFFF');
    rect.setAttribute('rx', '8');
    rect.setAttribute('ry', '8');

    // Nascondi testo originale bpmn-js
    var origTexts = visual.querySelectorAll('text:not(.thesis-choreography-band)');
    origTexts.forEach(function (t) { t.style.display = 'none'; });

    // ── BANDA SUPERIORE (Sender – Iniziatore, bianca) ──
    var topBand = document.createElementNS(svgNS, 'path');
    topBand.setAttribute('class', 'thesis-choreography-band thesis-band-top');
    topBand.setAttribute('d',
      'M 0 ' + r + ' Q 0 0 ' + r + ' 0 L ' + (width - r) + ' 0 Q ' + width + ' 0 ' + width + ' ' + r +
      ' L ' + width + ' ' + bandH + ' L 0 ' + bandH + ' Z'
    );
    topBand.setAttribute('fill', '#FFFFFF');
    visual.appendChild(topBand);

    var topLine = document.createElementNS(svgNS, 'line');
    topLine.setAttribute('class', 'thesis-choreography-band');
    topLine.setAttribute('x1', '0');
    topLine.setAttribute('y1', bandH.toString());
    topLine.setAttribute('x2', width.toString());
    topLine.setAttribute('y2', bandH.toString());
    topLine.setAttribute('stroke', '#2C3E50');
    topLine.setAttribute('stroke-width', '1.5');
    visual.appendChild(topLine);

    var textSender = document.createElementNS(svgNS, 'text');
    textSender.setAttribute('class', 'thesis-choreography-band');
    textSender.setAttribute('x', (width / 2).toString());
    textSender.setAttribute('y', '18');
    textSender.setAttribute('text-anchor', 'middle');
    textSender.setAttribute('font-family', 'sans-serif');
    textSender.setAttribute('font-size', '12');
    textSender.setAttribute('font-weight', 'bold');
    textSender.setAttribute('fill', '#1B4F72');
    textSender.textContent = meta.sender;
    visual.appendChild(textSender);

    // ── SEZIONE CENTRALE (Azione / Nome del Task) ──
    var midArea = document.createElementNS(svgNS, 'rect');
    midArea.setAttribute('class', 'thesis-choreography-band thesis-band-mid');
    midArea.setAttribute('x', '0');
    midArea.setAttribute('y', bandH.toString());
    midArea.setAttribute('width', width.toString());
    midArea.setAttribute('height', (height - bandH * 2).toString());
    midArea.setAttribute('fill', 'transparent');
    visual.appendChild(midArea);

    var textAction = document.createElementNS(svgNS, 'text');
    textAction.setAttribute('class', 'thesis-choreography-band');
    textAction.setAttribute('x', (width / 2).toString());
    textAction.setAttribute('y', meta.parallel ? (bandH + 17).toString() : (bandH + 22).toString());
    textAction.setAttribute('text-anchor', 'middle');
    textAction.setAttribute('font-family', 'sans-serif');
    textAction.setAttribute('font-size', '11');
    textAction.setAttribute('font-weight', '600');
    textAction.setAttribute('fill', '#2C3E50');
    textAction.textContent = meta.action;
    visual.appendChild(textAction);

    // Marcatore parallelo |||
    if (meta.parallel) {
      var textParallel = document.createElementNS(svgNS, 'text');
      textParallel.setAttribute('class', 'thesis-choreography-band');
      textParallel.setAttribute('x', (width / 2).toString());
      textParallel.setAttribute('y', (bandH + 32).toString());
      textParallel.setAttribute('text-anchor', 'middle');
      textParallel.setAttribute('font-family', 'sans-serif');
      textParallel.setAttribute('font-size', '12');
      textParallel.setAttribute('font-weight', 'bold');
      textParallel.setAttribute('letter-spacing', '2');
      textParallel.setAttribute('fill', '#2C3E50');
      textParallel.textContent = '|||';
      visual.appendChild(textParallel);
    }

    // ── BANDA INFERIORE (Receiver – Destinatario, grigia) ──
    var by = height - bandH;
    var bottomBand = document.createElementNS(svgNS, 'path');
    bottomBand.setAttribute('class', 'thesis-choreography-band thesis-band-bottom');
    bottomBand.setAttribute('d',
      'M 0 ' + by + ' L ' + width + ' ' + by + ' L ' + width + ' ' + (height - r) +
      ' Q ' + width + ' ' + height + ' ' + (width - r) + ' ' + height +
      ' L ' + r + ' ' + height + ' Q 0 ' + height + ' 0 ' + (height - r) + ' Z'
    );
    bottomBand.setAttribute('fill', '#EAEDED');
    bottomBand.setAttribute('stroke', '#2C3E50');
    bottomBand.setAttribute('stroke-width', '1.5');
    visual.appendChild(bottomBand);

    var textReceiver = document.createElementNS(svgNS, 'text');
    textReceiver.setAttribute('class', 'thesis-choreography-band');
    textReceiver.setAttribute('x', (width / 2).toString());
    textReceiver.setAttribute('y', (height - 8).toString());
    textReceiver.setAttribute('text-anchor', 'middle');
    textReceiver.setAttribute('font-family', 'sans-serif');
    textReceiver.setAttribute('font-size', '12');
    textReceiver.setAttribute('font-weight', 'bold');
    textReceiver.setAttribute('fill', '#2C3E50');
    textReceiver.textContent = meta.receiver;
    visual.appendChild(textReceiver);

    // ── Linee tratteggiate di collegamento (puramente visive) ──
    // Verso la busta bianca (sopra)
    if (meta.topMsg) {
      var topConn = document.createElementNS(svgNS, 'line');
      topConn.setAttribute('class', 'thesis-choreography-band');
      topConn.setAttribute('x1', '32');
      topConn.setAttribute('y1', '0');
      topConn.setAttribute('x2', '32');
      topConn.setAttribute('y2', '-14');
      topConn.setAttribute('stroke', '#7F8C8D');
      topConn.setAttribute('stroke-width', '1.2');
      topConn.setAttribute('stroke-dasharray', '3,2');
      visual.appendChild(topConn);
    }
    // Verso la busta scura (sotto)
    if (meta.bottomMsg) {
      var bxConn = (element.id === 'Task_availability') ? -108 : (width - 18);
      var botConn = document.createElementNS(svgNS, 'line');
      botConn.setAttribute('class', 'thesis-choreography-band');
      botConn.setAttribute('x1', bxConn.toString());
      botConn.setAttribute('y1', height.toString());
      botConn.setAttribute('x2', bxConn.toString());
      botConn.setAttribute('y2', (height + 8).toString());
      botConn.setAttribute('stroke', '#7F8C8D');
      botConn.setAttribute('stroke-width', '1.2');
      botConn.setAttribute('stroke-dasharray', '3,2');
      visual.appendChild(botConn);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 12. RENDER GLOBALE (SVG + Overlays)
  // ─────────────────────────────────────────────────────────────────────────
  function renderAll() {
    if (!globalElementRegistry) return;
    var elements = globalElementRegistry.getAll();
    elements.forEach(function (el) {
      if (!el) return;
      var visual = globalElementRegistry.getGraphics(el);
      if (!visual) return;
      var gfx = visual.querySelector('.djs-visual');
      if (!gfx) return;

      if (el.type === 'bpmn:Task' || el.type === 'bpmn:ChoreographyTask' ||
          (el.id && el.id.indexOf('Task') !== -1)) {
        decorateChoreographyTask(gfx, el);
        addInteractiveOverlays(el);
      }
    });
  }

  // ─────────────────────────────────────────────────────────────────────────
  // 13. PALETTE PROVIDER (crea Choreography Task dalla palette)
  // ─────────────────────────────────────────────────────────────────────────
  function ChoreoPaletteProvider(palette, create, elementFactory, bpmnFactory) {
    this._create = create;
    this._elementFactory = elementFactory;
    this._bpmnFactory = bpmnFactory;
    palette.registerProvider(this);
  }

  ChoreoPaletteProvider.prototype.getPaletteEntries = function () {
    var create = this._create;
    var elementFactory = this._elementFactory;
    var bpmnFactory = this._bpmnFactory;

    function createChoreoTask(event) {
      var bo = bpmnFactory.create('bpmn:Task', {
        name: 'Interazione Collaborativa [msg: weedCoord | ack: coordAck]\n(Drone -> Tagliatore)'
      });
      var shape = elementFactory.createShape({
        type: 'bpmn:Task',
        businessObject: bo,
        width: 160,
        height: 90
      });
      create.start(event, shape);
    }

    return {
      'create.choreo-task': {
        group: 'activity',
        className: 'entry choreo-task-palette-entry bpmn-icon-choreography-task',
        title: 'Crea Choreography Task a 3 bande (MRS Coreografia)',
        action: { dragstart: createChoreoTask, click: createChoreoTask }
      }
    };
  };

  ChoreoPaletteProvider.$inject = ['palette', 'create', 'elementFactory', 'bpmnFactory'];

  // ─────────────────────────────────────────────────────────────────────────
  // 14. CONTEXT PAD PROVIDER (append Choreography Task dal context pad)
  // ─────────────────────────────────────────────────────────────────────────
  function ChoreoContextPadProvider(contextPad, create, elementFactory, bpmnFactory) {
    this._create = create;
    this._elementFactory = elementFactory;
    this._bpmnFactory = bpmnFactory;
    contextPad.registerProvider(this);
  }

  ChoreoContextPadProvider.prototype.getContextPadEntries = function (targetElement) {
    var create = this._create;
    var elementFactory = this._elementFactory;
    var bpmnFactory = this._bpmnFactory;

    return function (entries) {
      if (targetElement.type === 'bpmn:Process' || targetElement.type === 'bpmn:Collaboration') {
        return entries;
      }

      function appendChoreo(event) {
        var bo = bpmnFactory.create('bpmn:Task', {
          name: 'Interazione Successiva [msg: newMsg | ack: newAck]\n(Sender -> Receiver)'
        });
        var shape = elementFactory.createShape({
          type: 'bpmn:Task',
          businessObject: bo,
          width: 160,
          height: 90
        });
        create.start(event, shape, targetElement);
      }

      entries['append.choreo-task'] = {
        group: 'model',
        className: 'bpmn-icon-choreography-task',
        title: 'Collega Choreography Task a 3 bande',
        action: { click: appendChoreo, dragstart: appendChoreo }
      };
      return entries;
    };
  };

  ChoreoContextPadProvider.$inject = ['contextPad', 'create', 'elementFactory', 'bpmnFactory'];

  // ─────────────────────────────────────────────────────────────────────────
  // 15. MODULO PRINCIPALE (inietta overlays + gestisce eventi)
  // ─────────────────────────────────────────────────────────────────────────
  function ThesisChoreographyModule(eventBus, modeling, elementRegistry, overlays) {
    globalModeling = modeling;
    globalElementRegistry = elementRegistry;
    globalOverlays = overlays;

    // Render iniziale dopo import del diagramma
    eventBus.on('import.done', function () {
      setTimeout(renderAll, 100);
    });

    // Aggiornamento quando un elemento cambia (nome, proprietà)
    eventBus.on('element.changed', function (event) {
      if (!event || !event.element) return;
      var el = event.element;
      if (el.type === 'bpmn:Task' || el.type === 'bpmn:ChoreographyTask' ||
          (el.id && el.id.indexOf('Task') !== -1)) {
        var visual = globalElementRegistry.getGraphics(el);
        if (visual) {
          var gfx = visual.querySelector('.djs-visual');
          if (gfx) {
            gfx.removeAttribute('data-thesis-rendered');
            decorateChoreographyTask(gfx, el);
          }
        }
        addInteractiveOverlays(el);
      }
    });

    // Doppio clic su task → apre il pannello completo di modifica
    // Priorità 1500 per intercettare PRIMA dell'editor inline di bpmn-js
    eventBus.on('element.dblclick', 1500, function (event) {
      var el = event.element;
      var meta = parseChoreographyMeta(el);
      if (!meta) return; // lascia che il comportamento default gestisca task normali
      showFullEditDialog(el);
      return false; // impedisce l'apertura dell'editor inline default
    });

    // Pulizia overlay quando un elemento viene rimosso
    eventBus.on('shape.removed', function (event) {
      if (!event || !event.element) return;
      try { globalOverlays.remove({ element: event.element.id, type: 'choreo-envelope' }); } catch (e) { /* noop */ }
      try { globalOverlays.remove({ element: event.element.id, type: 'choreo-edit' }); } catch (e) { /* noop */ }
    });
  }

  ThesisChoreographyModule.$inject = ['eventBus', 'modeling', 'elementRegistry', 'overlays'];

  // ─────────────────────────────────────────────────────────────────────────
  // 16. REGISTRAZIONE PLUGIN IN CAMUNDA MODELER
  // ─────────────────────────────────────────────────────────────────────────
  window.plugins = window.plugins || [];

  window.plugins.push({
    type: 'bpmn.modeler.moddleExtension',
    plugin: choreoModdle
  });

  window.plugins.push({
    type: 'bpmn.modeler.additionalModules',
    plugin: {
      __init__: ['thesisChoreographyModule', 'choreoPaletteProvider', 'choreoContextPadProvider'],
      thesisChoreographyModule: ['type', ThesisChoreographyModule],
      choreoPaletteProvider:    ['type', ChoreoPaletteProvider],
      choreoContextPadProvider: ['type', ChoreoContextPadProvider]
    }
  });

  console.log('[MRS-Choreography] ✅ Plugin v2 registrato – Overlays + Form Inline + Doppio Clic');
})();
