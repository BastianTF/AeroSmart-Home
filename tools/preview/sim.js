/* Simulación del comportamiento de la app en la vista previa.
 * Copia la lógica de MainViewModel.kt, UserPreferences.kt y de los observeUiState() de cada pantalla,
 * para que los botones hagan lo mismo que en Android (paneles, diálogos, toasts, estados). */
(function () {
  const P = window.AndroidPreview;
  const S = {
    connected: false, loading: false, auto: true, ventOpen: false, movement: false,
    temp: 25, hum: 55, fan: 60, history: [], lastUpdate: null,
    rules: { temp: 26, hum: 70, presence: true },
    schedule: { start: 8 * 60, end: 22 * 60, days: [1, 2, 3, 4, 5, 6, 7] },
    profile: { name: 'Kay', phone: '+56 9 1234 5678', email: 'kay@aerosmart.cl' },
    settings: { notif: true, keep: false },
    users: [], // tabla usuarios de SQLite (en la vista previa vive solo en memoria)
    failed: 0, lockedUntil: 0,
  };
  const TEMP_MIN = 20, TEMP_MAX = 34, HUM_MIN = 40, HUM_MAX = 90;
  let root = null, stream = null;

  // ---------- MainViewModel ----------
  const rnd = (a, b) => a + Math.floor(Math.random() * (b - a + 1));
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const two = (n) => String(n).padStart(2, '0');
  function nowLabel() { const d = new Date(); return `${two(d.getHours())}:${two(d.getMinutes())}:${two(d.getSeconds())}`; }
  function simulateReading() {
    S.temp = clamp(S.temp + rnd(-1, 1), 18, 34);
    S.hum = clamp(S.hum + rnd(-3, 3), 30, 90);
    S.lastUpdate = nowLabel();
    S.history.push({ t: S.temp, h: S.hum, label: S.lastUpdate.slice(0, 5) });
    while (S.history.length > 20) S.history.shift();
  }
  function insideSchedule() {
    const d = new Date();
    const day = ((d.getDay() + 6) % 7) + 1; // lunes = 1 … domingo = 7
    const m = d.getHours() * 60 + d.getMinutes();
    const s = S.schedule;
    if (s.start <= s.end) return s.days.includes(day) && m >= s.start && m < s.end;
    const prev = day === 1 ? 7 : day - 1;
    return (s.days.includes(day) && m >= s.start) || (s.days.includes(prev) && m < s.end);
  }
  function evaluateAutomation() {
    if (!S.auto || !S.connected) return;
    if (!insideSchedule()) { S.ventOpen = false; return; }
    const climate = S.temp >= S.rules.temp || S.hum >= S.rules.hum;
    S.ventOpen = climate && (!S.rules.presence || S.movement);
  }
  let lastMovement = false;
  function emit() {
    // MainActivity: notificación cuando aparece movimiento
    const moving = S.connected && S.movement;
    if (moving && !lastMovement && S.settings.notif) notify('Movimiento detectado', 'El sensor PIR detectó presencia en la habitación.');
    lastMovement = moving;
    apply();
  }
  const VM = {
    connect() {
      if (S.connected) return;
      S.loading = true; apply();
      setTimeout(() => {
        S.loading = false; S.connected = true; S.movement = true; S.history = [];
        simulateReading(); evaluateAutomation(); emit();
        clearInterval(stream);
        stream = setInterval(() => { if (!S.connected) return; simulateReading(); evaluateAutomation(); emit(); }, 5000);
      }, 1000);
    },
    disconnect() { clearInterval(stream); S.connected = false; S.ventOpen = false; S.movement = false; S.lastUpdate = null; emit(); },
    togglePIR() { if (!S.connected) return; S.movement = !S.movement; evaluateAutomation(); emit(); },
    refresh() { if (!S.connected) return; simulateReading(); evaluateAutomation(); emit(); },
    setAuto(v) { S.auto = v; evaluateAutomation(); emit(); },
    toggleVent(open) { S.auto = false; S.ventOpen = open; emit(); },
    setFan(p) { S.fan = clamp(Math.floor(p / 20) * 20, 20, 100); emit(); },
    setRules(r) { S.rules = r; evaluateAutomation(); emit(); },
    setSchedule(s) { S.schedule = s; evaluateAutomation(); emit(); },
  };
  const comfort = () => (!S.connected ? 'Sin conexión' : S.temp >= S.rules.temp ? 'Caluroso' : S.temp <= 18 ? 'Frío' : 'Confort ideal');

  // ---------- ayudas de vista ----------
  const q = (id, scope) => { const r = scope || root; return r ? Array.from(r.querySelectorAll(`[data-id="${id}"]`)) : []; };
  const one = (id, scope) => q(id, scope)[0];
  function setText(id, t, scope) { q(id, scope).forEach((n) => { const s = n.querySelector('.txt') || n; s.textContent = t; }); }
  function setBg(id, ref, scope) {
    const d = P.drawable(ref);
    q(id, scope).forEach((n) => { n.style.background = ''; n.style.border = ''; n.style.borderRadius = ''; if (d && d.css) Object.assign(n.style, d.css); });
  }
  function setCardBg(id, ref) { const c = P.color(ref); q(id).forEach((n) => { n.style.backgroundColor = c; }); }
  function setChecked(id, v, scope) { q(id, scope).forEach((n) => { const i = n.querySelector('input'); if (i) i.checked = v; }); }
  function show(id, v, scope) { q(id, scope).forEach((n) => { n.style.display = v ? '' : 'none'; }); }
  function check(groupId, btnId) { q(groupId).forEach((g) => g._check && g._check(btnId)); }
  const str = (n) => (window.RES.strings[n] || n);
  const fmt = (m) => two(Math.floor(m / 60)) + ':' + two(m % 60);
  function greeting(name) {
    const h = new Date().getHours();
    const base = h >= 5 && h <= 11 ? 'Buenos días' : h >= 12 && h <= 19 ? 'Buenas tardes' : 'Buenas noches';
    const first = (name || '').trim().split(' ')[0];
    return first ? `${base}, ${first}` : base;
  }
  function describe(s) {
    const names = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
    const d = [...s.days].sort();
    const days = d.length === 7 ? 'Todos los días' : d.join() === '1,2,3,4,5' ? 'Lun a Vie' : d.join() === '6,7' ? 'Fines de semana' : d.map((i) => names[i - 1]).join(', ');
    return `${fmt(s.start)} – ${fmt(s.end)}${s.end < s.start ? ' (+1 día)' : ''} · ${days}`;
  }
  function initials(name) {
    const p = (name || '').trim().split(/\s+/).filter(Boolean);
    return !p.length ? '?' : p.length === 1 ? p[0][0].toUpperCase() : (p[0][0] + p[1][0]).toUpperCase();
  }

  // ---------- gráfico (SparklineView.kt) ----------
  function drawChart(host) {
    const w = host.clientWidth || 300, h = host.clientHeight || 160;
    const pl = 8, pr = 8, pt = 8, pb = 20;
    const H = S.history;
    const gx = (i) => pl + (H.length < 2 ? 0 : (i * (w - pl - pr)) / (H.length - 1));
    const gy = (v, lo, hi) => pt + (1 - (clamp(v, lo, hi) - lo) / (hi - lo)) * (h - pt - pb);
    let svg = `<svg viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" role="img" aria-label="Historial de temperatura y humedad">`;
    for (let i = 0; i <= 3; i++) { const y = pt + (i * (h - pt - pb)) / 3; svg += `<line x1="${pl}" x2="${w - pr}" y1="${y}" y2="${y}" stroke="#E2EEF2" stroke-width="1"/>`; }
    if (H.length >= 2) {
      const line = (key, lo, hi, col) => {
        const pts = H.map((r, i) => `${gx(i).toFixed(1)},${gy(r[key], lo, hi).toFixed(1)}`);
        const area = `M${pl},${h - pb} L${pts.join(' L')} L${gx(H.length - 1)},${h - pb} Z`;
        return `<path d="${area}" fill="${col}" fill-opacity=".08"/><polyline points="${pts.join(' ')}" fill="none" stroke="${col}" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round"/><circle cx="${gx(H.length - 1)}" cy="${gy(H[H.length - 1][key], lo, hi)}" r="4" fill="${col}" stroke="#fff" stroke-width="2"/>`;
      };
      svg += line('h', 20, 100, P.color('@color/primary')) + line('t', 15, 35, P.color('@color/sun_dark'));
      svg += `<text x="${pl}" y="${h - 4}" font-size="10" fill="#8A9AA3" font-family="Roboto, sans-serif">${H[0].label}</text>`;
      svg += `<text x="${w - pr}" y="${h - 4}" font-size="10" fill="#8A9AA3" text-anchor="end" font-family="Roboto, sans-serif">${H[H.length - 1].label}</text>`;
    }
    host.innerHTML = svg + '</svg>';
  }

  // ---------- observeUiState() de cada pantalla ----------
  function apply() {
    if (!root) return;
    const onMonitoring = !!one('chartHistory');
    const onVentTab = !!one('toggleVent');
    // ConnectionUi.render
    if (S.loading) { setText('txtStatus', 'Conectando…'); setText('txtStatusHint', 'Buscando el módulo Arduino'); setBg('viewStatusDot', '@drawable/circle_gray'); }
    else if (S.connected) { setText('txtStatus', str('status_connected')); setText('txtStatusHint', S.lastUpdate ? `Última lectura ${S.lastUpdate}` : 'Recibiendo datos'); setBg('viewStatusDot', '@drawable/circle_green'); }
    else { setText('txtStatus', str('status_disconnected')); setText('txtStatusHint', 'Vincula tu Arduino para ver datos en vivo'); setBg('viewStatusDot', '@drawable/circle_red'); }
    if (!S.loading) setText('btnConnect', S.connected ? str('disconnect_bluetooth') : str('connect'));
    q('btnConnect').forEach((n) => { n.style.opacity = S.loading ? '.5' : ''; n.style.pointerEvents = S.loading ? 'none' : ''; });
    setText('txtGreeting', greeting(S.profile.name));
    // Sensores
    setText('txtTemperature', S.connected ? `${S.temp} °C` : '-- °C');
    setText('txtHumidity', S.connected ? `${S.hum} %` : '-- %');
    setText('txtComfort', comfort());
    q('gaugeHumidity').forEach((g) => g._setProgress && g._setProgress(S.connected ? S.hum : 0));
    setText('txtLastUpdate', S.lastUpdate ? `Actualizado a las ${S.lastUpdate}` : 'Sin lecturas todavía');
    const mov = S.connected && S.movement;
    if (onMonitoring) setText('txtPIRStatus', !S.connected ? 'Sin conexión' : mov ? 'Movimiento detectado · toca para simular' : 'Sin movimiento · toca para simular');
    else setText('txtPIRStatus', mov ? 'Movimiento detectado' : 'Sin movimiento');
    setBg('viewLedPIR', mov ? '@drawable/circle_green' : '@drawable/circle_gray');
    // Monitoreo
    if (onMonitoring) {
      const H = S.history;
      const range = (k) => (H.length ? `Mín ${Math.min(...H.map((r) => r[k]))} · Máx ${Math.max(...H.map((r) => r[k]))}` : 'Mín -- · Máx --');
      setText('txtTempRange', range('t')); setText('txtHumRange', range('h'));
      setText('txtReadingsCount', H.length === 1 ? '1 lectura' : `${H.length} lecturas`);
      show('txtChartEmpty', H.length < 2);
      setText('txtChartEmpty', S.connected ? 'Esperando más lecturas…' : 'Conecta el dispositivo para ver las lecturas en vivo');
      const c = one('chartHistory'); if (c) requestAnimationFrame(() => drawChart(c));
    }
    // Modo automático
    setChecked('switchAutoMode', S.auto); setChecked('switchAutoMain', S.auto);
    setText('txtAutoStatus', S.auto ? str('auto_mode_desc') : 'Modo manual activado');
    setText('txtAutoStatusHeader', S.auto ? 'Automático activado' : 'Modo manual');
    setText('txtAutoDesc', S.auto ? 'La compuerta se abre sola según tus reglas y horario.' : 'Tú decides cuándo abrir o cerrar la compuerta.');
    setBg('dotAuto', S.auto ? '@drawable/circle_green' : '@drawable/circle_gray');
    check('toggleAutoMode', S.auto ? 'btnSegAuto' : 'btnSegManual');
    check('toggleOpMode', S.auto ? 'btnModeAuto' : 'btnModeManual');
    setText('txtRuleTemp', `Abre desde ${S.rules.temp} °C`);
    setText('txtRuleHumidity', `Abre desde ${S.rules.hum} %`);
    setText('txtRulePIR', S.rules.presence ? 'Solo si hay personas' : 'Con o sin personas');
    setText('txtScheduleDesc', describe(S.schedule));
    setText('txtScheduleState', insideSchedule() ? 'Dentro del horario ahora' : 'Fuera del horario ahora');
    // Compuerta y turbina
    if (onVentTab) {
      setText('txtServoStatus', S.ventOpen ? 'Compuerta abierta' : 'Compuerta cerrada');
      setText('txtVentSub', S.ventOpen ? `Ventilando al ${S.fan} %` : 'La turbina está detenida');
      check('toggleVent', S.ventOpen ? 'btnOpenVent' : 'btnCloseVent');
      setText('txtFanSpeed', `${S.fan} %`);
      q('seekFanSpeed').forEach((n) => { const r = n.querySelector('input'); if (r && +r.value !== S.fan) r.value = S.fan; paintSlider(r); });
      for (let i = 1; i <= 5; i++) setBg('bar' + i, i <= S.fan / 20 ? '@drawable/bg_bar_on' : '@drawable/bg_bar_off');
      q('imgFan').forEach((n) => { n.classList.toggle('fan-spin', S.ventOpen); n.style.setProperty('--spin', Math.max(0.35, (2.4 * 20) / S.fan) + 's'); });
    } else {
      setText('txtServoStatus', S.ventOpen ? 'Abierta' : 'Cerrada');
      setCardBg('btnVentToggle', S.ventOpen ? '@color/primary_container' : '@color/card_bg');
    }
  }
  function paintSlider(r) {
    if (!r) return;
    const pct = ((r.value - r.min) / (r.max - r.min)) * 100;
    r.style.setProperty('--pct', pct + '%');
  }

  // ---------- toasts, diálogos, paneles y notificaciones ----------
  function toast(msg) {
    if (!root) return;
    root.querySelectorAll('.sim-toast').forEach((t) => t.remove());
    const t = document.createElement('div'); t.className = 'sim-toast'; t.setAttribute('role', 'status'); t.textContent = msg;
    root.append(t); setTimeout(() => t.classList.add('out'), 1900); setTimeout(() => t.remove(), 2300);
  }
  function notify(title, text) {
    if (!root) return;
    root.querySelectorAll('.sim-notif').forEach((t) => t.remove());
    const n = document.createElement('div'); n.className = 'sim-notif'; n.setAttribute('role', 'status');
    const ic = P.drawable('@drawable/ic_walk');
    n.innerHTML = `<span class="sim-notif-ic">${ic && ic.svg ? ic.svg('#fff') : ''}</span><span><b></b><small></small></span>`;
    n.querySelector('b').textContent = 'AeroSmart Home · ' + title; n.querySelector('small').textContent = text;
    root.append(n); setTimeout(() => n.classList.add('out'), 3200); setTimeout(() => n.remove(), 3600);
  }
  function dialog(title, msg, buttons, items, content) {
    if (!root) return;
    const wrap = document.createElement('div'); wrap.className = 'sim-scrim';
    const box = document.createElement('div'); box.className = 'sim-dialog'; box.setAttribute('role', 'dialog'); box.setAttribute('aria-label', title);
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
    buttons.forEach(([label, fn, danger]) => { const b = document.createElement('button'); b.type = 'button'; b.textContent = label; if (danger) b.className = 'danger'; b.onclick = () => { if (fn && fn() === false) return; close(); }; row.append(b); });
    box.append(row); wrap.append(box);
    wrap.addEventListener('click', (e) => { if (e.target === wrap) close(); });
    root.append(wrap);
    return close;
  }
  // BottomSheetDialog con un layout real (sheet_*.xml)
  function sheet(layout) {
    const body = P.renderNode(layout, { screen: 'sheet' });
    const wrap = document.createElement('div'); wrap.className = 'sim-sheet-scrim';
    const box = document.createElement('div'); box.className = 'sim-sheet'; box.setAttribute('role', 'dialog');
    box.append(body); wrap.append(box);
    const close = () => { wrap.classList.add('out'); setTimeout(() => wrap.remove(), 220); };
    wrap.addEventListener('click', (e) => { if (e.target === wrap) close(); });
    root.append(wrap);
    return { body, close };
  }
  function bluetoothPicker() {
    const devices = ['AeroSmart-ESP32 (Bluetooth BLE)', 'HC-05 (Bluetooth Classic)', 'Arduino Wi-Fi / MQTT Broker'];
    dialog('Elige tu dispositivo', null, [['Cancelar']], devices.map((d) => [d, () => { toast(`Vinculando con ${d}…`); VM.connect(); }]));
  }

  // Selector de hora tipo rueda (equivale a MaterialTimePicker en Android)
  function wheelPicker(title, minutes, onPick) {
    const wrap = document.createElement('div'); wrap.className = 'wheel-wrap';
    const mk = (count, step, val) => {
      const w = document.createElement('div'); w.className = 'wheel'; w.tabIndex = 0;
      w.innerHTML = '<div class="pad"></div>' + Array.from({ length: count }, (_, i) => `<div data-v="${i * step}">${two(i * step)}</div>`).join('') + '<div class="pad"></div>';
      const items = () => Array.from(w.querySelectorAll('[data-v]'));
      const pick = () => { const idx = Math.round(w.scrollTop / 48); items().forEach((it, i) => it.classList.toggle('sel', i === idx)); return +(items()[clamp(idx, 0, count - 1)].dataset.v); };
      w._value = pick;
      w.addEventListener('scroll', () => pick(), { passive: true });
      w.addEventListener('click', (e) => { const it = e.target.closest('[data-v]'); if (it) w.scrollTo({ top: items().indexOf(it) * 48, behavior: 'smooth' }); });
      w.addEventListener('keydown', (e) => { if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { e.preventDefault(); w.scrollBy({ top: e.key === 'ArrowDown' ? 48 : -48, behavior: 'smooth' }); } });
      requestAnimationFrame(() => { w.scrollTop = Math.round(val / step) * 48; pick(); });
      return w;
    };
    const hw = mk(24, 1, Math.floor(minutes / 60)), mw = mk(12, 5, minutes % 60);
    hw.setAttribute('aria-label', 'Horas'); mw.setAttribute('aria-label', 'Minutos');
    const colon = document.createElement('span'); colon.className = 'wheel-colon'; colon.textContent = ':';
    wrap.append(hw, colon, mw);
    const sub = document.createElement('p'); sub.className = 'sim-sub'; sub.textContent = 'Desliza para elegir la hora';
    const content = document.createElement('div'); content.append(sub, wrap);
    dialog(title, null, [['Cancelar'], ['Aceptar', () => onPick(hw._value() * 60 + mw._value())]], null, content);
  }

  // ---------- AutomationFragment: reglas ----------
  function ruleSheet(kind) {
    const { body, close } = sheet('sheet_rule');
    const cfg = {
      temp: ['Regla de temperatura', 'La compuerta se abre cuando la temperatura llega a este valor.', TEMP_MIN, TEMP_MAX, S.rules.temp, '°C'],
      hum: ['Regla de humedad', 'La compuerta se abre cuando la humedad llega a este valor.', HUM_MIN, HUM_MAX, S.rules.hum, '%'],
    }[kind];
    const slider = one('sliderRule', body)?.querySelector('input');
    if (cfg) {
      const [title, desc, min, max, val, unit] = cfg;
      setText('txtRuleTitle', title, body); setText('txtRuleDesc', desc, body);
      setText('txtRuleMin', `${min} ${unit}`, body); setText('txtRuleMax', `${max} ${unit}`, body);
      if (slider) {
        slider.min = min; slider.max = max; slider.step = 1; slider.value = val; paintSlider(slider);
        const upd = () => { setText('txtRuleValue', `${slider.value} ${unit}`, body); paintSlider(slider); };
        slider.addEventListener('input', upd); upd();
      }
    } else {
      setText('txtRuleTitle', 'Regla de presencia', body);
      setText('txtRuleDesc', 'Usa el sensor PIR para ventilar solo cuando hay alguien en la habitación. Ahorra energía.', body);
      show('layoutRuleSlider', false, body); show('layoutRuleSwitch', true, body);
      setChecked('switchRule', S.rules.presence, body);
    }
    one('btnSaveRule', body)?.addEventListener('click', () => {
      const r = { ...S.rules };
      if (kind === 'temp') r.temp = +slider.value;
      if (kind === 'hum') r.hum = +slider.value;
      if (kind === 'presence') r.presence = !!one('switchRule', body)?.querySelector('input')?.checked;
      VM.setRules(r); toast('Regla guardada'); close();
    });
  }

  // ---------- AutomationFragment: horario ----------
  function scheduleSheet() {
    const { body, close } = sheet('sheet_schedule');
    let start = S.schedule.start, end = S.schedule.end;
    const chips = [1, 2, 3, 4, 5, 6, 7].map((i) => one('chipDay' + i, body));
    const setDay = (c, on) => { if (c) { c.dataset.checked = on ? '1' : '0'; c._paint && c._paint(); } };
    chips.forEach((c, i) => setDay(c, S.schedule.days.includes(i + 1)));
    const refresh = () => { setText('txtStartTime', fmt(start), body); setText('txtEndTime', fmt(end), body); };
    refresh();
    const on = (id, fn) => { const n = one(id, body); if (n) { n.classList.add('clickable', 'press'); n.addEventListener('click', (e) => { e.stopPropagation(); fn(); }); } };
    on('cardStartTime', () => wheelPicker('Hora de inicio', start, (v) => { start = v; refresh(); }));
    on('cardEndTime', () => wheelPicker('Hora de término', end, (v) => { end = v; refresh(); }));
    on('chipPresetOffice', () => { start = 8 * 60; end = 18 * 60; chips.forEach((c, i) => setDay(c, i < 5)); refresh(); });
    on('chipPresetNight', () => { start = 22 * 60; end = 6 * 60; refresh(); });
    on('chipPresetAllDay', () => { start = 0; end = 23 * 60 + 59; refresh(); });
    on('btnSaveSchedule', () => {
      const days = chips.map((c, i) => (c && c.dataset.checked === '1' ? i + 1 : 0)).filter(Boolean);
      const fail = (t) => { setText('txtScheduleError', t, body); show('txtScheduleError', true, body); };
      if (!days.length) return fail('Elige al menos un día.');
      if (start === end) return fail('La hora de inicio y de término no pueden ser iguales.');
      VM.setSchedule({ start, end, days }); toast('Horario guardado: ' + describe(S.schedule)); close();
    });
  }

  // ---------- Perfil ----------
  const inputOf = (editId) => root && root.querySelector(`input[data-edit="${editId}"]`);
  function fillProfile() {
    setText('txtAvatarInitials', initials(S.profile.name));
    setText('txtProfileDisplayName', S.profile.name || 'Usuario AeroSmart');
    setText('txtProfileEmail', S.profile.email || 'Sin correo registrado');
  }
  function normalizeCl(v) {
    let d = v.replace(/\D/g, '');
    if (d.length === 11 && d.startsWith('56')) d = d.slice(2);
    if (d.length !== 9 || d[0] !== '9') return null;
    return `+56 ${d[0]} ${d.slice(1, 5)} ${d.slice(5)}`;
  }
  const validName = (n) => n.trim().length >= 2 && n.trim().length <= 60 && /^[\p{L} '\-]+$/u.test(n.trim());
  const validEmail = (e) => /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(e);
  function saveProfile() {
    const name = (inputOf('etProfileName')?.value || '').trim();
    const phone = normalizeCl(inputOf('etProfilePhone')?.value || '');
    const tN = one('tilProfileName'), tP = one('tilProfilePhone');
    tN?._setError(validName(name) ? null : 'Escribe tu nombre (solo letras)');
    tP?._setError(phone ? null : 'Formato: +56 9 1234 5678');
    if (!validName(name) || !phone) return;
    inputOf('etProfilePhone').value = phone;
    S.profile.name = name; S.profile.phone = phone; fillProfile(); toast('Datos guardados');
  }
  function comingSoon(provider, what) {
    dialog(`${what} con ${provider}`, `El ${what.toLowerCase()} con ${provider} estará disponible próximamente. Por ahora usa tu correo y contraseña.`, [['Entendido']]);
  }

  // ---------- qué hace cada botón (setOnClickListener) ----------
  const HANDLERS = {
    btnConnect: () => (S.connected ? VM.disconnect() : bluetoothPicker()),
    btnRefresh: () => { if (S.connected) { VM.refresh(); toast('Datos de sensores actualizados'); } else toast('Conecta el dispositivo primero'); },
    cardPIR: () => { if (S.connected) VM.togglePIR(); else toast('Conecta el dispositivo para simular presencia PIR'); },
    btnVentToggle: () => { const open = !S.ventOpen; VM.toggleVent(open); toast(open ? 'Compuerta abierta' : 'Compuerta cerrada'); },
    btnSegAuto: () => VM.setAuto(true),
    btnSegManual: () => VM.setAuto(false),
    btnModeAuto: () => VM.setAuto(true),
    btnModeManual: () => VM.setAuto(false),
    btnOpenVent: () => { VM.toggleVent(true); toast('Compuerta abierta (modo manual)'); },
    btnCloseVent: () => { VM.toggleVent(false); toast('Compuerta cerrada (modo manual)'); },
    ruleTemp: () => ruleSheet('temp'),
    ruleHumidity: () => ruleSheet('hum'),
    rulePIR: () => ruleSheet('presence'),
    itemSchedule: () => scheduleSheet(),
    btnSaveProfile: () => saveProfile(),
    rowNotifications: () => { const i = one('switchNotifications')?.querySelector('input'); if (i) { i.checked = !i.checked; SWITCHES.switchNotifications(i.checked); } },
    rowKeepScreen: () => { const i = one('switchKeepScreen')?.querySelector('input'); if (i) { i.checked = !i.checked; SWITCHES.switchKeepScreen(i.checked); } },
    txtTerms: () => dialog('Términos y privacidad', '• AeroSmart Home usa las lecturas de temperatura, humedad y presencia solo para controlar la ventilación de tu hogar.\n\n• Tu nombre, correo y celular se guardan en este teléfono y se usan para las alertas.\n\n• No vendemos ni compartimos tus datos con terceros.\n\n• Puedes borrar tus datos cerrando sesión o desinstalando la app.', [['Entendido']]),
    txtForgotPassword: () => {
      const box = document.createElement('div'); box.style.padding = '0 24px';
      box.innerHTML = '<label class="til-box outlined" style="--til-bg:#fff"><input type="email" placeholder=" " aria-label="Correo"><span class="til-label">Correo electrónico</span></label>';
      dialog('Recuperar contraseña', 'Te enviaremos un enlace para crear una nueva contraseña.', [['Cancelar'], ['Enviar enlace', () => {
        const v = box.querySelector('input').value.trim();
        if (!validEmail(v)) { toast('Por favor ingresa un correo válido'); return false; }
        toast(`Enlace de recuperación enviado a ${v}`);
      }]], null, box);
    },
    btnGoogle: () => comingSoon('Google', 'Inicio'), btnFacebook: () => comingSoon('Facebook', 'Inicio'), btnApple: () => comingSoon('Apple', 'Inicio'),
    btnRegGoogle: () => comingSoon('Google', 'Registro'), btnRegFacebook: () => comingSoon('Facebook', 'Registro'), btnRegApple: () => comingSoon('Apple', 'Registro'),
  };
  const SWITCHES = {
    switchAutoMode: (v) => VM.setAuto(v),
    switchAutoMain: (v) => VM.setAuto(v),
    switchNotifications: (v) => { S.settings.notif = v; toast(v ? 'Alertas de presencia activadas' : 'Alertas de presencia desactivadas'); },
    switchKeepScreen: (v) => { S.settings.keep = v; toast(v ? 'La pantalla se mantendrá encendida' : 'La pantalla se apagará normalmente'); },
  };

  // LoginActivity / RegisterActivity / ProfileActivity (los llama la navegación de la vista previa)
  function login(next) {
    const email = (inputOf('edtEmail')?.value || '').trim(), pass = inputOf('edtPassword')?.value || '';
    if (!validEmail(email)) return toast('Ingresa un correo válido');
    if (pass.length < 6) return toast('La contraseña debe tener al menos 6 caracteres');
    if (Date.now() < S.lockedUntil) return toast(`Demasiados intentos. Espera ${Math.ceil((S.lockedUntil - Date.now()) / 1000)} s`);
    const u = S.users.find((x) => x.email === email.toLowerCase() && x.pass === pass);
    if (!u) {
      if (++S.failed >= 5) { S.lockedUntil = Date.now() + 30000; S.failed = 0; }
      return toast(S.users.length ? 'Correo o contraseña incorrectos' : 'Correo o contraseña incorrectos. ¿Ya creaste tu cuenta en Regístrate?');
    }
    S.failed = 0;
    const b = one('btnLogin'); setText('btnLogin', 'Iniciando…'); if (b) b.style.pointerEvents = 'none';
    setTimeout(() => { S.profile = { name: u.name, phone: u.phone, email: u.email }; next(); }, 600);
  }
  function register(next) {
    const v = (id) => (inputOf(id)?.value || '').trim();
    const first = v('etFirstName'), last = v('etLastName'), phone = normalizeCl(v('etPhone')), email = v('etRegEmail'), p1 = inputOf('etRegPassword')?.value || '', p2 = inputOf('etConfirmPassword')?.value || '';
    if (!first || !last) return toast('Ingresa tu nombre y apellido');
    if (!validName(`${first} ${last}`)) return toast('El nombre solo puede tener letras');
    if (!phone) return toast('Ingresa un celular válido: +56 9 1234 5678');
    if (!validEmail(email)) return toast('Ingresa un correo electrónico válido');
    if (p1.length < 8) return toast('La contraseña debe tener al menos 8 caracteres');
    if (p1 !== p2) return toast('Las contraseñas no coinciden. Verifícala.');
    const box = document.createElement('div'); box.style.padding = '0 24px';
    box.innerHTML = '<label class="til-box outlined" style="--til-bg:#fff"><input inputmode="numeric" maxlength="4" placeholder=" " aria-label="Código"><span class="til-label">Código de 4 dígitos</span></label>';
    dialog('Verifica tu correo', `Enviamos un código a ${email}. (Código de prueba: 1234)`, [['Cancelar'], ['Verificar', () => {
      if (box.querySelector('input').value.trim() !== '1234') { toast('Código incorrecto. Inténtalo de nuevo.'); return false; }
      if (S.users.some((x) => x.email === email.toLowerCase())) { toast('Ese correo ya tiene una cuenta. Inicia sesión.'); return; }
      S.users.push({ name: `${first} ${last}`, phone, email: email.toLowerCase(), pass: p1 });
      S.profile = { name: `${first} ${last}`, phone, email: email.toLowerCase() };
      toast('¡Cuenta creada! Bienvenido/a'); setTimeout(next, 300);
    }]], null, box);
  }
  function logout(next) {
    dialog('¿Cerrar sesión?', 'Tendrás que volver a ingresar tu correo y contraseña.', [['Cancelar'], ['Cerrar sesión', () => { S.profile.email = ''; VM.disconnect(); setTimeout(next, 160); }, true]]);
  }

  function attach(screenEl) {
    root = screenEl;
    root.querySelectorAll('[data-id]').forEach((n) => {
      const id = n.dataset.id;
      const fn = HANDLERS[id];
      if (fn) { n.classList.add('clickable'); n.addEventListener('click', (ev) => { ev.stopPropagation(); fn(); }); }
      if (SWITCHES[id]) { const i = n.querySelector('input'); if (i) { i.addEventListener('click', (e) => e.stopPropagation()); i.addEventListener('change', () => SWITCHES[id](i.checked)); } }
      if (id === 'seekFanSpeed') { const r = n.querySelector('input'); if (r) { r.addEventListener('input', () => { paintSlider(r); setText('txtFanSpeed', `${Math.floor(r.value / 20) * 20} %`); }); r.addEventListener('change', () => VM.setFan(+r.value)); } }
    });
    // Datos guardados en UserPreferences
    if (inputOf('etProfileName')) {
      inputOf('etProfileName').value = S.profile.name; inputOf('etProfilePhone').value = S.profile.phone;
      setChecked('switchNotifications', S.settings.notif); setChecked('switchKeepScreen', S.settings.keep);
      fillProfile();
    }
    setText('txtAvatarInitials', initials(S.profile.name));
    apply();
  }

  window.AndroidSim = { attach, hasHandler: (id) => !!HANDLERS[id], toast, login, register, logout };
})();
