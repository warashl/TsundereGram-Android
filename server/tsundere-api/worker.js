/**
 * TsundereGram API — Cloudflare Worker.
 *
 * Держит токен бота у себя (секрет BOT_TOKEN), приложение ходит сюда вместо api.telegram.org.
 *  - чтение конфига (getChat), getFile и скачивание файлов — публичные;
 *  - запись (editMessageText, sendDocument) — только с подписью пользователя Telegram
 *  - конфиг до 4096 символов хранится текстом закрепа, больше — документом tsundere_config.txt в закрепе
 *    (initData мини-приложения бота, заголовок X-Tsundere-Auth), подпись проверяется токеном бота;
 *  - при записи конфига сервер сам решает, что можно менять: обычный пользователь — только свои
 *    записи (баннер/рамка/цвета/градиент/«клиент»), админы значков — значки, создатель — список админов.
 *
 * Переменные окружения:
 *   BOT_TOKEN   (secret, обязательно) — токен бота, админа канала с конфигом
 *   CHANNEL     (необязательно) — канал с закреплённым конфигом, по умолчанию @TsundereGram_Banners
 *   CREATOR_IDS (необязательно) — id создателей через запятую, по умолчанию 5133421262
 *   AI          (привязка Workers AI) — распознавание голосовых «из коробки» (POST /transcribe)
 */

const DEFAULT_CHANNEL = "@TsundereGram_Banners";
const DEFAULT_CREATORS = "5133421262";
const CONFIG_HEADER = "#TSUNDERE_BADGES_CONFIG";
const SALT = "tsundere_tsun_dere_super_secret_salt_2026_xoxo";
const AES_PASSPHRASE = "tsunderegram_cloud_secure_encryption_key_v1";
const SPECIAL_KEYS = ["banners", "frames", "admins", "profile_colors", "clients", "nickname_gradients"];
const USER_MAPS = ["banners", "frames", "profile_colors", "nickname_gradients"];
const AUTH_MAX_AGE = 24 * 60 * 60; // initData действует сутки
const READ_CACHE_SECONDS = 15;

const enc = new TextEncoder();
let botInfoCache = null;

export default {
  async fetch(request, env, ctx) {
    try {
      // токен мог быть вставлен с пробелом/переводом строки: URL такие символы съедает,
      // а HMAC подписи — нет, из-за чего вся запись отвечала 401
      if (env.BOT_TOKEN) env = Object.assign({}, env, { BOT_TOKEN: String(env.BOT_TOKEN).trim() });
      if (!env.BOT_TOKEN) {
        return json({ ok: false, description: "BOT_TOKEN не задан в настройках Worker" }, 500);
      }
      const url = new URL(request.url);
      const path = url.pathname;
      if (path === "/" || path === "/app") {
        return new Response(APP_HTML, { headers: { "content-type": "text/html; charset=utf-8" } });
      }
      if (path === "/info") {
        const me = await getMe(env);
        return json({ ok: true, bot: me && me.username, channel: channel(env) });
      }
      if (path === "/setup") {
        return setup(env, url);
      }
      if (path === "/transcribe") {
        return transcribe(env, request);
      }
      if (path.startsWith("/file/")) {
        return proxyFile(env, path.slice("/file/".length), request, ctx);
      }
      if (path.startsWith("/bot/")) {
        const method = path.slice("/bot/".length);
        switch (method) {
          case "getChat":
            return getChatCached(env, url, ctx);
          case "getFile":
            return tgRaw(env, "getFile", { file_id: url.searchParams.get("file_id") || "" });
          case "sendDocument":
            return sendDocument(env, request);
          case "editMessageText":
            return editConfig(env, request, url, ctx);
          default:
            return json({ ok: false, error_code: 403, description: "Метод не разрешён: " + method }, 403);
        }
      }
      return json({ ok: false, error_code: 404, description: "Not found" }, 404);
    } catch (e) {
      return json({ ok: false, error_code: 500, description: String(e && e.message || e) }, 500);
    }
  },
};

// ---------------------------------------------------------------- helpers

function json(obj, status = 200, extra = {}) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...extra },
  });
}

