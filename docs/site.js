(function () {
  var key = "phonekey23-checklist-v1";
  var boxes = document.querySelectorAll("[data-check]");
  if (!boxes.length) return;

  var saved = {};
  try {
    saved = JSON.parse(localStorage.getItem(key) || "{}") || {};
  } catch (error) {
    saved = {};
  }

  boxes.forEach(function (box) {
    var id = box.getAttribute("data-check");
    box.checked = saved[id] === true;
    box.addEventListener("change", function () {
      saved[id] = box.checked;
      try {
        localStorage.setItem(key, JSON.stringify(saved));
      } catch (error) {
        /* Private mode can reject storage. The box still works for this visit. */
      }
    });
  });
})();
