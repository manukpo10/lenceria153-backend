import fs from "fs";
import path from "path";

const DB_DIR = path.join(__dirname, "../../data");

function ensureDir() {
  if (!fs.existsSync(DB_DIR)) {
    fs.mkdirSync(DB_DIR, { recursive: true });
  }
}

export function readJson<T>(filename: string): T[] {
  ensureDir();
  const filePath = path.join(DB_DIR, filename);
  if (!fs.existsSync(filePath)) return [];
  const raw = fs.readFileSync(filePath, "utf-8");
  return JSON.parse(raw) as T[];
}

export function writeJson<T>(filename: string, data: T[]): void {
  ensureDir();
  const filePath = path.join(DB_DIR, filename);
  fs.writeFileSync(filePath, JSON.stringify(data, null, 2), "utf-8");
}

export function readOne<T>(filename: string, predicate: (item: T) => boolean): T | null {
  const items = readJson<T>(filename);
  return items.find(predicate) ?? null;
}

export function insert<T extends { id: string }>(filename: string, item: T): T {
  const items = readJson<T>(filename);
  items.push(item);
  writeJson(filename, items);
  return item;
}

export function update<T>(
  filename: string,
  predicate: (item: T) => boolean,
  updater: (item: T) => T
): T | null {
  const items = readJson<T>(filename);
  const idx = items.findIndex(predicate);
  if (idx === -1) return null;
  items[idx] = updater(items[idx]);
  writeJson(filename, items);
  return items[idx];
}

export function remove<T>(
  filename: string,
  predicate: (item: T) => boolean
): boolean {
  const items = readJson<T>(filename);
  const idx = items.findIndex(predicate);
  if (idx === -1) return false;
  items.splice(idx, 1);
  writeJson(filename, items);
  return true;
}