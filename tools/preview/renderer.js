/* Intérprete simple de layouts XML de Android -> HTML/CSS.
 * 1dp = 1px dentro de un teléfono de 360 x 780 dp.
 * Cubre los widgets que usa este proyecto. Es una aproximación. */
(function () {
  const NS_A = 'http://schemas.android.com/apk/res/android';
  const NS_APP = 'http://schemas.android.com/apk/res-auto';
  const NS_AAPT = 'http://schemas.android.com/aapt';
  // Valores por defecto de Theme.Material3.Light
  const THEME = { colorPrimary: '#6750A4', onPrimary: '#FFFFFF', text: '#1D1B20', textSecondary: '#49454F', outline: '#79747E' };
  const R = window.RES;
  R.styles = R.styles || {};
  function styleChain(name) {
    const out = [];
    let n = name, guard = 0;
    while (n && R.styles[n] && guard++ < 10) {
      out.push(R.styles[n]);
      const st = R.styles[n];
      n = st.parent ? st.parent.replace(/^@style\//, '') : (n.includes('.') ? n.slice(0, n.lastIndexOf('.')) : null);
    }
    return out; // del más específico al más general
  }
  function styleItems(name) {
    const merged = {};
    styleChain(name).reverse().forEach((st) => Object.assign(merged, st.items));
    return merged;
  }
  const appTheme = styleItems('Theme.VentiladorAmbiental');
  const warnings = new Set();

  const parser = new DOMParser();
  const xmlCache = {};

  function parseXml(text) {
    if (xmlCache[text]) return xmlCache[text];
    const doc = parser.parseFromString(text, 'application/xml');
    const err = doc.querySelector('parsererror');
    if (err) throw new Error(err.textContent.slice(0, 200));
    return (xmlCache[text] = doc.documentElement);
  }
  const a = (el, n) => (el.hasAttributeNS(NS_A, n) ? el.getAttributeNS(NS_A, n) : null);
  const app = (el, n) => (el.hasAttributeNS(NS_APP, n) ? el.getAttributeNS(NS_APP, n) : null);
  const kids = (el) => Array.from(el.children);

  // ---------- recursos ----------
  const SYS_COLORS = { white: '#FFFFFF', black: '#000000', transparent: '#00000000', darker_gray: '#AAAAAA' };
  function color(v, depth = 0) {
    if (v == null || depth > 5) return null;
    v = v.trim();
    if (v.startsWith('@android:color/')) return color(SYS_COLORS[v.slice(15)] || '#000', depth + 1);
    if (v.startsWith('@color/')) {
      const n = v.slice(7);
      if (R.colors[n] != null) return color(R.colors[n], depth + 1);
      if (R.colorLists[n]) {
        const items = kids(parseXml(R.colorLists[n]));
        const def = items.find((i) => !Array.from(i.attributes).some((x) => x.name.includes('state_'))) || items[0];
        return color(a(def, 'color'), depth + 1);
      }
      warnings.add(`Color no definido: ${v}`);
      return '#FF00FF';
    }
    if (v.startsWith('?')) return v.includes('colorPrimary') ? THEME.colorPrimary : null;
    const m = v.match(/^#([0-9a-f]+)$/i);
    if (!m) return null;
    let h = m[1];
    if (h.length === 3) h = 'F' + h;
    if (h.length === 4) h = h.split('').map((c) => c + c).join('');
    if (h.length === 6) h = 'FF' + h;
    const al = parseInt(h.slice(0, 2), 16) / 255;
    const r = parseInt(h.slice(2, 4), 16), g = parseInt(h.slice(4, 6), 16), b = parseInt(h.slice(6, 8), 16);
    return al >= 1 ? `rgb(${r},${g},${b})` : `rgba(${r},${g},${b},${+al.toFixed(3)})`;
  }
  // Color según estado (para selectores de color con state_checked)
  function colorFor(v, checked) {
    if (v && v.startsWith('@color/') && R.colorLists[v.slice(7)]) {
      const items = kids(parseXml(R.colorLists[v.slice(7)]));
      const hit = items.find((i) => (i.getAttributeNS(NS_A, 'state_checked') === 'true') === !!checked && (checked || !i.hasAttributeNS(NS_A, 'state_checked'))) || items[items.length - 1];
      return color(a(hit, 'color'));
    }
    return color(v);
  }
  function dim(v) {
    if (v == null) return null;
    const m = String(v).match(/^(-?[\d.]+)(dp|dip|sp|px|pt)?$/);
    return m ? parseFloat(m[1]) : null;
  }
  function str(v) {
    if (v == null) return '';
    if (v.startsWith('@string/')) {
      const n = v.slice(8);
      if (R.strings[n] == null) { warnings.add(`Texto no definido: ${v}`); return n; }
      v = R.strings[n];
    }
    return v.replace(/\\n/g, '\n').replace(/\\'/g, "'").replace(/\\"/g, '"');
  }

  // Íconos del sistema Android usados en los layouts (aproximados con Material Icons)
  const SYS_DRAWABLES = {
    ic_dialog_email: 'M20,4H4C2.9,4 2,4.9 2,6v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6C22,4.9 21.1,4 20,4zM20,8l-8,5L4,8V6l8,5l8,-5V8z',
    ic_lock_lock: 'M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10C20,8.9 19.1,8 18,8zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2s2,0.9 2,2S13.1,17 12,17zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1s3.1,1.39 3.1,3.1V8z',
    ic_popup_reminder: 'M12,22c1.1,0 2,-0.9 2,-2h-4C10,21.1 10.9,22 12,22zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5S10.5,3.17 10.5,4v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1L18,16z',
  };

  // Devuelve {kind:'vector'|'img'|'shape'|'color', ...}
  function drawable(ref, depth = 0) {
    if (!ref || depth > 4) return null;
    if (ref.startsWith('#') || ref.startsWith('@color/') || ref.startsWith('@android:color/')) return { kind: 'color', color: color(ref) };
    if (ref.startsWith('@android:drawable/')) {
      const p = SYS_DRAWABLES[ref.slice(18)];
      return p ? { kind: 'vector', w: 24, h: 24, svg: (tint) => `<svg viewBox="0 0 24 24" width="100%" height="100%"><path fill="${tint || '#757575'}" d="${p}"/></svg>` } : null;
    }
    if (!ref.startsWith('@drawable/') && !ref.startsWith('@mipmap/')) return null;
    const name = ref.split('/')[1];
    const d = R.drawables[name];
    if (!d) { warnings.add(`Drawable no encontrado: ${ref}`); return null; }
    if (d.type === 'img') return { kind: 'img', src: d.src };
    const root = parseXml(d.text);
    return xmlDrawable(root, depth);
  }
  function xmlDrawable(root, depth) {
    const t = root.localName;
    if (t === 'vector') return vectorDrawable(root);
    if (t === 'shape') return { kind: 'shape', css: shapeCss(root), w: null, h: null, ...shapeSize(root) };
    if (t === 'ripple' || t === 'layer-list' || t === 'selector' || t === 'inset') {
      const items = kids(root).filter((k) => k.localName === 'item' && !(t === 'ripple' && a(k, 'id') === '@android:id/mask'));
      if (t === 'selector') {
        const def = items.find((i) => !Array.from(i.attributes).some((x) => x.name.includes('state_'))) || items[items.length - 1];
        return def ? itemDrawable(def, depth) : null;
      }
      if (t === 'inset') return a(root, 'drawable') ? drawable(a(root, 'drawable'), depth + 1) : (kids(root)[0] ? xmlDrawable(kids(root)[0], depth + 1) : null);
      return items[0] ? itemDrawable(items[0], depth) : null;
    }
    if (t === 'bitmap') return drawable(a(root, 'src'), depth + 1);
    return null;
  }
  function itemDrawable(item, depth) {
    if (a(item, 'drawable')) return drawable(a(item, 'drawable'), depth + 1);
    const c = kids(item)[0];
    return c ? xmlDrawable(c, depth + 1) : null;
  }
  function shapeSize(root) {
    const s = kids(root).find((k) => k.localName === 'size');
    return s ? { w: dim(a(s, 'width')), h: dim(a(s, 'height')) } : {};
  }
  function shapeCss(root) {
    const css = {};
    const shape = a(root, 'shape') || 'rectangle';
    for (const k of kids(root)) {
      if (k.localName === 'solid') css.backgroundColor = color(a(k, 'color'));
      if (k.localName === 'gradient') {
        const cols = [a(k, 'startColor'), a(k, 'centerColor'), a(k, 'endColor')].filter(Boolean).map((c) => color(c));
        if ((a(k, 'type') || 'linear') === 'radial') css.backgroundImage = `radial-gradient(circle, ${cols.join(',')})`;
        else css.backgroundImage = `linear-gradient(${90 - parseFloat(a(k, 'angle') || 0)}deg, ${cols.join(',')})`;
      }
      if (k.localName === 'corners') {
        const r = dim(a(k, 'radius')) || 0;
        const c = (n) => (dim(a(k, n)) ?? r) + 'px';
        css.borderRadius = `${c('topLeftRadius')} ${c('topRightRadius')} ${c('bottomRightRadius')} ${c('bottomLeftRadius')}`;
      }
      if (k.localName === 'stroke') {
        css.border = `${dim(a(k, 'width')) || 1}px ${a(k, 'dashWidth') ? 'dashed' : 'solid'} ${color(a(k, 'color')) || '#000'}`;
      }
    }
    if (shape === 'oval') css.borderRadius = '50%';
    css.boxSizing = 'border-box';
    return css;
  }
  function vectorDrawable(root) {
    const vw = parseFloat(a(root, 'viewportWidth')) || 24, vh = parseFloat(a(root, 'viewportHeight')) || 24;
    const w = dim(a(root, 'width')) || vw, h = dim(a(root, 'height')) || vh;
    const rootTint = color(a(root, 'tint'));
    const alpha = a(root, 'alpha');
    function fillOf(el) {
      if (a(el, 'fillColor')) return color(a(el, 'fillColor'));
      const attr = kids(el).find((k) => k.namespaceURI === NS_AAPT && k.getAttribute('name') === 'android:fillColor');
      const g = attr && kids(attr)[0];
      if (g) {
        const items = kids(g).filter((i) => i.localName === 'item');
        return color(items.length ? a(items[0], 'color') : a(g, 'startColor'));
      }
      return 'none';
    }
    function node(el) {
      if (el.localName === 'path') {
        const d = a(el, 'pathData') || a(el, 'path') || '';
        const fill = fillOf(el);
        const st = color(a(el, 'strokeColor'));
        const attrs = [`d="${d}"`, `fill="${fill || 'none'}"`, `data-f="${fill && fill !== 'none' ? 1 : 0}"`];
        if (a(el, 'fillAlpha')) attrs.push(`fill-opacity="${a(el, 'fillAlpha')}"`);
        if (a(el, 'fillType') === 'evenOdd') attrs.push('fill-rule="evenodd"');
        if (st) attrs.push(`stroke="${st}"`, `stroke-width="${a(el, 'strokeWidth') || 1}"`, `data-s="1"`);
        if (a(el, 'strokeLineCap')) attrs.push(`stroke-linecap="${a(el, 'strokeLineCap')}"`);
        if (a(el, 'strokeLineJoin')) attrs.push(`stroke-linejoin="${a(el, 'strokeLineJoin')}"`);
        if (a(el, 'strokeAlpha')) attrs.push(`stroke-opacity="${a(el, 'strokeAlpha')}"`);
        return `<path ${attrs.join(' ')}/>`;
      }
      if (el.localName === 'group') {
        const tx = +a(el, 'translateX') || 0, ty = +a(el, 'translateY') || 0;
        const px = +a(el, 'pivotX') || 0, py = +a(el, 'pivotY') || 0;
        const rot = +a(el, 'rotation') || 0, sx = a(el, 'scaleX') ?? 1, sy = a(el, 'scaleY') ?? 1;
        const tf = `translate(${tx + px} ${ty + py}) rotate(${rot}) scale(${sx} ${sy}) translate(${-px} ${-py})`;
        return `<g transform="${tf}">${kids(el).map(node).join('')}</g>`;
      }
      return '';
    }
    const inner = kids(root).map(node).join('');
    return {
      kind: 'vector', w, h,
      svg: (tint) => {
        let s = inner;
        const t = tint || rootTint;
        if (t) s = s.replace(/fill="[^"]*" data-f="1"/g, `fill="${t}" data-f="1"`).replace(/stroke="[^"]*"(?= stroke-width="[^"]*" data-s)/g, `stroke="${t}"`);
        return `<svg viewBox="0 0 ${vw} ${vh}" width="100%" height="100%" preserveAspectRatio="xMidYMid meet"${alpha ? ` opacity="${alpha}"` : ''}>${s}</svg>`;
      },
    };
  }
  function svgUrl(svg) { return `url("data:image/svg+xml,${encodeURIComponent(svg.replace('<svg ', '<svg xmlns="http://www.w3.org/2000/svg" '))}")`; }

  function applyBackground(div, ref, tint) {
    const d = drawable(ref);
    if (!d) return null;
    if (d.kind === 'color') div.style.backgroundColor = tint || d.color;
    else if (d.kind === 'shape') { Object.assign(div.style, d.css); if (tint) { div.style.backgroundColor = tint; div.style.backgroundImage = ''; } }
    else if (d.kind === 'vector') { div.style.backgroundImage = svgUrl(d.svg(tint)); div.style.backgroundSize = '100% 100%'; }
    else if (d.kind === 'img') { div.style.backgroundImage = `url(${d.src})`; div.style.backgroundSize = '100% 100%'; }
    return d;
  }

  // ---------- vistas ----------
  const GROUPS = new Set(['MaterialButtonToggleGroup', 'LinearLayout', 'FrameLayout', 'RelativeLayout', 'ConstraintLayout', 'ScrollView', 'NestedScrollView', 'CardView', 'MaterialCardView', 'HorizontalScrollView', 'CoordinatorLayout']);

  function gravityOf(v) { return new Set((v || '').split('|').map((s) => s.trim()).filter(Boolean)); }
  function hAlign(g) {
    if (g.has('center') || g.has('center_horizontal')) return 'center';
    if (g.has('end') || g.has('right')) return 'flex-end';
    if (g.has('start') || g.has('left')) return 'flex-start';
    return null;
  }
  function vAlign(g) {
    if (g.has('center') || g.has('center_vertical')) return 'center';
    if (g.has('bottom')) return 'flex-end';
    if (g.has('top')) return 'flex-start';
    return null;
  }

  // ctx: { screen, onboardingIndex, state }
  function render(el, parent, ctx) {
    const full = el.localName;
    const tag = full.split('.').pop();
    if (tag === 'include') {
      const lay = (el.getAttribute('layout') || '').replace('@layout/', '');
      if (R.layouts[lay]) return render(parseXml(R.layouts[lay]), parent, ctx);
      return null;
    }
    if (tag === 'merge' || tag === 'requestFocus') return null;
    applyStyle(el, tag);
    const div = document.createElement('div');
    div.className = 'v v-' + tag;
    if (a(el, 'stateListAnimator')) div.classList.add('press');
    const id = (a(el, 'id') || '').replace(/^@\+?id\//, '');
    if (id) div.dataset.id = id;
    div.dataset.tag = tag;
    div.title = id ? `${tag} #${id}` : tag;

    // tamaño
    const W = a(el, 'layout_width'), H = a(el, 'layout_height');
    const wDp = dim(W), hDp = dim(H);
    const weight = parseFloat(a(el, 'layout_weight') || 0);
    const pk = parent.kind;
    if (wDp != null && !(wDp === 0 && (weight || pk === 'constraint'))) { div.style.width = wDp + 'px'; div.style.flexShrink = '0'; }
    if (hDp != null && !(hDp === 0 && (weight || pk === 'constraint'))) { div.style.height = hDp + 'px'; div.style.flexShrink = '0'; }
    const fillW = W === 'match_parent' || W === 'fill_parent';
    const fillH = H === 'match_parent' || H === 'fill_parent';

    // márgenes / padding
    const m = dim(a(el, 'layout_margin'));
    const mh = dim(a(el, 'layout_marginHorizontal')), mv = dim(a(el, 'layout_marginVertical'));
    const mT = dim(a(el, 'layout_marginTop')) ?? mv ?? m ?? 0;
    const mB = dim(a(el, 'layout_marginBottom')) ?? mv ?? m ?? 0;
    const mS = dim(a(el, 'layout_marginStart')) ?? dim(a(el, 'layout_marginLeft')) ?? mh ?? m ?? 0;
    const mE = dim(a(el, 'layout_marginEnd')) ?? dim(a(el, 'layout_marginRight')) ?? mh ?? m ?? 0;
    div.style.margin = `${mT}px ${mE}px ${mB}px ${mS}px`;
    const p = dim(a(el, 'padding'));
    const ph = dim(a(el, 'paddingHorizontal')), pv = dim(a(el, 'paddingVertical'));
    let pad = {
      t: dim(a(el, 'paddingTop')) ?? pv ?? p, b: dim(a(el, 'paddingBottom')) ?? pv ?? p,
      s: dim(a(el, 'paddingStart')) ?? dim(a(el, 'paddingLeft')) ?? ph ?? p, e: dim(a(el, 'paddingEnd')) ?? dim(a(el, 'paddingRight')) ?? ph ?? p,
    };

    // posición dentro del padre
    const lg = gravityOf(a(el, 'layout_gravity'));
    if (pk === 'linear-v') {
      if (fillW) div.style.alignSelf = 'stretch';
      else if (hAlign(lg)) div.style.alignSelf = hAlign(lg);
      if (weight) { div.style.flex = `${weight} ${weight} 0px`; div.style.minHeight = '0'; }
      else if (fillH) { div.style.flex = '1 1 auto'; }
    } else if (pk === 'linear-h') {
      if (fillH) div.style.alignSelf = 'stretch';
      else if (vAlign(lg)) div.style.alignSelf = vAlign(lg);
      if (weight) { div.style.flex = `${weight} ${weight} 0px`; div.style.minWidth = '0'; }
      else if (fillW) { div.style.flex = '1 1 auto'; div.style.minWidth = '0'; }
    } else if (pk === 'frame' || pk === 'relative') {
      div.style.gridArea = '1 / 1';
      let js = fillW ? 'stretch' : 'start', as = fillH ? 'stretch' : 'start';
      if (pk === 'frame') {
        const h = hAlign(lg), v = vAlign(lg);
        if (h && !fillW) js = h === 'center' ? 'center' : h === 'flex-end' ? 'end' : 'start';
        if (v && !fillH) as = v === 'center' ? 'center' : v === 'flex-end' ? 'end' : 'start';
      } else {
        const t = (n) => a(el, n) === 'true';
        if (!fillW) {
          if (t('layout_alignParentEnd') || t('layout_alignParentRight')) js = 'end';
          if (t('layout_centerHorizontal') || t('layout_centerInParent')) js = 'center';
        }
        if (!fillH) {
          if (t('layout_alignParentBottom')) as = 'end';
          if (t('layout_centerVertical') || t('layout_centerInParent')) as = 'center';
        }
        for (const rule of ['layout_toStartOf', 'layout_toLeftOf', 'layout_toEndOf', 'layout_toRightOf', 'layout_below', 'layout_above']) {
          if (a(el, rule)) div.dataset[rule.replace('layout_', '')] = a(el, rule).replace(/^@\+?id\//, '');
        }
        if (div.dataset.toStartOf || div.dataset.toLeftOf || div.dataset.toEndOf || div.dataset.toRightOf) div.style.minWidth = '0';
      }
      div.style.justifySelf = js; div.style.alignSelf = as;
    } else if (pk === 'constraint') {
      const has = (n) => app(el, n) != null;
      const st = has('layout_constraintStart_toStartOf') || has('layout_constraintLeft_toLeftOf') || has('layout_constraintStart_toEndOf');
      const en = has('layout_constraintEnd_toEndOf') || has('layout_constraintRight_toRightOf') || has('layout_constraintEnd_toStartOf');
      if (fillW || (wDp === 0 && st && en)) div.style.alignSelf = 'stretch';
      else if (st && en) div.style.alignSelf = 'center';
      else if (en) div.style.alignSelf = 'flex-end';
      else div.style.alignSelf = 'flex-start';
      const top = has('layout_constraintTop_toTopOf') || has('layout_constraintTop_toBottomOf');
      const bot = has('layout_constraintBottom_toBottomOf') || has('layout_constraintBottom_toTopOf');
      if (hDp === 0 || fillH) { div.style.flex = '1 1 0px'; div.style.minHeight = '0'; }
      else if (bot && !top) div.style.marginTop = 'auto';
    } else if (pk === 'scroll') {
      div.style.flexShrink = '0';
      if (fillW) div.style.alignSelf = 'stretch';
    }

    // atributos comunes
    const bgTint = color(a(el, 'backgroundTint'));
    const vis = a(el, 'visibility');
    if (vis === 'gone') div.style.display = 'none';
    if (vis === 'invisible') div.style.visibility = 'hidden';
    if (a(el, 'alpha')) div.style.opacity = a(el, 'alpha');
    const elev = dim(a(el, 'elevation'));
    if (elev) div.style.boxShadow = shadow(elev);
    if (a(el, 'clickable') === 'true' || a(el, 'onClick')) div.classList.add('clickable');
    if (!isButtonTag(tag) && dim(a(el, 'minHeight'))) div.style.minHeight = dim(a(el, 'minHeight')) + 'px';

    let childKind = null;
    const isButton = tag === 'Button' || tag === 'MaterialButton' || tag === 'AppCompatButton';

    if ((a(el, 'background') || '').includes('selectableItemBackground')) div.classList.add('clickable', 'ripple');
    if (!isButton && a(el, 'background')) {
      const d = applyBackground(div, a(el, 'background'), bgTint);
      if (d && d.kind === 'shape' && W === 'wrap_content' && d.w) div.style.width = d.w + 'px';
      if (d && d.kind === 'shape' && H === 'wrap_content' && d.h) div.style.height = d.h + 'px';
    }

    switch (tag) {
      case 'LinearLayout': {
        const vert = a(el, 'orientation') === 'vertical';
        childKind = vert ? 'linear-v' : 'linear-h';
        div.style.display = 'flex';
        div.style.flexDirection = vert ? 'column' : 'row';
        const g = gravityOf(a(el, 'gravity'));
        if (vert) { div.style.alignItems = hAlign(g) || 'flex-start'; div.style.justifyContent = vAlign(g) || 'flex-start'; }
        else { div.style.alignItems = vAlign(g) || 'flex-start'; div.style.justifyContent = hAlign(g) || 'flex-start'; }
        break;
      }
      case 'FrameLayout': case 'CoordinatorLayout':
        childKind = 'frame'; div.style.display = 'grid'; div.style.gridTemplate = 'minmax(0,1fr) / minmax(0,1fr)'; break;
      case 'RelativeLayout':
        childKind = 'relative'; div.style.display = 'grid'; div.style.gridTemplate = 'minmax(0,1fr) / minmax(0,1fr)'; break;
      case 'ConstraintLayout':
        childKind = 'constraint'; div.style.display = 'flex'; div.style.flexDirection = 'column'; break;
      case 'ScrollView': case 'NestedScrollView':
        childKind = 'scroll'; div.style.display = 'flex'; div.style.flexDirection = 'column'; div.style.overflowY = 'auto'; div.style.overflowX = 'hidden';
        if (a(el, 'fillViewport') === 'true') div.dataset.fill = '1';
        break;
      case 'HorizontalScrollView':
        childKind = 'linear-h'; div.style.display = 'flex'; div.style.overflowX = 'auto'; break;
      case 'CardView': case 'MaterialCardView': {
        childKind = 'frame';
        div.style.display = 'grid'; div.style.gridTemplate = 'minmax(0,1fr) / minmax(0,1fr)';
        div.style.backgroundColor = color(app(el, 'cardBackgroundColor')) || (tag === 'MaterialCardView' ? '#FEF7FF' : '#FFFFFF');
        div.style.setProperty('--til-bg', div.style.backgroundColor);
        div.style.borderRadius = (dim(app(el, 'cardCornerRadius')) ?? (tag === 'MaterialCardView' ? 12 : 2)) + 'px';
        const e = dim(app(el, 'cardElevation')) ?? (tag === 'MaterialCardView' ? 1 : 2);
        if (e) div.style.boxShadow = shadow(e);
        if (app(el, 'strokeWidth')) div.style.border = `${dim(app(el, 'strokeWidth'))}px solid ${color(app(el, 'strokeColor')) || '#ccc'}`;
        div.style.overflow = 'hidden';
        const cp = dim(app(el, 'contentPadding'));
        if (cp != null) pad = { t: cp, b: cp, s: cp, e: cp };
        break;
      }
      case 'TextView': case 'MaterialTextView': case 'CheckedTextView':
        textStyle(div, el, { size: 14, color: THEME.text });
        div.append(textSpan(str(a(el, 'text'))));
        break;
      case 'Button': case 'MaterialButton': case 'AppCompatButton': {
        div.classList.add('clickable', 'btn');
        const bg = bgTint || color(app(el, 'backgroundTint')) || (a(el, 'background') ? null : THEME.colorPrimary);
        if (bg) div.style.backgroundColor = bg;
        else applyBackground(div, a(el, 'background'));
        const sc = color(app(el, 'strokeColor'));
        if (sc) div.style.boxShadow = `inset 0 0 0 ${dim(app(el, 'strokeWidth')) || 1}px ${sc}`;
        div.style.borderRadius = (dim(app(el, 'cornerRadius')) ?? 999) + 'px';
        textStyle(div, el, { size: 14, color: THEME.onPrimary, weight: 500, gravity: 'center' });
        const minH = dim(a(el, 'minHeight'));
        div.style.minHeight = hDp ? '' : ((minH ? minH - 12 : 40) + 'px');
        div.style.minWidth = wDp ? '' : '64px';
        if (pad.s == null) pad = { t: pad.t ?? 0, b: pad.b ?? 0, s: 24, e: 24 };
        // MaterialButton reserva 6dp arriba y abajo (insetTop/insetBottom)
        const inset = dim(app(el, 'insetTop')) ?? 6;
        if (hDp) { div.style.height = (hDp - 2 * inset) + 'px'; }
        div.style.marginTop = `${mT + inset}px`; div.style.marginBottom = `${mB + inset}px`;
        div.style.gap = (dim(app(el, 'iconPadding')) ?? 8) + 'px';
        div.append(textSpan(str(a(el, 'text'))));
        const bgRef = app(el, 'backgroundTint'), tcRef = a(el, 'textColor');
        const isList = (r) => r && r.startsWith('@color/') && R.colorLists[r.slice(7)];
        const stateful = isList(bgRef) || isList(tcRef);
        if (stateful) {
          div._paint = (on) => {
            div.dataset.checked = on ? '1' : '0';
            div.setAttribute('aria-pressed', String(!!on));
            if (isList(bgRef)) div.style.backgroundColor = colorFor(bgRef, on);
            if (isList(tcRef)) div.style.color = colorFor(tcRef, on);
            div.style.boxShadow = on ? 'none' : (sc ? `inset 0 0 0 ${dim(app(el, 'strokeWidth')) || 1}px ${sc}` : '');
          };
          div._paint(false);
        }
        const icon = app(el, 'icon');
        if (icon) {
          const d = drawable(icon);
          if (d && d.kind === 'vector') {
            const ic = document.createElement('span'); ic.className = 'btn-icon';
            const sz = dim(app(el, 'iconSize')) || 18; ic.style.width = ic.style.height = sz + 'px';
            ic.innerHTML = d.svg(isList(app(el, 'iconTint')) ? 'currentColor' : (color(app(el, 'iconTint')) || div.style.color));
            if ((app(el, 'iconGravity') || '').includes('End')) div.append(ic); else div.prepend(ic);
          }
        }
        break;
      }
      case 'ImageView': case 'ImageButton': case 'AppCompatImageView': case 'ShapeableImageView': {
        div.classList.toggle('clickable', tag === 'ImageButton' || div.classList.contains('clickable'));
        const d = drawable(a(el, 'src') || app(el, 'srcCompat'));
        const tint = color(app(el, 'tint') || a(el, 'tint'));
        const scale = a(el, 'scaleType') || 'fitCenter';
        div.style.display = 'flex'; div.style.alignItems = 'center'; div.style.justifyContent = 'center';
        if (d) {
          if (W === 'wrap_content' && d.w) div.style.width = d.w + (pad.s || 0) + (pad.e || 0) + 'px';
          if (H === 'wrap_content' && d.h) div.style.height = d.h + (pad.t || 0) + (pad.b || 0) + 'px';
          if (d.kind === 'vector') {
            const box = document.createElement('div'); box.className = 'img-box';
            box.innerHTML = d.svg(tint);
            if (scale === 'centerCrop') box.querySelector('svg').setAttribute('preserveAspectRatio', 'xMidYMid slice');
            if (scale === 'fitXY') box.querySelector('svg').setAttribute('preserveAspectRatio', 'none');
            div.append(box);
          } else if (d.kind === 'img') {
            const img = document.createElement('img'); img.src = d.src; img.alt = a(el, 'contentDescription') || '';
            img.style.objectFit = scale === 'centerCrop' ? 'cover' : scale === 'fitXY' ? 'fill' : 'contain';
            if (W === 'wrap_content' && H === 'wrap_content') { img.style.width = 'auto'; img.style.height = 'auto'; }
            div.append(img);
          } else if (d.kind === 'shape') {
            const box = document.createElement('div'); box.className = 'img-box'; Object.assign(box.style, d.css); div.append(box);
          }
        }
        break;
      }
      case 'View':
        if (W === 'wrap_content' && !div.style.width) div.style.width = '0px';
        break;
      case 'Space':
        break;
      case 'MaterialButtonToggleGroup':
        childKind = 'linear-h'; div.style.display = 'flex'; div.style.alignItems = 'stretch';
        div.dataset.toggle = (app(el, 'checkedButton') || '').replace(/^@\+?id\//, '') || '-';
        div.setAttribute('role', 'group');
        break;
      case 'CircularProgressIndicator': {
        const size = dim(app(el, 'indicatorSize')) || 40, th = dim(app(el, 'trackThickness')) || 4;
        div.style.width = div.style.height = size + 'px'; div.style.flexShrink = '0';
        const ic = color(app(el, 'indicatorColor')) || THEME.colorPrimary, tc = color(app(el, 'trackColor')) || 'rgba(0,0,0,.1)';
        const r = (size - th) / 2, C = 2 * Math.PI * r, max = +a(el, 'max') || 100;
        div.innerHTML = `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" style="transform:rotate(-90deg);display:block"><circle cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="${tc}" stroke-width="${th}"/><circle class="cpi" cx="${size / 2}" cy="${size / 2}" r="${r}" fill="none" stroke="${ic}" stroke-width="${th}" stroke-linecap="round" stroke-dasharray="${C}" stroke-dashoffset="${C}"/></svg>`;
        div._setProgress = (v) => { const c = div.querySelector('.cpi'); if (c) c.setAttribute('stroke-dashoffset', String(C * (1 - Math.max(0, Math.min(max, v)) / max))); };
        div._setProgress(+a(el, 'progress') || 0);
        break;
      }
      case 'SparklineView':
        div.classList.add('spark');
        break;
      case 'TextInputLayout':
        return textInput(el, div, pad, ctx);
      case 'SwitchMaterial': case 'Switch': case 'SwitchCompat': case 'MaterialSwitch':
        return switchView(el, div, pad, tag === 'MaterialSwitch');
      case 'SeekBar': case 'Slider': {
        const r = document.createElement('input'); r.type = 'range'; r.className = 'slider';
        r.min = a(el, 'valueFrom') || 0; r.max = a(el, 'valueTo') || a(el, 'max') || 100;
        if (a(el, 'stepSize')) r.step = a(el, 'stepSize');
        r.value = a(el, 'value') || a(el, 'progress') || 0;
        r.setAttribute('aria-label', a(el, 'contentDescription') || id || 'deslizador');
        if (!hDp) div.style.minHeight = '44px';
        r.style.accentColor = color(a(el, 'progressTint')) || color(a(el, 'thumbTint')) || THEME.colorPrimary;
        r.style.width = '100%';
        div.append(r); div.style.display = 'flex'; div.style.alignItems = 'center';
        if (!hDp) div.style.minHeight = '32px';
        break;
      }
      case 'ViewPager2': case 'ViewPager':
        childKind = 'frame'; div.style.display = 'grid'; div.style.gridTemplate = 'minmax(0,1fr) / minmax(0,1fr)';
        if (id === 'viewPagerOnboarding' && ctx.onboarding) {
          const pg = ctx.onboarding;
          const root = parseXml(R.layouts.item_onboarding_page);
          const node = render(root, { kind: 'frame' }, ctx);
          node.style.justifySelf = 'stretch'; node.style.alignSelf = 'stretch';
          node.querySelector('[data-id="txtTitleOnboarding"]')?.replaceChildren(textSpan(pg.title));
          node.querySelector('[data-id="txtDescriptionOnboarding"]')?.replaceChildren(textSpan(pg.desc));
          const imgHost = node.querySelector('[data-id="imgOnboarding"]');
          const d = drawable('@drawable/' + pg.image);
          if (imgHost && d) {
            imgHost.replaceChildren();
            if (d.kind === 'img') { const im = document.createElement('img'); im.src = d.src; imgHost.append(im); }
            else if (d.kind === 'vector') { const b = document.createElement('div'); b.className = 'img-box'; b.innerHTML = d.svg(); imgHost.append(b); }
          }
          div.append(node);
        } else if (id === 'viewPagerMain' && ctx.tabLayout) {
          const node = render(parseXml(R.layouts[ctx.tabLayout]), { kind: 'frame' }, ctx);
          node.style.justifySelf = 'stretch'; node.style.alignSelf = 'stretch';
          div.append(node);
        } else {
          div.classList.add('placeholder'); div.append(textSpan(`${tag}: contenido por código`));
        }
        break;
      case 'ChipGroup':
        childKind = 'linear-h'; div.style.display = 'flex'; div.style.flexWrap = 'wrap';
        div.style.gap = `${dim(app(el, 'chipSpacingVertical')) ?? 4}px ${dim(app(el, 'chipSpacingHorizontal')) ?? 8}px`;
        break;
      case 'Chip': {
        div.classList.add('chip-v', 'clickable', 'press');
        const cp = weight ? 0 : (dim(app(el, 'chipStartPadding')) ?? 12);
        if (pad.s == null) pad = { ...pad, s: cp, e: cp };
        const paint = () => {
          const on = div.dataset.checked === '1';
          div.style.backgroundColor = colorFor(app(el, 'chipBackgroundColor'), on) || (on ? THEME.container : 'transparent');
          div.style.color = colorFor(a(el, 'textColor'), on) || THEME.text;
          div.style.boxShadow = on ? 'none' : `inset 0 0 0 1px ${color(app(el, 'chipStrokeColor')) || '#79747E'}`;
          div.setAttribute('aria-pressed', String(on));
        };
        div.dataset.checked = a(el, 'checked') === 'true' ? '1' : '0';
        div.style.fontSize = (dim(a(el, 'textSize')) || 14) + 'px';
        div.style.fontWeight = (a(el, 'textStyle') || '').includes('bold') ? '700' : '500';
        div.append(textSpan(str(a(el, 'text'))));
        if (a(el, 'checkable') === 'true' || (el.getAttribute('style') || '').includes('Filter')) {
          div.addEventListener('click', () => { div.dataset.checked = div.dataset.checked === '1' ? '0' : '1'; paint(); });
        }
        div._paint = paint; paint();
        break;
      }
      case 'BottomNavigationView':
        return bottomNav(el, div, ctx);
      case 'ProgressBar': {
        div.classList.add('progress');
        if (!wDp) div.style.width = '48px';
        if (!hDp) div.style.height = '48px';
        break;
      }
      default:
        if (!GROUPS.has(tag)) {
          warnings.add(`Widget sin soporte en la vista previa: ${full}`);
          div.classList.add('placeholder'); div.append(textSpan(tag));
        }
    }

    div.style.padding = `${pad.t || 0}px ${pad.e || 0}px ${pad.b || 0}px ${pad.s || 0}px`;
    if (vis === 'gone') div.style.display = 'none';

    if (childKind) {
      for (const k of kids(el)) {
        const c = render(k, { kind: childKind, el }, ctx);
        if (c) div.append(c);
      }
      if (childKind === 'scroll' && div.dataset.fill && div.firstElementChild) div.firstElementChild.style.minHeight = '100%';
      if (div.dataset.toggle) setupToggleGroup(div);
    }
    return div;
  }

  // MaterialButtonToggleGroup: botones unidos, esquinas solo en los extremos, selección única
  function setupToggleGroup(g) {
    const btns = Array.from(g.children).filter((c) => c.classList.contains('btn'));
    btns.forEach((b, i) => {
      const r = b.style.borderRadius || '14px';
      b.style.borderRadius = i === 0 ? `${r} 0 0 ${r}` : i === btns.length - 1 ? `0 ${r} ${r} 0` : '0';
      if (i > 0) b.style.marginLeft = '-1px';
      b.addEventListener('click', () => g._check(b.dataset.id));
    });
    g._check = (bid) => btns.forEach((b) => b._paint && b._paint(b.dataset.id === bid));
    g._check(g.dataset.toggle);
  }

  function isButtonTag(t) { return t === 'Button' || t === 'MaterialButton' || t === 'AppCompatButton'; }
  function shadow(e) {
    return `0 ${Math.min(e, 12) * 0.5}px ${Math.min(e, 12) * 1.5}px rgba(0,0,0,${0.08 + Math.min(e, 12) * 0.012}), 0 0.5px 1px rgba(0,0,0,0.06)`;
  }
  function textSpan(t) { const s = document.createElement('span'); s.className = 'txt'; s.textContent = t; return s; }
  function textStyle(div, el, def) {
    div.style.fontSize = (dim(a(el, 'textSize')) || def.size) + 'px';
    div.style.color = color(a(el, 'textColor')) || def.color;
    const ts = a(el, 'textStyle') || '';
    div.style.fontWeight = ts.includes('bold') ? '700' : def.weight || '400';
    if (ts.includes('italic')) div.style.fontStyle = 'italic';
    const ff = a(el, 'fontFamily') || '';
    if (ff.includes('medium')) div.style.fontWeight = '500';
    if (ff.includes('monospace')) div.style.fontFamily = 'var(--mono)';
    if (ff.includes('serif') && !ff.includes('sans')) div.style.fontFamily = 'Georgia, serif';
    if (a(el, 'letterSpacing')) div.style.letterSpacing = parseFloat(a(el, 'letterSpacing')) + 'em';
    if (a(el, 'textAllCaps') === 'true') div.style.textTransform = 'uppercase';
    const ml = a(el, 'maxLines') || (a(el, 'singleLine') === 'true' ? '1' : null);
    if (ml) div.style.setProperty('--lines', ml), div.classList.add('clamp');
    const lse = dim(a(el, 'lineSpacingExtra')), lsm = parseFloat(a(el, 'lineSpacingMultiplier') || 1);
    div.style.lineHeight = `calc(${1.17 * lsm}em + ${lse || 0}px)`;
    const g = gravityOf(a(el, 'gravity') || def.gravity);
    div.style.display = 'flex'; div.style.flexDirection = 'column';
    div.style.justifyContent = vAlign(g) || (def.gravity ? 'center' : 'flex-start');
    const h = hAlign(g) || (def.gravity ? 'center' : null);
    div.style.textAlign = h === 'center' ? 'center' : h === 'flex-end' ? 'right' : 'left';
    const ds = a(el, 'drawableStart') || a(el, 'drawableLeft');
    if (ds) { const d = drawable(ds); if (d && d.kind === 'vector') { div.style.flexDirection = 'row'; div.style.alignItems = 'center'; div.style.gap = (dim(a(el, 'drawablePadding')) || 0) + 'px'; const ic = document.createElement('span'); ic.className = 'btn-icon'; ic.style.width = d.w + 'px'; ic.style.height = d.h + 'px'; ic.innerHTML = d.svg(color(a(el, 'drawableTint'))); div.prepend(ic); } }
  }

  function textInput(el, div, pad, ctx) {
    div.classList.add('til');
    const style = el.getAttribute('style') || '';
    const outlined = style.includes('Outlined') || true;
    const edit = kids(el).find((k) => /EditText$/.test(k.localName));
    const hint = str(a(el, 'hint') || (edit && a(edit, 'hint')) || '');
    const field = document.createElement('label'); field.className = 'til-box' + (outlined ? ' outlined' : '');
    const rad = dim(app(el, 'boxCornerRadiusTopStart')); if (rad != null) field.style.borderRadius = rad + 'px';
    const fc = color(app(el, 'boxStrokeColor')); if (fc) field.style.setProperty('--focus', fc);
    const start = app(el, 'startIconDrawable');
    if (start) { const d = drawable(start); if (d && d.kind === 'vector') { const ic = document.createElement('span'); ic.className = 'til-icon'; ic.innerHTML = d.svg(color(app(el, 'startIconTint')) || THEME.textSecondary); field.append(ic); field.classList.add('has-start'); } }
    const input = document.createElement('input');
    const it = edit ? a(edit, 'inputType') || '' : '';
    input.type = /Password/i.test(it) ? 'password' : /Email/i.test(it) ? 'email' : /phone/i.test(it) ? 'tel' : /number/i.test(it) ? 'number' : 'text';
    input.placeholder = ' ';
    input.id = 'in-' + ctx.screen + '-' + ((edit && (a(edit, 'id') || '').replace(/^@\+?id\//, '')) || Math.random().toString(36).slice(2, 7));
    if (edit && a(edit, 'text')) input.value = str(a(edit, 'text'));
    if (edit && a(edit, 'id')) input.dataset.edit = a(edit, 'id').replace(/^@\+?id\//, '');
    if (edit && a(edit, 'maxLength')) input.maxLength = +a(edit, 'maxLength');
    const lab = document.createElement('span'); lab.className = 'til-label'; lab.textContent = hint;
    field.append(input, lab);
    const endMode = app(el, 'endIconMode') || (app(el, 'passwordToggleEnabled') === 'true' ? 'password_toggle' : null);
    if (endMode === 'password_toggle') {
      const eye = document.createElement('button'); eye.type = 'button'; eye.className = 'til-end'; eye.setAttribute('aria-label', 'Mostrar contraseña');
      eye.innerHTML = '<svg viewBox="0 0 24 24" width="24" height="24"><path fill="#757575" d="M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5C21.27,7.61 17,4.5 12,4.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5s5,2.24 5,5S14.76,17 12,17zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3s3,-1.34 3,-3S13.66,9 12,9z"/></svg>';
      eye.onclick = () => { input.type = input.type === 'password' ? 'text' : 'password'; };
      field.append(eye); field.classList.add('has-end');
    } else if (endMode === 'clear_text') field.classList.add('has-end');
    div.append(field);
    const helper = str(app(el, 'helperText') || '');
    const hp = document.createElement('div'); hp.className = 'til-helper'; hp.textContent = helper; hp.dataset.helper = helper;
    if (!helper) hp.style.display = 'none';
    div.append(hp);
    div._setError = (msg) => { field.classList.toggle('error', !!msg); hp.textContent = msg || hp.dataset.helper; hp.classList.toggle('error', !!msg); hp.style.display = (msg || hp.dataset.helper) ? '' : 'none'; };
    div.style.padding = `${pad.t || 0}px ${pad.e || 0}px ${pad.b || 0}px ${pad.s || 0}px`;
    return div;
  }

  function switchView(el, div, pad, m3) {
    div.classList.add('sw', 'clickable');
    div.style.display = 'flex'; div.style.alignItems = 'center'; div.style.gap = '12px';
    const txt = str(a(el, 'text'));
    if (txt) { const t = textSpan(txt); t.style.flex = '1'; t.style.fontSize = (dim(a(el, 'textSize')) || 14) + 'px'; t.style.color = color(a(el, 'textColor')) || THEME.text; div.append(t); }
    const inp = document.createElement('input'); inp.type = 'checkbox'; inp.className = 'sw-input' + (m3 ? ' m3' : '');
    inp.checked = a(el, 'checked') === 'true';
    inp.setAttribute('aria-label', txt || (a(el, 'id') || 'interruptor'));
    const c = color(a(el, 'thumbTint')) || color(app(el, 'thumbTint')) || THEME.colorSecondary || THEME.colorPrimary;
    inp.style.setProperty('--on', c);
    div.append(inp);
    div.style.padding = `${pad.t || 0}px ${pad.e || 0}px ${pad.b || 0}px ${pad.s || 0}px`;
    return div;
  }

  function bottomNav(el, div, ctx) {
    const menuName = (app(el, 'menu') || '@menu/bottom_nav_menu').split('/')[1];
    const nav = navBar(menuName, ctx.activeTab, ctx.onTab);
    nav.style.alignSelf = 'stretch';
    const bg = color(a(el, 'background')); if (bg) nav.style.background = bg;
    if (dim(app(el, 'elevation'))) nav.style.boxShadow = '0 -1px 8px rgba(22,37,46,.08)';
    return nav;
  }
  function navBar(menuName, active, onTab) {
    const nav = document.createElement('nav'); nav.className = 'bnav';
    const menu = R.menus[menuName];
    if (!menu) return nav;
    const list = R.colorLists.color_bottom_nav_item ? kids(parseXml(R.colorLists.color_bottom_nav_item)) : [];
    const onC = list.length ? color(a(list.find((i) => i.hasAttributeNS(NS_A, 'state_checked')) || list[0], 'color')) : THEME.colorPrimary;
    const offC = list.length ? color(a(list.find((i) => !i.hasAttributeNS(NS_A, 'state_checked')) || list[0], 'color')) : THEME.textSecondary;
    kids(parseXml(menu)).filter((i) => i.localName === 'item').forEach((item, idx) => {
      const b = document.createElement('button'); b.type = 'button'; b.className = 'bnav-item' + (idx === active ? ' on' : '');
      const c = idx === active ? onC : offC;
      const d = drawable(a(item, 'icon'));
      b.innerHTML = `<span class="bnav-ic">${d && d.kind === 'vector' ? d.svg(c) : ''}</span>`;
      b.append(textSpan(str(a(item, 'title'))));
      b.style.color = c;
      b.style.setProperty('--ind', THEME.container || c);
      b.onclick = () => onTab && onTab(idx);
      nav.append(b);
    });
    return nav;
  }

  // Ajuste posterior de RelativeLayout (toStartOf / toEndOf)
  function fixRelative(root) {
    root.querySelectorAll('[data-to-start-of],[data-to-left-of],[data-to-end-of],[data-to-right-of]').forEach((n) => {
      const scope = n.parentElement;
      const findSib = (id) => id && Array.from(scope.children).find((c) => c.dataset.id === id);
      const s = findSib(n.dataset.toStartOf || n.dataset.toLeftOf);
      if (s) { const cs = getComputedStyle(s); n.style.marginRight = (s.offsetWidth + parseFloat(cs.marginLeft) + parseFloat(cs.marginRight) + parseFloat(n.style.marginRight || 0)) + 'px'; }
      const e = findSib(n.dataset.toEndOf || n.dataset.toRightOf);
      if (e) { const cs = getComputedStyle(e); n.style.marginLeft = (e.offsetWidth + parseFloat(cs.marginLeft) + parseFloat(cs.marginRight) + parseFloat(n.style.marginLeft || 0)) + 'px'; }
    });
  }

  let themeApplied = false;
  function applyTheme() {
    if (themeApplied) return; themeApplied = true;
    const c = (k) => appTheme[k] && color(appTheme[k]);
    if (c('colorPrimary')) THEME.colorPrimary = c('colorPrimary');
    if (c('colorOnPrimary')) THEME.onPrimary = c('colorOnPrimary');
    if (c('colorOnSurface')) THEME.text = c('colorOnSurface');
    if (c('colorOnSurfaceVariant')) THEME.textSecondary = c('colorOnSurfaceVariant');
    if (c('colorSecondary')) THEME.colorSecondary = c('colorSecondary');
    THEME.container = c('colorPrimaryContainer') || '#E8DEF8';
    THEME.bg = c('android:colorBackground') || '#FEF7FF';
    document.documentElement.style.setProperty('--tp', THEME.colorPrimary);
  }
  // Copia los atributos de style="@style/..." (o del estilo por defecto del tema) al elemento
  function applyStyle(el, tag) {
    if (el.__styled) return; el.__styled = true;
    let name = (el.getAttribute('style') || '').replace(/^@style\//, '');
    if (!name) {
      if (/^(Button|MaterialButton|AppCompatButton)$/.test(tag)) name = (appTheme.materialButtonStyle || '').replace(/^@style\//, '');
      if (tag === 'TextInputLayout') name = (appTheme.textInputStyle || '').replace(/^@style\//, '');
    }
    if (!name) return;
    const items = styleItems(name);
    for (const [k, v] of Object.entries(items)) {
      if (k.startsWith('android:')) { const n = k.slice(8); if (!el.hasAttributeNS(NS_A, n)) el.setAttributeNS(NS_A, 'android:' + n, v); }
      else if (!k.includes(':')) { if (!el.hasAttributeNS(NS_APP, k)) el.setAttributeNS(NS_APP, 'app:' + k, v); }
    }
  }

  function renderLayout(name, ctx) {
    applyTheme();
    const xml = R.layouts[name];
    if (!xml) throw new Error('Layout no encontrado: ' + name);
    const root = parseXml(xml);
    const node = render(root, { kind: 'root' }, ctx);
    node.style.flex = '1 1 auto'; node.style.minHeight = '0';
    node.style.alignSelf = 'stretch';
    return node;
  }

  window.AndroidPreview = { THEME, renderLayout, renderNode: (name, ctx) => render(parseXml(R.layouts[name]), { kind: 'root' }, ctx || {}), navBar, fixRelative, drawable, color, warnings, parseXml };
})();
