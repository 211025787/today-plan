// 逐帧渲染宣传片：Chromium + CDP 截帧，输出 PNG 序列，供 ffmpeg 编码。
// 运行：node video/render.mjs
import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import os from "node:os";

const CHROME = path.join(os.homedir(), "AppData/Local/ms-playwright/chromium-1169/chrome-win/chrome.exe");
const ROOT = path.resolve(".");
const PAGE = "file:///" + path.join(ROOT, "video/promo.html").replace(/\\/g, "/");
const OUT_DIR = path.join(ROOT, ".tmp_docs/frames");
const FPS = 25;
const DURATION = 36;

// 直接覆盖同名帧，避免批量删除触发安全策略
fs.mkdirSync(OUT_DIR, { recursive: true });

const port = 9333;
const proc = spawn(CHROME, [
  "--headless=new",
  `--remote-debugging-port=${port}`,
  "--window-size=1920,1080",
  "--hide-scrollbars",
  "--force-device-scale-factor=1",
  "--disable-gpu",
  "--no-first-run",
  "--no-default-browser-check",
  PAGE,
], { stdio: "ignore" });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function getTarget() {
  for (let i = 0; i < 50; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${port}/json/list`);
      const list = await res.json();
      const page = list.find((t) => t.type === "page" && t.url.startsWith("file:"));
      if (page && page.webSocketDebuggerUrl) return page.webSocketDebuggerUrl;
    } catch {}
    await sleep(200);
  }
  throw new Error("无法连接到 Chromium 调试端口");
}

const wsUrl = await getTarget();
const ws = new WebSocket(wsUrl);
await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej; });

let msgId = 0;
const pending = new Map();
ws.onmessage = (ev) => {
  const msg = JSON.parse(ev.data);
  if (msg.id && pending.has(msg.id)) {
    pending.get(msg.id)(msg);
    pending.delete(msg.id);
  }
};
function send(method, params = {}) {
  const id = ++msgId;
  ws.send(JSON.stringify({ id, method, params }));
  return new Promise((res) => pending.set(id, res));
}

await send("Page.enable");
await send("Runtime.enable");
// 强制精确视口：窗口装饰会让实际视口比 window-size 小（如 1902x932），
// 导致按 1920x1080 布局的页面被裁切
await send("Emulation.setDeviceMetricsOverride", {
  width: 1920,
  height: 1080,
  deviceScaleFactor: 1,
  mobile: false,
});

// 等待页面资源就绪
for (let i = 0; i < 100; i++) {
  const r = await send("Runtime.evaluate", { expression: "window.__ready === true", returnByValue: true });
  if (r.result?.result?.value === true) break;
  await sleep(100);
}

const stillsMode = process.argv.includes("--stills");
const STILL_TIMES = [2.5, 9, 16, 22.5, 29.5, 34.5];

const total = Math.round(FPS * DURATION);
const started = Date.now();

if (stillsMode) {
  for (const t of STILL_TIMES) {
    await send("Runtime.evaluate", { expression: `window.seek(${t})` });
    const shot = await send("Page.captureScreenshot", {
      format: "jpeg", quality: 92, fromSurface: true, optimizeForSpeed: true,
    });
    fs.writeFileSync(
      path.join(OUT_DIR, `s_${String(t).replace(".", "_")}.jpg`),
      Buffer.from(shot.result.data, "base64")
    );
  }
  console.log(`已输出 ${STILL_TIMES.length} 张关键帧 -> ${OUT_DIR}`);
  ws.close();
  proc.kill();
  process.exit(0);
}

for (let i = 0; i < total; i++) {
  const t = i / FPS;
  await send("Runtime.evaluate", { expression: `window.seek(${t})` });
  const shot = await send("Page.captureScreenshot", {
    format: "jpeg",
    quality: 92,
    fromSurface: true,
    optimizeForSpeed: true,
  });
  const data = shot.result?.data;
  if (!data) throw new Error(`第 ${i} 帧截图为空`);
  fs.writeFileSync(path.join(OUT_DIR, `f${String(i).padStart(5, "0")}.jpg`), Buffer.from(data, "base64"));
  if (i % 60 === 0) {
    const el = ((Date.now() - started) / 1000).toFixed(1);
    console.log(`渲染中 ${i}/${total} 帧  ${el}s`);
  }
}

console.log(`完成 ${total} 帧，用时 ${((Date.now() - started) / 1000).toFixed(1)}s -> ${OUT_DIR}`);
ws.close();
proc.kill();
process.exit(0);
