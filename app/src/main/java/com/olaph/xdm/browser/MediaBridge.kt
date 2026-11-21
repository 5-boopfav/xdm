package com.olaph.xdm.browser

import android.webkit.JavascriptInterface

/**
 * The only object exposed to page JavaScript (as `XdmBridge`).
 * Input is bounded and only HTTP(S) URLs get through.
 */
class MediaBridge(
    private val mediaSink: (url: String, source: String) -> Unit,
    private val playSink: (url: String) -> Unit,
    private val touchSink: (url: String) -> Unit,
) {
    @JavascriptInterface
    fun onMedia(url: String?, source: String?) {
        val u = clean(url) ?: return
        mediaSink(u, source.orEmpty().take(16))
    }

    @JavascriptInterface
    fun onPlay(url: String?) {
        playSink(clean(url).orEmpty())
    }

    @JavascriptInterface
    fun onTouchVideo(url: String?) {
        val raw = url.orEmpty()
        touchSink(if (raw == "blob") raw else clean(raw).orEmpty())
    }

    private fun clean(url: String?): String? {
        if (url == null || url.length > MAX_URL) return null
        val lower = url.take(8).lowercase()
        return url.takeIf {
            lower.startsWith("http://") || lower.startsWith("https://")
        }
    }

    private companion object {
        const val MAX_URL = 4096
    }
}

/**
 * Injected into pages to collect media URLs exposed by the DOM
 * and public embedded metadata.
 */
object MediaScript {
    const val BRIDGE_NAME = "XdmBridge"

    val SOURCE: String = """
(function () {
  var B = window.$BRIDGE_NAME;
  if (!B) return;

  if (window.__xdmScan) {
    window.__xdmScan();
    return;
  }

  var sent = {};
  var count = 0;

  function abs(u) {
    try {
      return new URL(u, document.baseURI).href;
    } catch (e) {
      return null;
    }
  }

  function send(u, src) {
    if (!u || typeof u !== 'string') return;

    var a = abs(u);

    if (!a || !/^https?:\/\//i.test(a)) return;
    if (sent[a] || count >= 200) return;

    sent[a] = 1;
    count++;

    try {
      B.onMedia(a, src);
    } catch (e) {}
  }

  function srcOf(el) {
    return el.currentSrc || el.src || '';
  }

  function isMedia(el) {
    return el && (
      el.tagName === 'VIDEO' ||
      el.tagName === 'AUDIO'
    );
  }

  function remember(e) {
    try {
      var d = (e.target && e.target.ownerDocument) || document;
      var p = (e.touches && e.touches.length) ? e.touches[0] : e;

      var list = d.elementsFromPoint
        ? d.elementsFromPoint(p.clientX, p.clientY)
        : [e.target];

      for (var i = 0; i < list.length; i++) {
        if (isMedia(list[i])) {
          var u = srcOf(list[i]);

          B.onTouchVideo(
            u.indexOf('http') === 0 ? u : 'blob'
          );

          return;
        }
      }
    } catch (x) {}
  }

  function listen(doc) {
    if (doc.__xdmL) return;

    doc.__xdmL = 1;

    doc.addEventListener('play', function (e) {
      if (!isMedia(e.target)) return;

      var u = srcOf(e.target);

      try {
        B.onPlay(u.indexOf('http') === 0 ? u : '');
      } catch (x) {}

      send(u, 'play');
    }, true);

    doc.addEventListener(
      'touchstart',
      remember,
      { capture: true, passive: true }
    );

    doc.addEventListener('contextmenu', remember, true);
  }

  /*
   * Scan public embedded JSON metadata.
   * This does not bypass login or private content.
   */
  function scanEmbeddedScripts(doc) {
    try {
      var scripts = doc.querySelectorAll('script');

      var patterns = [
        /"playAddr"\s*:\s*"((?:\\.|[^"\\])*)"/g,
        /"contentUrl"\s*:\s*"((?:\\.|[^"\\])*)"/g,
        /"video_url"\s*:\s*"((?:\\.|[^"\\])*)"/g,
        /"play_url"\s*:\s*"((?:\\.|[^"\\])*)"/g
      ];

      for (var s = 0; s < scripts.length; s++) {
        var content = scripts[s].textContent || '';

        if (!content || content.length > 2000000) continue;

        for (var p = 0; p < patterns.length; p++) {
          patterns[p].lastIndex = 0;

          var match;

          while ((match = patterns[p].exec(content)) !== null) {
            try {
              var decoded = JSON.parse('"' + match[1] + '"');
              send(decoded, 'social-json');
            } catch (e) {}
          }
        }
      }
    } catch (e) {}
  }

  function scanDoc(doc, depth) {
    try {
      listen(doc);

      var els = doc.querySelectorAll(
        'video, audio, source'
      );

      for (var i = 0; i < els.length; i++) {
        send(els[i].currentSrc, 'dom');
        send(els[i].src, 'dom');
      }

      var metas = doc.querySelectorAll(
        'meta[property^="og:video"], ' +
        'meta[property^="og:audio"], ' +
        'meta[name="twitter:player:stream"]'
      );

      for (var j = 0; j < metas.length; j++) {
        send(
          metas[j].getAttribute('content'),
          'meta'
        );
      }

      // Inspect embedded public video metadata.
      scanEmbeddedScripts(doc);

      if (depth < 3) {
        var frames = doc.querySelectorAll('iframe');

        for (var k = 0; k < frames.length; k++) {
          try {
            if (frames[k].contentDocument) {
              scanDoc(
                frames[k].contentDocument,
                depth + 1
              );
            }
          } catch (x) {}
        }
      }
    } catch (x) {}
  }

  function scan() {
    scanDoc(document, 0);
  }

  window.__xdmScan = scan;

  var timer = null;

  function later() {
    if (timer) return;

    timer = setTimeout(function () {
      timer = null;
      scan();
    }, 600);
  }

  try {
    new MutationObserver(later).observe(
      document,
      {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ['src']
      }
    );
  } catch (e) {}

  setInterval(scan, 4000);

  scan();
})();
""".trimIndent()
}
