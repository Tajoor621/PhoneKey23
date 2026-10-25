import { x as require_jsx_runtime, y as Link } from "../_libs/@tanstack/react-router+[...].mjs";
//#region node_modules/.nitro/vite/services/ssr/assets/activate-DXzNdwAc.js
var import_jsx_runtime = require_jsx_runtime();
function Activate() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("main", {
		className: "mx-auto flex min-h-dvh w-full max-w-lg flex-col justify-center bg-canvas px-7 py-10 text-fg",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h1", {
				className: "font-display text-4xl font-semibold",
				children: "PhoneKey is free."
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "mt-4 text-lg leading-relaxed text-muted",
				children: "No ads. No subscriptions. No accounts. Your WireGuard keys stay on this device."
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Link, {
				to: "/",
				className: "mt-8 flex h-12 items-center justify-center rounded-2xl bg-brass font-semibold text-brass-ink",
				children: "Open PhoneKey"
			})
		]
	});
}
//#endregion
export { Activate as component };
