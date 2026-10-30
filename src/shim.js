// Gives the GrandView page the same small data API it uses as a claude.ai artifact
// (window.claude.use("db")), backed by Firebase Firestore when a family code is set,
// or by storage on this phone only when it isn't. Also exposes the phone's
// speech and alarm clock as window.GVNative.
import { registerPlugin, Capacitor } from "@capacitor/core";
import { initializeApp } from "firebase/app";
import { getAuth, signInAnonymously } from "firebase/auth";
import {
  initializeFirestore, persistentLocalCache, doc as fsDoc, collection as fsCollection,
  setDoc, updateDoc, deleteDoc, getDoc, getDocs, addDoc, onSnapshot as fsOnSnapshot,
  query as fsQuery, where as fsWhere, orderBy as fsOrderBy, limit as fsLimit
} from "firebase/firestore";
import { firebaseConfig } from "./firebase-config.js";

// ---- phone speech + alarms ----
if (Capacitor.isNativePlatform()) window.GVNative = registerPlugin("SpeakAlarm");

// ---- Firestore-backed store, scoped to families/<code>/ ----
function firestoreDb(fs, root) {
  const wrapDoc = s => ({ id: s.id, exists: s.exists(), data: () => s.data(), metadata: s.metadata });
  const wrapQuery = s => ({
    docs: s.docs.map(wrapDoc), size: s.size, empty: s.empty, metadata: s.metadata,
    docChanges: () => s.docChanges().map(c => ({ type: c.type, doc: wrapDoc(c.doc), oldIndex: c.oldIndex, newIndex: c.newIndex }))
  });
  const docRef = path => {
    const ref = fsDoc(fs, root + "/" + path);
    return {
      id: ref.id, path,
      get: async () => wrapDoc(await getDoc(ref)),
      set: data => setDoc(ref, data),
      update: data => updateDoc(ref, data),
      delete: () => deleteDoc(ref),
      onSnapshot: (next, err) => fsOnSnapshot(ref, s => next(wrapDoc(s)), err),
      collection: sub => colRef(path + "/" + sub)
    };
  };
  const queryOf = (ref, parts) => ({
    where: (f, op, v) => queryOf(ref, [...parts, fsWhere(f, op, v)]),
    orderBy: (f, dir) => queryOf(ref, [...parts, fsOrderBy(f, dir)]),
    limit: n => queryOf(ref, [...parts, fsLimit(n)]),
    get: async () => wrapQuery(await getDocs(fsQuery(ref, ...parts))),
    onSnapshot: (next, err) => fsOnSnapshot(fsQuery(ref, ...parts), s => next(wrapQuery(s)), err)
  });
  const colRef = path => {
    const ref = fsCollection(fs, root + "/" + path);
    return {
      path, ...queryOf(ref, []),
      doc: id => docRef(path + "/" + (id || fsDoc(ref).id)),
      add: async data => { const r = await addDoc(ref, data); return docRef(path + "/" + r.id); }
    };
  };
  return { doc: docRef, collection: colRef };
}

