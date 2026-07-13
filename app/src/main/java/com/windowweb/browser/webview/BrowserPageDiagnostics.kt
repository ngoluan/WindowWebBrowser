package com.windowweb.browser.webview

import com.windowweb.browser.core.ConsoleEntry
import com.windowweb.browser.core.ConsoleLevel

object BrowserPageDiagnostics {
    const val SOURCE = "WindowWeb Diagnostics"
    const val PREFIX = "[WindowWeb]"

    val documentStartScript: String = """
        (function () {
          if (window.__windowWebDiagnosticsInstalled) return;
          window.__windowWebDiagnosticsInstalled = true;

          function describe(value) {
            try {
              if (value instanceof Error) return value.stack || value.message || String(value);
              if (typeof value === 'string') return value;
              if (value === undefined) return 'undefined';
              if (value === null) return 'null';
              if (typeof value === 'function') return '[Function ' + (value.name || 'anonymous') + ']';
              if (typeof value !== 'object') return String(value);

              var seen = [];
              return JSON.stringify(value, function (key, nested) {
                if (nested instanceof Error) {
                  return {
                    name: nested.name,
                    message: nested.message,
                    stack: nested.stack
                  };
                }
                if (typeof nested === 'object' && nested !== null) {
                  if (seen.indexOf(nested) >= 0) return '[Circular]';
                  seen.push(nested);
                }
                return nested;
              }, 2);
            } catch (_) {
              try { return String(value); } catch (_) { return '<unprintable>'; }
            }
          }

          function wrapConsole(level) {
            var original = console && console[level];
            if (typeof original !== 'function') return;
            original = original.bind(console);
            console[level] = function () {
              try {
                var rendered = Array.prototype.map.call(arguments, describe);
                return original.apply(console, rendered);
              } catch (_) {
                return original.apply(console, arguments);
              }
            };
          }

          ['debug', 'log', 'info', 'warn', 'error'].forEach(wrapConsole);

          window.addEventListener('error', function (event) {
            var target = event && event.target;
            if (target && target !== window) {
              var resource = target.src || target.href || target.currentSrc || target.tagName || 'unknown resource';
              console.error('[WindowWeb resource error] ' + resource);
              return;
            }
            var message = event && event.message ? event.message : 'Unknown JavaScript error';
            var source = event && event.filename ? event.filename : location.href;
            var line = event && event.lineno ? event.lineno : 0;
            var column = event && event.colno ? event.colno : 0;
            var stack = event && event.error ? describe(event.error) : '';
            console.error('[WindowWeb uncaught error] ' + message + ' @ ' + source + ':' + line + ':' + column + (stack ? '\n' + stack : ''));
          }, true);

          window.addEventListener('unhandledrejection', function (event) {
            console.error('[WindowWeb unhandled promise rejection] ' + describe(event && event.reason));
          });

          window.addEventListener('securitypolicyviolation', function (event) {
            console.warn('[WindowWeb CSP violation] blocked=' + (event.blockedURI || '<inline>') +
              ' directive=' + (event.effectiveDirective || event.violatedDirective || 'unknown') +
              ' source=' + (event.sourceFile || location.href) + ':' + (event.lineNumber || 0));
          });

          // Keep diagnostics passive. Replacing fetch/EventSource changes function
          // identity and can break streaming SDKs such as OpenCode's global event client.
          window.addEventListener('offline', function () {
            console.warn('[WindowWeb network] browser reported offline');
          });

          window.addEventListener('online', function () {
            console.info('[WindowWeb network] browser reported online');
          });
        })();
    """.trimIndent()

    val snapshotScript: String = """
        (function () {
          try {
            var body = document.body;
            var root = document.documentElement;
            var text = body ? String(body.innerText || body.textContent || '').trim() : '';
            var childCount = body ? body.children.length : 0;
            var htmlLength = root ? root.outerHTML.length : 0;
            var rect = body ? body.getBoundingClientRect() : null;
            var style = body ? getComputedStyle(body) : null;
            var width = rect ? Math.round(rect.width) : 0;
            var height = rect ? Math.round(rect.height) : 0;
            var display = style ? style.display : 'missing';
            var visibility = style ? style.visibility : 'missing';
            var opacity = style ? style.opacity : 'missing';
            var visuallyHidden = display === 'none' || visibility === 'hidden' || opacity === '0' || height === 0;
            var suspicious = !body || visuallyHidden || (text.length === 0 && childCount === 0);
            return (suspicious ? 'BLANK' : 'OK') +
              '|url=' + encodeURIComponent(location.href) +
              '|ready=' + document.readyState +
              '|title=' + encodeURIComponent(document.title || '') +
              '|contentType=' + encodeURIComponent(document.contentType || '') +
              '|textLength=' + text.length +
              '|bodyChildren=' + childCount +
              '|htmlLength=' + htmlLength +
              '|bodySize=' + width + 'x' + height +
              '|display=' + display +
              '|visibility=' + visibility +
              '|opacity=' + opacity +
              '|online=' + navigator.onLine;
          } catch (error) {
            return 'ERROR|message=' + encodeURIComponent(error && (error.stack || error.message) || String(error));
          }
        })();
    """.trimIndent()

    fun consoleEntry(tabId: String, rawResult: String?, sourceUrl: String?): ConsoleEntry {
        val decoded = decodeJavascriptResult(rawResult)
        val suspicious = decoded.startsWith("BLANK|") || decoded.startsWith("ERROR|")
        return ConsoleEntry(
            tabId = tabId,
            level = if (suspicious) ConsoleLevel.WARNING else ConsoleLevel.INFO,
            message = "$PREFIX page snapshot: $decoded",
            source = sourceUrl ?: SOURCE,
            lineNumber = null
        )
    }

    private fun decodeJavascriptResult(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return "ERROR|message=No diagnostic result"
        if (raw.length < 2 || raw.first() != '"' || raw.last() != '"') return raw
        return raw.substring(1, raw.length - 1)
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }
}