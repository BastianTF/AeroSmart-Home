/* Simulación del comportamiento de la app en la vista previa.
 * Copia la lógica de MainViewModel.kt y de los observeUiState() de los Fragments,
 * para que los botones hagan lo mismo que en Android (diálogos, toasts, estados). */
(function () {
  const P = window.AndroidPreview;
  const KT = (window.RES.kotlin) || { dialogs: {}, toasts: {} };
  const S = { schedule: { start: 8 * 60, end: 22 * 60, days: [1, 2, 3, 4, 5, 6, 7] }, connected: false, loading: false, auto: true, ventOpen: false, movement: false, temp: 27, hum: 55 };
  let root = null;

  // ---- MainViewModel ----
  function evaluateAutomation() {
    if (!S.auto || !S.connected) return;
    if (S.movement && S.temp > 26) S.ventOpen = true;
    else if (!S.movement || S.temp < 22) S.ventOpen = false;
  }
  const VM = {
    connect() { S.loading = true; apply(); setTimeout(() => { S.loading = false; S.connected = true; S.movement = true; evaluateAutomation(); apply(); }, 1000); },
    disconnect() { S.connected = false; S.ventOpen = false; S.movement = false; apply(); },
    togglePIR() { if (S.connected) { S.movement = !S.movement; evaluateAutomation(); apply(); } },
    refresh() { if (S.connected) { S.temp = 24 + Math.floor(Math.random() * 5); S.hum = 50 + Math.floor(Math.random() * 11); evaluateAutomation(); apply(); } },
    setAuto(v) { S.auto = v; if (S.auto && S.connected) evaluateAutomation(); apply(); },
    toggleVent(open) { S.auto = false; S.ventOpen = open; apply(); },
  };

  // ---- ayudas de vista ----
  const q = (id) => (root ? Array.from(root.querySelectorAll(`[data-id="${id}"]`)) : []);
  function setText(id, t, color) { q(id).forEach((n) => { const s = n.querySelector('.txt') || n; s.textContent = t; if (color) n.style.color = color; }); }
  function setHtmlStatus(id, label, color) {
    q(id).forEach((n) => { const s = n.querySelector('.txt') || n; s.innerHTML = ''; s.append('Estado: '); const b = document.createElement('b'); b.textContent = label; b.style.color = color; s.append(b); });
  }
  function setBg(id, ref) {
    const d = P.drawable(ref);
    q(id).forEach((n) => { n.style.background = ''; n.style.border = ''; n.style.borderRadius = ''; if (d && d.css) Object.assign(n.style, d.css); });
  }
  function setCardBg(id, ref) { const c = P.color(ref); q(id).forEach((n) => { n.style.backgroundColor = c; }); }
  function setChecked(id, v) { q(id).forEach((n) => { const i = n.querySelector('input'); if (i) i.checked = v; }); }
  const str = (n) => (window.RES.strings[n] || n);

  // ---- observeUiState() de cada Fragment ----
  function apply() {
    if (!root) return;
    // Conexión
    if (S.loading) { setHtmlStatus('txtStatus', 'Conectando con Arduino...', '#5B6B74'); setBg('viewStatusDot', '@drawable/circle_gray'); }
    else if (S.connected) { setHtmlStatus('txtStatus', 'Conectado', '#2E9E6A'); setBg('viewStatusDot', '@drawable/circle_green'); }
    else { setHtmlStatus('txtStatus', 'Desconectado', '#D64541'); setBg('viewStatusDot', '@drawable/circle_red'); }
    setText('btnConnect', S.connected ? str('disconnect_bluetooth') : str('connect'));
    q('btnConnect').forEach((n) => { n.style.opacity = S.loading ? '.6' : ''; n.style.pointerEvents = S.loading ? 'none' : ''; });
    // Sensores
    setText('txtTemperature', S.connected ? `${S.temp} °C` : '-- °C');
    setText('txtHumidity', S.connected ? `${S.hum} %` : '-- %');
    const mov = S.connected && S.movement;
    setText('txtPIRStatus', mov ? 'Estado: Movimiento detectado' : str('status_no_movement'));
    setBg('viewLedPIR', mov ? '@drawable/circle_green' : '@drawable/circle_gray');
    // Modo automático
    setChecked('switchAutoMode', S.auto); setChecked('switchAutoMain', S.auto);
    setText('txtAutoStatus', S.auto ? str('auto_mode_desc') : 'Modo manual activado');
    setText('txtAutoStatusHeader', S.auto ? 'Estado: Activado' : 'Estado: Modo Manual');
    setBg('dotAuto', S.auto ? '@drawable/circle_green' : '@drawable/circle_gray');
    ['btnSegAuto', 'btnModeAuto'].forEach((id) => setBg(id, S.auto ? '@drawable/bg_segmented_selected' : '@drawable/bg_segmented_unselected'));
    ['btnSegManual', 'btnModeManual'].forEach((id) => setBg(id, S.auto ? '@drawable/bg_segmented_unselected' : '@drawable/bg_segmented_selected'));
    // Compuerta
    const onVentTab = q('btnOpenVent').length > 0;
    if (onVentTab) setText('txtServoStatus', S.ventOpen ? 'Estado: Abierto' : 'Estado: Cerrado', P.color(S.ventOpen ? '@color/success' : '@color/primary'));
    else setText('txtServoStatus', S.ventOpen ? str('status_open') : str('status_closed'));
    setBg('btnOpenVent', S.ventOpen ? '@drawable/bg_segmented_selected' : '@drawable/bg_segmented_unselected');
    setBg('btnCloseVent', S.ventOpen ? '@drawable/bg_segmented_unselected' : '@drawable/bg_segmented_selected');
    setCardBg('btnVentToggle', S.ventOpen ? '@color/primary_container' : '@color/surface_variant');
    setText('txtScheduleDesc', describe(S.schedule));
  }

  // ---- diálogos y toasts (como AlertDialog y Toast de Android) ----
  function toast(msg) {
    if (!root) return;
    root.querySelectorAll('.sim-toast').forEach((t) => t.remove());
    const t = document.createElement('div'); t.className = 'sim-toast'; t.textContent = msg;
    root.append(t); setTimeout(() => t.classList.add('out'), 1800); setTimeout(() => t.remove(), 2200);
  }
  function dialog(title, msg, buttons, items, content) {
    if (!root) return;
    const wrap = document.createElement('div'); wrap.className = 'sim-scrim';
    const box = document.createElement('div'); box.className = 'sim-dialog'; box.setAttribute('role', 'dialog');
    const h = document.createElement('h3'); h.textContent = title; box.append(h);
    if (msg) { const p = document.createElement('p'); p.textContent = msg; box.append(p); }
    const close = () => { wrap.classList.add('out'); setTimeout(() => wrap.remove(), 150); };
    if (content) { content.classList.add('sim-content'); box.append(content); }
    if (items) {
      const ul = document.createElement('div'); ul.className = 'sim-items';
      items.forEach(([label, fn]) => { const b = document.createElement('button'); b.type = 'button'; b.textContent = label; b.onclick = () => { close(); fn(); }; ul.append(b); });
      box.append(ul);
    }
    const row = document.createElement('div'); row.className = 'sim-actions';
    buttons.forEach(([label, fn]) => { const b = document.createElement('button'); b.type = 'button'; b.textContent = label; b.onclick = () => { if (fn && fn() === false) return; close(); }; row.append(b); });
    box.append(row); wrap.append(box);
    wrap.addEventListener('click', (e) => { if (e.target === wrap) close(); });
    root.append(wrap);
  }
  function bluetoothPicker() {
    const devices = ['AeroSmart-ESP32 (Bluetooth BLE)', 'HC-05 (Bluetooth Classic)', 'Arduino Wi-Fi / MQTT Broker'];
    dialog('Vincular con Hardware Arduino', null, [['Cancelar']], devices.map((d) => [d, () => { toast(`Vinculando con ${d}...`); VM.connect(); }]));
  }

  // ---- Programación horaria (AutomationFragment.showScheduleDialog) ----
  const fmt = (m) => String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0');
  function describe(s) {
    const names = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
    const d = [...s.days].sort();
    const days = d.length === 7 ? 'Todos los días' : d.join() === '1,2,3,4,5' ? 'Lun a Vie' : d.join() === '6,7' ? 'Fines de semana' : d.map((i) => names[i - 1]).join(', ');
    return `${fmt(s.start)} – ${fmt(s.end)}${s.end < s.start ? ' (al día siguiente)' : ''} · ${days}`;
  }
  function scheduleDialog() {
    if (!window.RES.layouts.dialog_schedule) return;
    let start = S.schedule.start, end = S.schedule.end;
    const body = P.renderNode('dialog_schedule', { screen: 'dlg' });
    body.style.padding = '0';
    const btnS = body.querySelector('[data-id="btnStartTime"]'), btnE = body.querySelector('[data-id="btnEndTime"]');
    const label = (b, t) => { const s = b && b.querySelector('.txt'); if (s) s.textContent = t; };
    const refresh = () => { label(btnS, 'Desde ' + fmt(start)); label(btnE, 'Hasta ' + fmt(end)); };
    // Selector de hora: un <input type=time> invisible encima del botón abre el reloj del teléfono
    [[btnS, () => start, (v) => (start = v)], [btnE, () => end, (v) => (end = v)]].forEach(([b, get, set]) => {
      if (!b) return;
      const inp = document.createElement('input'); inp.type = 'time'; inp.className = 'time-overlay'; inp.value = fmt(get());
      inp.setAttribute('aria-label', b === btnS ? 'Hora de inicio' : 'Hora de término');
      inp.addEventListener('input', () => { if (!inp.value) return; const [h, m] = inp.value.split(':').map(Number); set(h * 60 + m); refresh(); });
      b.append(inp);
    });
    for (let i = 1; i <= 7; i++) { const c = body.querySelector(`[data-id="chipDay${i}"]`); if (c) { c.dataset.checked = S.schedule.days.includes(i) ? '1' : '0'; c._paint && c._paint(); } }
    refresh();
    dialog('Programación Horaria', null, [['Cancelar'], ['Guardar', () => {
      const days = []; for (let i = 1; i <= 7; i++) { const c = body.querySelector(`[data-id="chipDay${i}"]`); if (c && c.dataset.checked === '1') days.push(i); }
      const err = body.querySelector('[data-id="txtScheduleError"]');
      const fail = (t) => { if (err) { err.style.display = ''; (err.querySelector('.txt') || err).textContent = t; } return false; };
      if (!days.length) return fail('Elige al menos un día.');
      if (start === end) return fail('La hora de inicio y de término no pueden ser iguales.');
      S.schedule = { start, end, days }; apply(); toast('Horario guardado: ' + describe(S.schedule));
    }]], null, body);
  }

  // ---- qué hace cada botón (setOnClickListener) ----
  const HANDLERS = {
    btnConnect: () => (S.connected ? VM.disconnect() : bluetoothPicker()),
    itemSchedule: () => scheduleDialog(),
    btnRefresh: () => { VM.refresh(); toast('Datos de sensores actualizados'); },
    cardPIR: () => { if (S.connected) { toast(S.movement ? 'Simulación PIR: Sin movimiento' : 'Simulación PIR: Movimiento detectado'); VM.togglePIR(); } else toast('Conecta el dispositivo primero'); },
    btnVentToggle: () => { const open = !S.ventOpen; VM.toggleVent(open); toast(`Compuerta ${open ? 'Abierta' : 'Cerrada'}`); },
    btnSegAuto: () => VM.setAuto(true),
    btnSegManual: () => VM.setAuto(false),
    btnModeAuto: () => { VM.setAuto(true); toast('Modo de Operación: Automático'); },
    btnModeManual: () => { VM.setAuto(false); toast('Modo de Operación: Manual'); },
    btnOpenVent: () => { VM.toggleVent(true); toast('Compuerta Abierta'); },
    btnCloseVent: () => { VM.toggleVent(false); toast('Compuerta Cerrada'); },
  };
  const SWITCHES = { switchAutoMode: (v) => VM.setAuto(v), switchAutoMain: (v) => VM.setAuto(v), switchVentilation: (v) => VM.toggleVent(v) };

  function attach(screenEl) {
    root = screenEl;
    root.querySelectorAll('[data-id]').forEach((n) => {
      const id = n.dataset.id;
      let fn = HANDLERS[id];
      if (!fn && KT.dialogs[id]) fn = () => dialog(KT.dialogs[id].title, KT.dialogs[id].msg, [['Aceptar']]);
      if (!fn && KT.toasts[id]) fn = () => toast(KT.toasts[id]);
      if (fn) { n.classList.add('clickable'); n.addEventListener('click', (ev) => { ev.stopPropagation(); fn(); }); }
      if (SWITCHES[id]) { const i = n.querySelector('input'); if (i) i.addEventListener('change', () => SWITCHES[id](i.checked)); }
    });
    apply();
  }

  window.AndroidSim = { attach, hasHandler: (id) => !!(HANDLERS[id] || KT.dialogs[id] || KT.toasts[id]), toast };
})();