function channel(env) {
  return env.CHANNEL || DEFAULT_CHANNEL;
}

function creators(env) {
  return (env.CREATOR_IDS || DEFAULT_CREATORS).split(",").map((s) => s.trim()).filter(Boolean);
}

async function tg(env, method, params) {
  const r = await fetch(`https://api.telegram.org/bot${env.BOT_TOKEN}/${method}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(params || {}),
  });
  const data = await r.json().catch(() => ({ ok: false, description: "bad response" }));
  return { status: r.status, data };
}

async function tgRaw(env, method, params) {
  const { status, data } = await tg(env, method, params);
  return json(data, status);
}

// ---------------------------------------------------------------- конфиг больше 4096 символов

const CONFIG_FILE_NAME = "tsundere_config.txt";
const TEXT_LIMIT = 4096;

/** Текст конфига из закрепа: обычный текст или документ tsundere_config.txt (когда конфиг > 4096). */
async function pinnedConfigText(env, pinned) {
  if (!pinned) return "";
  if (typeof pinned.text === "string" && pinned.text) return pinned.text;
  const doc = pinned.document;
  if (!doc || !doc.file_id) return "";
  const f = await tg(env, "getFile", { file_id: doc.file_id });
  if (!f.data.ok || !f.data.result || !f.data.result.file_path) throw new Error("getFile: " + (f.data.description || f.status));
  const r = await fetch("https://api.telegram.org/file/bot" + env.BOT_TOKEN + "/" + f.data.result.file_path);
  if (!r.ok) throw new Error("скачивание конфига: HTTP " + r.status);
  return await r.text();
}

/** Сообщение-документ → копия с полем text: и старые, и новые клиенты читают конфиг из text. */
async function withConfigText(env, msg) {
  if (!msg || (typeof msg.text === "string" && msg.text) || !msg.document) return msg;
  try {
    return Object.assign({}, msg, { text: await pinnedConfigText(env, msg) });
  } catch (e) {
    return msg;
  }
}

async function tgMultipart(env, method, form) {
  const r = await fetch("https://api.telegram.org/bot" + env.BOT_TOKEN + "/" + method, { method: "POST", body: form });
  const data = await r.json().catch(() => ({ ok: false, description: "bad response" }));
  return { status: r.status, data };
}

/** Сохраняет конфиг: до 4096 символов — текстом закрепа, больше — документом в закрепе. */
async function saveConfig(env, pinned, text) {
  const isDoc = !!(pinned && pinned.document);
  if (!isDoc && text.length <= TEXT_LIMIT) {
    return tg(env, "editMessageText", { chat_id: channel(env), message_id: pinned.message_id, text });
  }
  const blob = new Blob([text], { type: "text/plain" });
  if (isDoc) {
    const form = new FormData();
    form.set("chat_id", channel(env));
    form.set("message_id", String(pinned.message_id));
    form.set("media", JSON.stringify({ type: "document", media: "attach://cfg" }));
    form.set("cfg", blob, CONFIG_FILE_NAME);
    return tgMultipart(env, "editMessageMedia", form);
  }
  // текстовый закреп больше не вмещает конфиг: отправляем документ и закрепляем его
  const form = new FormData();
  form.set("chat_id", channel(env));
  form.set("document", blob, CONFIG_FILE_NAME);
  form.set("disable_notification", "true");
  const sent = await tgMultipart(env, "sendDocument", form);
  if (!sent.data.ok || !sent.data.result) return sent;
  const pin = await tg(env, "pinChatMessage", { chat_id: channel(env), message_id: sent.data.result.message_id, disable_notification: true });
  if (!pin.data.ok) return pin;
  return sent;
}

async function getMe(env) {
  if (botInfoCache) return botInfoCache;
  const { data } = await tg(env, "getMe", {});
  if (data.ok) botInfoCache = data.result;
  return botInfoCache;
}

async function setup(env, url) {
  if (env.SETUP_KEY && url.searchParams.get("key") !== env.SETUP_KEY) {
    return json({ ok: false, description: "нужен ?key=SETUP_KEY" }, 403);
  }
  const appUrl = `${url.origin}/app`;
  const { data } = await tg(env, "setChatMenuButton", {
    menu_button: { type: "web_app", text: "TsundereGram", web_app: { url: appUrl } },
  });
  const me = await getMe(env);
  return json({ ok: !!data.ok, bot: me && me.username, menu_button_url: appUrl, telegram: data });
}

// ---------------------------------------------------------------- чтение

function chatCacheKey(url) {
  return new Request(`${url.origin}/__cache/getChat`);
}

async function getChatCached(env, url, ctx) {
  const cache = caches.default;
  const key = chatCacheKey(url);
  const hit = await cache.match(key);
  if (hit) return hit;
  const { status, data } = await tg(env, "getChat", { chat_id: channel(env) });
  if (status === 200 && data.ok && data.result && data.result.pinned_message) {
    // конфиг больше 4096 символов лежит документом — клиентам отдаём его как обычный text
    data.result.pinned_message = await withConfigText(env, data.result.pinned_message);
  }
  const resp = json(data, status, { "cache-control": `public, max-age=${READ_CACHE_SECONDS}` });
  if (status === 200 && data.ok) ctx.waitUntil(cache.put(key, resp.clone()));
  return resp;
}

async function proxyFile(env, filePath, request, ctx) {
  if (!/^[A-Za-z0-9_\-./]+$/.test(filePath) || filePath.includes("..")) {
    return json({ ok: false, description: "bad path" }, 400);
  }
  const cache = caches.default;
  const key = new Request(new URL(request.url).toString());
  const hit = await cache.match(key);
  if (hit) return hit;
  const r = await fetch(`https://api.telegram.org/file/bot${env.BOT_TOKEN}/${filePath}`);
  if (!r.ok) return new Response("not found", { status: r.status });
  const headers = new Headers();
  headers.set("content-type", r.headers.get("content-type") || "application/octet-stream");
  if (r.headers.get("content-length")) headers.set("content-length", r.headers.get("content-length"));
  headers.set("cache-control", "public, max-age=86400");
  const resp = new Response(r.body, { status: 200, headers });
  ctx.waitUntil(cache.put(key, resp.clone()));
  return resp;
}

// ---------------------------------------------------------------- авторизация

function hex(buf) {
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function safeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

async function hmac(keyBytes, data) {
  const key = await crypto.subtle.importKey("raw", keyBytes, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return crypto.subtle.sign("HMAC", key, typeof data === "string" ? enc.encode(data) : data);
}

/** Проверка initData мини-приложения: https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app */
async function verifyInitData(env, initData, why) {
  why = why || {};
  if (!initData) { why.reason = "no initData"; return null; }
  const params = new URLSearchParams(initData);
  const hash = params.get("hash");
  if (!hash) { why.reason = "no hash"; return null; }
  const pairs = [];
  for (const [k, v] of params) if (k !== "hash") pairs.push([k, v]);
  pairs.sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0));
  const dataCheckString = pairs.map(([k, v]) => `${k}=${v}`).join("\n");
  const secret = await hmac(enc.encode("WebAppData"), env.BOT_TOKEN);
  const check = hex(await hmac(secret, dataCheckString));
  if (!safeEqual(check, hash.toLowerCase())) { why.reason = "bad signature"; return null; }
  const authDate = parseInt(params.get("auth_date") || "0", 10);
  if (!authDate || Date.now() / 1000 - authDate > AUTH_MAX_AGE) { why.reason = "expired"; return null; }
  const userRaw = params.get("user") || "";
  const m = /"id"\s*:\s*(\d+)/.exec(userRaw); // id строкой — без потери точности
  if (!m) { why.reason = "no user"; return null; }
  return { id: m[1] };
}

async function requireUser(env, request, why) {
  const user = await verifyInitData(env, request.headers.get("x-tsundere-auth"), why);
  return user;
}

function unauthorized(why) {
  return json({ ok: false, error_code: 401, description: "Нужна авторизация Telegram" + (why && why.reason ? " (" + why.reason + ")" : "") }, 401);
}

// ---------------------------------------------------------------- шифрование конфига (как TsundereCrypto)

async function aesKey() {
  const raw = new Uint8Array(await crypto.subtle.digest("SHA-256", enc.encode(AES_PASSPHRASE)));
  const key = await crypto.subtle.importKey("raw", raw, { name: "AES-CBC" }, false, ["encrypt", "decrypt"]);
  return { key, iv: raw.slice(0, 16) };
}

function b64encode(bytes) {
  let s = "";
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(s);
}

function b64decode(str) {
  const s = atob(str.replace(/\s+/g, ""));
  const out = new Uint8Array(s.length);
  for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
  return out;
}

async function encryptConfig(plain) {
  const { key, iv } = await aesKey();
  const ct = await crypto.subtle.encrypt({ name: "AES-CBC", iv }, key, enc.encode(plain));
  return b64encode(new Uint8Array(ct));
}

async function decryptPayload(payload) {
  let t = (payload || "").trim();
  if (t.startsWith("{") || t.startsWith("[")) return t;
  if (t.startsWith("ENC:")) t = t.slice(4).trim();
  if (!t) return "";
  const { key, iv } = await aesKey();
  const pt = await crypto.subtle.decrypt({ name: "AES-CBC", iv }, key, b64decode(t));
  return new TextDecoder().decode(pt);
}

/** Текст сообщения → объект конфига (как это делает клиент). */
async function parseConfigText(text) {
  text = text || "";
  let payload = text;
  const h = text.indexOf(CONFIG_HEADER);
  if (h >= 0) payload = text.slice(h + CONFIG_HEADER.length).trim();
  else if (text.indexOf("\n") >= 0) payload = text.slice(text.indexOf("\n") + 1).trim();
  const plain = await decryptPayload(payload);
  const a = plain.indexOf("{"), b = plain.lastIndexOf("}");
  if (a < 0 || b <= a) return {};
  return JSON.parse(plain.slice(a, b + 1));
}

async function hashUserId(id) {
  const d = await crypto.subtle.digest("SHA-256", enc.encode(`${SALT}:${id}:${SALT}`));
  return hex(d).slice(0, 32);
}

// ---------------------------------------------------------------- запись

function canon(v) {
  if (v === undefined) return "\u0000undef";
  if (v === null || typeof v !== "object") return JSON.stringify(v);
  if (Array.isArray(v)) return "[" + v.map(canon).join(",") + "]";
  return "{" + Object.keys(v).sort().map((k) => JSON.stringify(k) + ":" + canon(v[k])).join(",") + "}";
}

function asObj(v) {
  return v && typeof v === "object" && !Array.isArray(v) ? v : {};
}

/**
 * Применяет к старому конфигу только разрешённые изменения из нового.
 * Возвращает { result, applied, denied }.
 */
function mergeConfig(oldCfg, newCfg, who) {
  const result = JSON.parse(JSON.stringify(oldCfg));
  let applied = 0, denied = 0;
  const own = (k) => k === who.hash || k === who.id;

  // 1) значки — ключи верхнего уровня
  const keys = new Set([...Object.keys(oldCfg), ...Object.keys(newCfg)]);
  for (const k of keys) {
    if (SPECIAL_KEYS.includes(k)) continue;
    if (canon(oldCfg[k]) === canon(newCfg[k])) continue;
    if (!who.isAdmin) { denied++; continue; }
    if (newCfg[k] === undefined) delete result[k]; else result[k] = newCfg[k];
    applied++;
  }

  // 2) список админов — только создатель и только по явному флагу admins_change:
  //    иначе любое другое сохранение конфига со старой копией возвращало отозванных админов
  if (who.adminsChange && canon(oldCfg.admins) !== canon(newCfg.admins)) {
    if (who.isCreator) {
      if (newCfg.admins === undefined) delete result.admins; else result.admins = newCfg.admins;
      applied++;
    } else denied++;
  }

  // 3) clients — каждый может добавить/убрать только себя
  if (canon(oldCfg.clients) !== canon(newCfg.clients)) {
    const oldArr = Array.isArray(oldCfg.clients) ? oldCfg.clients : [];
    const newArr = Array.isArray(newCfg.clients) ? newCfg.clients : [];
    if (who.isAdmin) {
      result.clients = newArr;
      applied++;
    } else {
      const out = oldArr.filter((x) => !own(String(x)));
      for (const x of newArr) if (own(String(x)) && !out.includes(x)) out.push(x);
      const othersChanged = canon(oldArr.filter((x) => !own(String(x)))) !== canon(newArr.filter((x) => !own(String(x))));
      if (othersChanged) denied++;
      if (canon(out) !== canon(oldArr)) { result.clients = out; applied++; }
    }
  }

  // 4) персональные секции: свои ключи; для баннеров каналов — «кто первым поставил, тот и владелец»
  const ownerTag = who.hash.slice(0, 12);
  for (const m of USER_MAPS) {
    const om = asObj(oldCfg[m]), nm = asObj(newCfg[m]);
    const rm = { ...asObj(result[m]) };
    let touched = false;
    for (const k of new Set([...Object.keys(om), ...Object.keys(nm)])) {
      if (canon(om[k]) === canon(nm[k])) continue;
      let allowed = who.isAdmin || own(k);
      const foreign = !own(k) && !who.isAdmin; // баннер канала: запоминаем владельца
      // ключ баннера канала — отрицательный id диалога; чужие пользовательские ключи трогать нельзя
      if (!allowed && m === "banners" && /^-\d+$/.test(k)) {
        const prevOwner = om[k] && typeof om[k] === "object" ? om[k]._o : undefined;
        allowed = !prevOwner || prevOwner === ownerTag;
      }
      if (!allowed) { denied++; continue; }
      if (nm[k] === undefined) {
        delete rm[k];
      } else {
        let v = nm[k];
        if (foreign && v && typeof v === "object" && !Array.isArray(v)) v = { ...v, _o: ownerTag };
        rm[k] = v;
      }
      applied++;
      touched = true;
    }
    if (touched) result[m] = rm;
  }
  return { result, applied, denied };
}

async function editConfig(env, request, url, ctx) {
  const why = {};
  const user = await requireUser(env, request, why);
  if (!user) return unauthorized(why);

  const body = await request.json().catch(() => null);
  if (!body || typeof body.text !== "string") return json({ ok: false, error_code: 400, description: "bad body" }, 400);

  const chat = await tg(env, "getChat", { chat_id: channel(env) });
  if (!chat.data.ok) return json(chat.data, chat.status);
  const pinned = chat.data.result.pinned_message;
  if (!pinned) return json({ ok: false, error_code: 409, description: "нет закреплённого сообщения с конфигом" }, 409);

  let oldCfg, newCfg;
  let oldText;
  try { oldText = await pinnedConfigText(env, pinned); } catch (e) {
    // без текущего конфига сохранять нельзя — иначе затрём чужие записи
    return json({ ok: false, error_code: 502, description: "не удалось прочитать текущий конфиг: " + String((e && e.message) || e) }, 502);
  }
  try { oldCfg = await parseConfigText(oldText); } catch (e) { oldCfg = {}; }
  try { newCfg = await parseConfigText(body.text); } catch (e) {
    return json({ ok: false, error_code: 400, description: "не удалось прочитать новый конфиг" }, 400);
  }

  const hash = await hashUserId(user.id);
  const admins = Array.isArray(oldCfg.admins) ? oldCfg.admins.map(String) : [];
  const isCreator = creators(env).includes(user.id);
  const who = { id: user.id, hash, isCreator, isAdmin: isCreator || admins.includes(hash) || admins.includes(user.id), adminsChange: body.admins_change === true };

  const { result, applied, denied } = mergeConfig(oldCfg, newCfg, who);
  if (!applied) {
    if (denied) return json({ ok: false, error_code: 403, description: "Нет прав на это изменение" }, 403);
    return json({ ok: true, result: pinned, applied: 0 });
  }
  const text = CONFIG_HEADER + "\nENC:" + (await encryptConfig(JSON.stringify(result)));
  // до 4096 символов — текст закрепа, больше — документ tsundere_config.txt в закрепе (раньше тут был отказ 413)
  const edit = await saveConfig(env, pinned, text);
  if (!edit.data.ok && !/not modified/i.test(edit.data.description || "")) return json(edit.data, edit.status);
  // Кладём в кэш уже новый конфиг (а не просто удаляем запись): иначе ближайшее чтение могло
  // вернуть старую копию, и следующее сохранение откатывало только что сделанное изменение.
  const newPinned = Object.assign({}, edit.data.ok && edit.data.result && typeof edit.data.result === "object" ? edit.data.result : pinned, { text });
  const freshChat = Object.assign({}, chat.data, { result: Object.assign({}, chat.data.result, { pinned_message: newPinned }) });
  ctx.waitUntil(caches.default.put(chatCacheKey(url), json(freshChat, 200, { "cache-control": `public, max-age=${READ_CACHE_SECONDS}` })));
  return json({ ok: true, result: newPinned, applied, denied });
}

// ---------------------------------------------------------------- распознавание речи

const WHISPER_MODEL = "@cf/openai/whisper-large-v3-turbo";
const MAX_AUDIO_B64 = 30 * 1024 * 1024; // ~22 МБ звука — с запасом для длинных голосовых

function aiError(message, status) {
  // формат ответа как у REST API Workers AI: приложение разбирает оба одинаково
  return json({ success: false, errors: [{ message }] }, status);
}

async function transcribe(env, request) {
  if (request.method !== "POST") return aiError("Только POST", 405);
  const why = {};
  const user = await requireUser(env, request, why);
  if (!user) return aiError("Нужна авторизация Telegram" + (why.reason ? " (" + why.reason + ")" : ""), 401);
  if (!env.AI) return aiError("На сервере не подключён Workers AI (привязка AI)", 503);

  const body = await request.json().catch(() => null);
  const audio = body && typeof body.audio === "string" ? body.audio : "";
  if (!audio) return aiError("Нет аудио", 400);
  if (audio.length > MAX_AUDIO_B64) return aiError("Голосовое слишком длинное для распознавания", 413);

  const input = { audio };
  if (body && typeof body.language === "string" && /^[a-z]{2}$/.test(body.language)) input.language = body.language;
  try {
    const out = await env.AI.run(WHISPER_MODEL, input);
    const text = out && typeof out.text === "string" ? out.text.trim() : "";
    return json({ success: true, errors: [], result: { text } });
  } catch (e) {
    const msg = String((e && e.message) || e || "");
    if (/neuron|quota|limit|429/i.test(msg)) return aiError("Дневной лимит бесплатного распознавания исчерпан, попробуйте завтра", 429);
    return aiError("Ошибка распознавания: " + msg, 502);
  }
}

async function sendDocument(env, request) {
  const why = {};
  const user = await requireUser(env, request, why);
  if (!user) return unauthorized(why);
  const form = await request.formData();
  const out = new FormData();
  for (const [k, v] of form.entries()) {
    if (k === "chat_id") continue;
    out.append(k, v);
  }
  out.set("chat_id", channel(env));
  const r = await fetch(`https://api.telegram.org/bot${env.BOT_TOKEN}/sendDocument`, { method: "POST", body: out });
  const data = await r.json().catch(() => ({ ok: false, description: "bad response" }));
  return json(data, r.status);
}

const APP_HTML = `<!doctype html><html lang="ru"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>TsundereGram</title>
<style>body{font-family:system-ui,sans-serif;background:#1b1b22;color:#eee;display:flex;align-items:center;justify-content:center;height:100vh;margin:0}
.c{text-align:center;padding:24px}h1{font-size:22px;margin:0 0 8px}p{opacity:.7;margin:0}</style></head>
<body><div class="c"><h1>TsundereGram API</h1><p>Сервер значков, баннеров и рамок. Ничего делать не нужно (｀へ´)</p></div></body></html>`;

// для тестов в Node
export const __test = { saveConfig, pinnedConfigText, withConfigText, mergeConfig, parseConfigText, encryptConfig, hashUserId, verifyInitData, canon };
