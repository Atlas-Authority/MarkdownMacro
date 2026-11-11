/*
 * Post-render behaviour for the markdown macros (syntax highlighting and Mermaid diagrams).
 *
 * Shipped as a web-resource instead of inline <script> tags so it runs under Confluence's
 * Content-Security-Policy (script-src 'self' 'nonce-...'), which blocks inline scripts.
 * Runs once on page load and again whenever macro output is inserted dynamically
 * (e.g. macro previews, inline comments, page tabs that load content later).
 */
(function (window, document, $) {
    'use strict';

    const MACRO_NAMES = ['markdown', 'markdown-from-url'];
    const MACRO_SELECTOR = MACRO_NAMES.map(function (name) {
        return '[data-macro-name="' + name + '"]';
    }).join(',');
    const HIGHLIGHTED_ATTR = 'data-markdown-highlighted';

    let scheduled = false;

    function highlightCode(macroName) {
        $('[data-macro-name="' + macroName + '"] code').each(function (i, block) {
            if (block.getAttribute(HIGHLIGHTED_ATTR) === 'true') return;
            block.setAttribute(HIGHLIGHTED_ATTR, 'true');
            if (window.hljs) {
                window.hljs.highlightBlock(block);
            }
        });
    }

    function processAll() {
        scheduled = false;
        MACRO_NAMES.forEach(function (macroName) {
            highlightCode(macroName);
            if (window.MarkdownMermaidRenderer) {
                window.MarkdownMermaidRenderer.renderAll(macroName);
            }
        });
    }

    function scheduleProcessAll() {
        if (scheduled) return;
        scheduled = true;
        window.requestAnimationFrame(processAll);
    }

    function containsMacro(node) {
        return node.nodeType === Node.ELEMENT_NODE
            && (node.matches(MACRO_SELECTOR) || node.querySelector(MACRO_SELECTOR) !== null);
    }

    function observeInsertedMacros() {
        const observer = new MutationObserver(function (mutations) {
            for (const mutation of mutations) {
                for (const node of mutation.addedNodes) {
                    if (containsMacro(node)) {
                        scheduleProcessAll();
                        return;
                    }
                }
            }
        });
        observer.observe(document.body, { childList: true, subtree: true });
    }

    $(function () {
        processAll();
        observeInsertedMacros();
    });
})(window, document, AJS.$);
