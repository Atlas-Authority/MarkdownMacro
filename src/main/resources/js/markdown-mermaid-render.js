(function (window) {
    'use strict';

    let mermaidInitialized = false;
    let pending = [];
    let observer = null;

    function ensureMermaidInitialized() {
        if (mermaidInitialized) return;
        mermaidInitialized = true;
        mermaid.initialize({
            securityLevel: 'sandbox',
            startOnLoad: false,
            theme: 'default',
            flowchart: {
                useMaxWidth: false,
                htmlLabels: true
            }
        });
    }

    function isRenderable(el) {
        if (!el) return false;
        const rect = el.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0;
    }

    function renderBlock(block) {
        if (block.getAttribute('data-mermaid-rendered') === 'true') return true;
        block.setAttribute('data-mermaid-rendered', 'true');
        try {
            mermaid.init(undefined, block);
            return true;
        } catch (e) {
            block.removeAttribute('data-mermaid-rendered');
            return false;
        }
    }

    function ensureObserver() {
        if (observer) return;
        observer = new MutationObserver(function () {
            pending = pending.filter(function (block) {
                if (isRenderable(block)) {
                    return !renderBlock(block);
                }
                return true;
            });
            if (!pending.length) {
                observer.disconnect();
                observer = null;
            }
        });
        observer.observe(document.body, {
            attributes: true,
            attributeFilter: ['class', 'style'],
            subtree: true
        });
    }

    // Safe to call once per macro instance on the page: blocks already
    // rendered or already queued are skipped, so multiple macros sharing
    // this page share one pending queue and one observer instead of each
    // spinning up their own.
    function renderAll(macroName) {
        ensureMermaidInitialized();
        AJS.$('[data-macro-name="' + macroName + '"] .language-mermaid').each(function (i, block) {
            if (pending.indexOf(block) !== -1) return;
            if (isRenderable(block)) {
                renderBlock(block);
            } else if (block.getAttribute('data-mermaid-rendered') !== 'true') {
                pending.push(block);
            }
        });
        if (pending.length) {
            ensureObserver();
        }
    }

    window.MarkdownMermaidRenderer = { renderAll: renderAll };
})(window);