// ---- store kept on this phone only ----
function localDb() {
  const KEY = "gv-local-db";
  let data = {}; try { data = JSON.parse(localStorage.getItem(KEY) || "{}"); } catch (e) {}
  const listeners = new Set();
  const save = () => { try { localStorage.setItem(KEY, JSON.stringify(data)); } catch (e) {} listeners.forEach(l => l()); };
  const snapDoc = (path, body) => ({ id: path.split("/").pop(), exists: body !== undefined, data: () => body, metadata: { fromCache: false, hasPendingWrites: false } });
  const ops = { "==": (a, b) => a === b, "!=": (a, b) => a !== b, "<": (a, b) => a < b, "<=": (a, b) => a <= b, ">": (a, b) => a > b, ">=": (a, b) => a >= b };
  const docRef = path => ({
    id: path.split("/").pop(), path,
    get: async () => snapDoc(path, data[path]),
    set: async body => { data[path] = JSON.parse(JSON.stringify(body)); save(); },
    update: async body => { data[path] = { ...(data[path] || {}), ...body }; save(); },
    delete: async () => { delete data[path]; save(); },
    onSnapshot: next => { const l = () => next(snapDoc(path, data[path])); listeners.add(l); setTimeout(l); return () => listeners.delete(l); },
    collection: sub => colRef(path + "/" + sub)
  });
  const queryOf = (path, q) => {
    const run = () => {
      let rows = Object.keys(data).filter(k => k.startsWith(path + "/") && k.split("/").length === path.split("/").length + 1)
        .map(k => snapDoc(k, data[k]));
      for (const [f, op, v] of q.where) rows = rows.filter(r => ops[op] && ops[op](r.data()[f], v));
      if (q.order) rows.sort((a, b) => (a.data()[q.order[0]] > b.data()[q.order[0]] ? 1 : -1) * (q.order[1] === "desc" ? -1 : 1));
      else rows.sort((a, b) => a.id.localeCompare(b.id));
      return q.limit ? rows.slice(0, q.limit) : rows;
    };
    const snap = (rows, prev) => ({
      docs: rows, size: rows.length, empty: !rows.length, metadata: { fromCache: false, hasPendingWrites: false },
      docChanges: () => rows.filter(r => !prev.has(r.id)).map(r => ({ type: "added", doc: r }))
    });
    return {
      where: (f, op, v) => queryOf(path, { ...q, where: [...q.where, [f, op, v]] }),
      orderBy: (f, dir = "asc") => queryOf(path, { ...q, order: [f, dir] }),
      limit: n => queryOf(path, { ...q, limit: n }),
      get: async () => snap(run(), new Set()),
      onSnapshot: next => {
        let prev = new Set();
        const l = () => { const rows = run(); next(snap(rows, prev)); prev = new Set(rows.map(r => r.id)); };
        listeners.add(l); setTimeout(l); return () => listeners.delete(l);
      }
    };
  };
  const colRef = path => ({
    path, ...queryOf(path, { where: [] }),
    doc: id => docRef(path + "/" + (id || Math.random().toString(36).slice(2, 12))),
    add: async body => { const r = docRef(path + "/" + Math.random().toString(36).slice(2, 12)); await r.set(body); return r; }
  });
  return { doc: docRef, collection: colRef };
}

// Phone-only data gets the usual starting list the first time the app opens.
function seedLocal(db) {
  try { if (localStorage.getItem("gv-seeded")) return; localStorage.setItem("gv-seeded", "1"); } catch (e) { return; }
  const start = [
    ["wake-up", "Wake up", "sun", "07:30", "Time to wake up, [name]"], ["morning-pills", "Morning pills", "pill", "08:00"],
    ["breakfast", "Breakfast", "meal", "08:30"], ["water", "Glass of water", "drink", "10:30"], ["lunch", "Lunch", "meal", "12:30"],
    ["tea", "Cup of tea", "drink", "15:00"], ["evening-meal", "Evening meal", "meal", "18:00"], ["evening-pills", "Evening pills", "pill", "18:30"],
    ["cooker", "Cooker turned off", "cooker", "19:00"], ["door", "Front door locked", "door", "21:00"]
  ];
  start.forEach(([id, label, icon, time, say], i) => db.collection("tasks").doc(id).set({ label, icon, kind: "daily", time, date: "", note: "", order: i, say: say || "", days: [], countdown: false, noConfirm: false }));
}

let dbPromise = null;
async function openDb() {
  let code = ""; try { code = localStorage.getItem("gv-family") || ""; } catch (e) {}
  if (code && firebaseConfig) {
    try {
      const app = initializeApp(firebaseConfig);
      await signInAnonymously(getAuth(app));
      const fs = initializeFirestore(app, { localCache: persistentLocalCache() }); // keeps working offline
      return firestoreDb(fs, "families/" + code);
    } catch (e) { console.error("Family sharing unavailable", e); }
  }
  const db = localDb(); seedLocal(db); return db;
}
window.claude = { use: async name => name === "db" ? (dbPromise ||= openDb()) : null };
