(function() {
    'use strict';

    // Target and restrict scroll/swipe behavior within Instagram's Reel viewer container
    function restrictReelScroll() {
        // Instagram typically renders modals or full-screen viewers using role="dialog" or sections
        const containers = document.querySelectorAll('div[role="dialog"], section');
        
        containers.forEach(container => {
            // Check if it's the reel viewer (contains a video player)
            if (container.querySelector('video') || container.innerHTML.includes('reel')) {
                // Prevent mouse wheel scrolling to adjacent reels
                container.onwheel = function(e) {
                    e.stopPropagation();
                };

                // Prevent touch-move event propagation to lock the current reel in place
                container.ontouchmove = function(e) {
                    e.stopPropagation();
                }, { passive: true };
            }
        });
    }

    // Observe dynamic DOM changes since Instagram loads reel views asynchronously via AJAX/React
    const observer = new MutationObserver(() => {
        restrictReelScroll();
    });

    observer.observe(document.body, {
        childList: true,
        subtree: true
    });

    // Initial execution on window load
    window.addEventListener('load', restrictReelScroll);
})();
