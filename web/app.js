/**
 * Openfy — comportamiento de la web.
 *
 * Sin dependencias externas y sin peticiones a terceros: todo lo que hace este
 * fichero usa APIs del navegador y datos servidos por el propio sitio.
 *
 * Funciones:
 *   1. Tema claro/oscuro (como el conmutador de la app) guardado en localStorage.
 *   2. Descarga fiable: lee el manifiesto de la release, comprueba que el CDN
 *      responde y, si no, cambia el botón al espejo de GitHub Releases.
 *   3. Botones de copiar para el SHA-256 y la huella de firma.
 */
(function () {
  'use strict';

  var site = (window.OPENFY_SITE && window.OPENFY_SITE.site) || {};
  var legal = (window.OPENFY_SITE && window.OPENFY_SITE.legal) || {};

  var MANIFEST_URL = 'downloads/latest.json';
  var RELEASE_MIRROR = 'https://github.com/Pausiar/openfy/releases/latest/download/openfy-latest.apk';

  /* ------------------------------------------------------------------ */
  /* Utilidades                                                          */
  /* ------------------------------------------------------------------ */

  function $(selector, scope) {
    return (scope || document).querySelector(selector);
  }

  function $$(selector, scope) {
    return Array.prototype.slice.call((scope || document).querySelectorAll(selector));
  }

  function setText(selector, value) {
    $$('[data-release="' + selector + '"]').forEach(function (element) {
      element.textContent = value;
    });
  }

  function formatBytes(bytes) {
    if (!bytes && bytes !== 0) return '—';
    if (bytes < 1024) return bytes + ' B';
    var mb = bytes / (1024 * 1024);
    if (mb >= 1024) return (mb / 1024).toFixed(2) + ' GB';
    return mb.toFixed(1) + ' MB';
  }

  function formatDate(iso) {
    if (!iso) return null;
    var date = new Date(iso);
    if (isNaN(date.getTime())) return null;
    return date.toLocaleDateString('es-ES', { year: 'numeric', month: 'long', day: 'numeric' });
  }

  function groupFingerprint(hex) {
    if (!hex) return null;
    var clean = hex.replace(/[^0-9a-fA-F]/g, '').toUpperCase();
    var groups = clean.match(/.{1,2}/g);
    return groups ? groups.join(':') : clean;
  }

  /* ------------------------------------------------------------------ */
  /* 1. Tema                                                             */
  /* ------------------------------------------------------------------ */

  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    var toggle = $('#theme-toggle');
    if (toggle) {
      toggle.setAttribute(
        'aria-label',
        theme === 'dark' ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro'
      );
    }
  }

  function initTheme() {
    var stored = null;
    try {
      stored = window.localStorage.getItem('openfy-theme');
    } catch (error) {
      stored = null;
    }

    // ?theme=light|dark permite previsualizar un tema sin guardarlo.
    var forced = null;
    try {
      var requested = new URLSearchParams(window.location.search).get('theme');
      if (requested === 'light' || requested === 'dark') forced = requested;
    } catch (error) {
      forced = null;
    }

    // La app es dark-first, así que el sitio arranca en oscuro salvo que el
    // visitante haya elegido otra cosa.
    applyTheme(forced || (stored === 'light' || stored === 'dark' ? stored : 'dark'));

    var toggle = $('#theme-toggle');
    if (!toggle) return;
    toggle.addEventListener('click', function () {
      var next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
      applyTheme(next);
      try {
        window.localStorage.setItem('openfy-theme', next);
      } catch (error) {
        /* Almacenamiento bloqueado: el tema dura lo que dura la página. */
      }
    });
  }

  /* ------------------------------------------------------------------ */
  /* 2. Datos legales                                                     */
  /* ------------------------------------------------------------------ */

  function initLegalFields() {
    $$('[data-legal]').forEach(function (element) {
      var key = element.getAttribute('data-legal');
      var value = legal[key];
      if (value) {
        element.textContent = value;
        element.classList.remove('field-pending');
        return;
      }
      element.textContent = element.getAttribute('data-placeholder') || 'Pendiente de completar';
      element.classList.add('field-pending');
    });
  }

  /* ------------------------------------------------------------------ */
  /* 3. Descarga                                                         */
  /* ------------------------------------------------------------------ */

  function setStatus(text, state) {
    var box = $('#download-status');
    var label = $('#download-status-text');
    if (label) label.textContent = text;
    if (box) {
      box.classList.remove('is-ok', 'is-warn');
      if (state) box.classList.add(state);
    }
  }

  function applyManifest(manifest) {
    if (!manifest) return;

    if (manifest.versionName) {
      setText('version', manifest.versionName);
      if (site.fallbackVersion) {
        // La versión publicada manda sobre la de respaldo.
      }
    }

    if (manifest.apk && manifest.apk.sizeBytes) {
      setText('size', formatBytes(manifest.apk.sizeBytes));
    }

    if (manifest.apk && manifest.apk.sha256) {
      setText('sha256', manifest.apk.sha256);
    }

    if (manifest.signature && manifest.signature.sha256) {
      setText('fingerprint', groupFingerprint(manifest.signature.sha256));
    }

    if (manifest.releasedAt) {
      var released = formatDate(manifest.releasedAt);
      if (released) setText('released', 'Publicada el ' + released);
    }

    if (manifest.apk && manifest.apk.url) {
      var downloadLinks = $$('[data-release="download"]');
      downloadLinks.forEach(function (link) {
        link.setAttribute('href', manifest.apk.url);
      });
    }

    if (manifest.mirrors && manifest.mirrors.length) {
      var mirror = manifest.mirrors[0];
      var mirrorLinks = $$('[data-release="mirror"]');
      mirrorLinks.forEach(function (link) {
        link.setAttribute('href', mirror.url);
        link.textContent = 'Espejo: ' + mirror.label;
      });
    }
  }

  /**
   * Comprueba que la APK se puede descargar desde el CDN de Pages.
   * Si la comprobación falla (red bloqueada, CDN caído), el botón pasa al
   * espejo de GitHub Releases para que nunca se quede una descarga muerta.
   */
  function checkDownload(url) {
    if (!url || typeof window.fetch !== 'function') {
      setStatus('Descarga disponible en GitHub Releases.', 'is-ok');
      return;
    }

    var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timer = window.setTimeout(function () {
      if (controller) controller.abort();
    }, 8000);

    fetch(url, { method: 'HEAD', signal: controller ? controller.signal : undefined })
      .then(function (response) {
        window.clearTimeout(timer);
        if (response.ok) {
          setStatus('Descarga verificada: disponible desde el CDN de GitHub Pages.', 'is-ok');
          return;
        }
        throw new Error('HTTP ' + response.status);
      })
      .catch(function () {
        window.clearTimeout(timer);
        $$('[data-release="download"]').forEach(function (link) {
          link.setAttribute('href', RELEASE_MIRROR);
        });
        setStatus('El CDN no responde: el botón usa el espejo de GitHub Releases.', 'is-warn');
      });
  }

  function loadManifest() {
    if (typeof window.fetch !== 'function') return;

    fetch(MANIFEST_URL + '?t=' + Date.now(), { cache: 'no-store' })
      .then(function (response) {
        if (!response.ok) throw new Error('HTTP ' + response.status);
        return response.json();
      })
      .then(function (manifest) {
        applyManifest(manifest);
        checkDownload(manifest && manifest.apk ? manifest.apk.url : null);
      })
      .catch(function () {
        // Sin manifiesto se muestran los valores de respaldo incrustados en el HTML.
        setStatus('Descarga disponible en GitHub Releases.', 'is-ok');
      });
  }

  /* ------------------------------------------------------------------ */
  /* 4. Copiar al portapapeles                                            */
  /* ------------------------------------------------------------------ */

  function initCopyButtons() {
    $$('[data-copy]').forEach(function (button) {
      button.addEventListener('click', function () {
        var key = button.getAttribute('data-copy');
        var source = $('[data-release="' + key + '"]');
        if (!source) return;
        var text = source.textContent.trim();
        if (!text || text === '—') return;

        var done = function () {
          var original = button.textContent;
          button.textContent = '¡Copiado!';
          window.setTimeout(function () {
            button.textContent = original;
          }, 1600);
        };

        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text).then(done, function () {
            window.prompt('Copia el valor:', text);
          });
        } else {
          window.prompt('Copia el valor:', text);
        }
      });
    });
  }

  /* ------------------------------------------------------------------ */
  /* 5. Año en curso                                                     */
  /* ------------------------------------------------------------------ */

  function initYear() {
    var element = $('#current-year');
    if (element) element.textContent = String(new Date().getFullYear());
  }

  /* ------------------------------------------------------------------ */

  function init() {
    initTheme();
    initLegalFields();
    initCopyButtons();
    initYear();
    loadManifest();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
